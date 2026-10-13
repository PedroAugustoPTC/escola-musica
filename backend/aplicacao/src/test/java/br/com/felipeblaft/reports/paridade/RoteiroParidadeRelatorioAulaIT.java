package br.com.felipeblaft.reports.paridade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.configuracao.CatalogoMensagens;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.LlmPort;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.LlmProgramavelTeste;
import br.com.felipeblaft.reports.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.reports.suporte.TelegramConversaBase;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Paridade do relatório de aula (R-16, SC-006): o mesmo roteiro — enviar áudio, responder, alterar
 * por texto, alterar por áudio, aprovar e tentar aprovar de novo — na Web e no Telegram, com IA
 * simulada. Estados, versões, interações (meio e resultado), PDFs e aprovação devem ser iguais; só
 * o canal registrado muda.
 */
class RoteiroParidadeRelatorioAulaIT extends TelegramConversaBase {

    @Autowired CatalogoMensagens catalogo;

    @MockitoSpyBean LlmPort llm;

    private Student matteo;
    private Student helena;

    /** Operações do roteiro num canal; cada uma devolve um código neutro. */
    interface CanalRelatorio {
        String registrar() throws Exception;

        String responder(String texto) throws Exception;

        String alterarPorTexto(String texto) throws Exception;

        String alterarPorAudio() throws Exception;

        String aprovar() throws Exception;

        String aprovarDeNovo() throws Exception;
    }

    @BeforeEach
    void carregar() {
        Teacher clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        helena = cenario.aluno(clara, "Helena Prado", "Violino");
        cenario.tempoPorTrabalho = Duration.ofSeconds(2);
    }

    @AfterEach
    void restaurarRelogio() {
        cenario.tempoPorTrabalho = Duration.ZERO;
    }

    /** Uma linha por tarefa de IA do relatório: quantas chamadas e se os dados de custo vieram. */
    private List<String> custos(Student aluno) {
        return jdbc.queryForList(
                "select tarefa || ' x' || count(*) || ' sucesso=' || bool_and(sucesso)"
                        + " || ' duracao=' || bool_and(duracao_ms is not null)"
                        + " || ' custo=' || bool_and(custo_estimado_usd is not null)"
                        + " from chamada_ia where relatorio_tipo = 'AULA' and relatorio_id = ?"
                        + " group by tarefa order by tarefa",
                String.class,
                relatorioDe(aluno));
    }

    // ---------------------------------------------------------------- consultas neutras

    private UUID relatorioDe(Student aluno) {
        return jdbc.queryForObject(
                "select id from relatorio_aula where aluno_id = ?", UUID.class, aluno.id());
    }

    /** Resultado do trabalho do tipo e meio dados (o relógio fixo empata as datas). */
    private String resultado(Student aluno, String tipo, String meio) {
        return jdbc.queryForObject(
                "select resultado_codigo from trabalho_processamento"
                        + " where aluno_id = ? and tipo = ? and meio = ?"
                        + " order by concluido_em desc limit 1",
                String.class,
                aluno.id(),
                tipo,
                meio);
    }

    private int versaoVigente(Student aluno) {
        return jdbc.queryForObject(
                "select versao_vigente from relatorio_aula where aluno_id = ?",
                Integer.class,
                aluno.id());
    }

    /** Estado final do relatório sem identificadores, com o canal trocado por um marcador. */
    private List<String> resumo(Student aluno, String canal) {
        UUID id = relatorioDe(aluno);
        List<String> linhas = new ArrayList<>();
        linhas.add(
                jdbc.queryForObject(
                        "select status || ' vigente=' || versao_vigente"
                                + " || ' aprovada=' || coalesce(versao_aprovada::text, '-')"
                                + " || ' canalOrigem=' || canal_origem"
                                + " || ' canalAprovacao=' || coalesce(canal_aprovacao, '-')"
                                + " from relatorio_aula where id = ?",
                        String.class,
                        id));
        linhas.addAll(
                jdbc.queryForList(
                        "select tipo || ' ' || meio || ' ' || resultado || ' base=' || versao_base"
                                + " || ' resultante=' || coalesce(versao_resultante::text, '-')"
                                + " || ' canal=' || canal"
                                + " from interacao_relatorio where relatorio_id = ? order by versao_base, tipo",
                        String.class,
                        id));
        linhas.addAll(
                jdbc.queryForList(
                        "select 'versão ' || numero || ' ' || origem || ' canal=' || canal"
                                + " from versao_relatorio where relatorio_id = ? order by numero",
                        String.class,
                        id));
        linhas.add(
                "pdfs="
                        + jdbc.queryForList(
                                "select d.tipo from documento_pdf d join versao_relatorio v on v.id = d.versao_id"
                                        + " where v.relatorio_id = ? order by v.numero, d.tipo",
                                String.class,
                                id));
        return linhas.stream().map(l -> l.replace(canal, "CANAL")).toList();
    }

    private static RoteiroParidade roteiro(CanalRelatorio canal) {
        return new RoteiroParidade()
                .passo("enviar áudio", c -> canal.registrar())
                .passo("responder", c -> canal.responder("Estudou bem; vai preparar a valsa."))
                .passo(
                        "alterar por texto",
                        c -> canal.alterarPorTexto("Diga que a aula foi ótima."))
                .passo("alterar por áudio", c -> canal.alterarPorAudio())
                .passo("aprovar", c -> canal.aprovar())
                .passo("aprovar de novo", c -> canal.aprovarDeNovo());
    }

    // ---------------------------------------------------------------- Web

    private CanalRelatorio web(Cookie sessao, Student aluno) {
        return new CanalRelatorio() {
            private int corpoStatus(org.springframework.test.web.servlet.MvcResult resultado) {
                return resultado.getResponse().getStatus();
            }

            private String codigo(org.springframework.test.web.servlet.MvcResult resultado)
                    throws Exception {
                int status = corpoStatus(resultado);
                if (status / 100 == 2) {
                    return "ACEITO";
                }
                return JSON.readTree(resultado.getResponse().getContentAsString())
                        .path("codigo")
                        .asString();
            }

            private MockMultipartFile audio() {
                return new MockMultipartFile(
                        "audio", "aula.webm", "audio/webm", NormalizacaoAudioTeste.AUDIO_COM_FALA);
            }

            @Override
            public String registrar() throws Exception {
                codigo(
                        mvc.perform(
                                        multipart("/api/v1/alunos/{id}/relatorios-aula", aluno.id())
                                                .file(audio())
                                                .cookie(sessao)
                                                .with(csrf().asHeader()))
                                .andReturn());
                cenario.processarFila();
                return resultado(aluno, "REGISTRO_AULA", "AUDIO");
            }

            @Override
            public String responder(String texto) throws Exception {
                mvc.perform(
                        post("/api/v1/relatorios-aula/{id}/respostas", relatorioDe(aluno))
                                .cookie(sessao)
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        JSON.writeValueAsString(java.util.Map.of("texto", texto))));
                cenario.processarFila();
                return resultado(aluno, "RESPOSTA_PERGUNTAS", "TEXTO");
            }

            @Override
            public String alterarPorTexto(String texto) throws Exception {
                mvc.perform(
                        post("/api/v1/relatorios-aula/{id}/alteracoes", relatorioDe(aluno))
                                .cookie(sessao)
                                .with(csrf().asHeader())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        JSON.writeValueAsString(
                                                java.util.Map.of(
                                                        "texto",
                                                        texto,
                                                        "versaoBase",
                                                        versaoVigente(aluno)))));
                cenario.processarFila();
                return resultado(aluno, "ALTERACAO", "TEXTO");
            }

            @Override
            public String alterarPorAudio() throws Exception {
                mvc.perform(
                        multipart("/api/v1/relatorios-aula/{id}/alteracoes", relatorioDe(aluno))
                                .file(audio())
                                .param("versaoBase", String.valueOf(versaoVigente(aluno)))
                                .cookie(sessao)
                                .with(csrf().asHeader()));
                cenario.processarFila();
                return resultado(aluno, "ALTERACAO", "AUDIO");
            }

            private String aprovarVersao() throws Exception {
                return codigo(
                        mvc.perform(
                                        post(
                                                        "/api/v1/relatorios-aula/{id}/aprovacao",
                                                        relatorioDe(aluno))
                                                .cookie(sessao)
                                                .with(csrf().asHeader())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"versaoConfirmada\":"
                                                                + versaoVigente(aluno)
                                                                + "}"))
                                .andReturn());
            }

            @Override
            public String aprovar() throws Exception {
                aprovarVersao();
                return jdbc.queryForObject(
                        "select status from relatorio_aula where aluno_id = ?",
                        String.class,
                        aluno.id());
            }

            @Override
            public String aprovarDeNovo() throws Exception {
                return aprovarVersao();
            }
        };
    }

    // ---------------------------------------------------------------- Telegram

    /** Código do catálogo cujo texto é o da última mensagem (o Telegram responde com texto). */
    private String codigoDaUltimaMensagem() {
        String texto = ultimaMensagem().get("text").asString();
        String melhor = texto;
        int tamanho = 0;
        for (String chave : catalogo.chaves()) {
            String modelo = catalogo.modelo(chave);
            if (!modelo.contains("{") && modelo.length() > tamanho && texto.contains(modelo)) {
                melhor = chave;
                tamanho = modelo.length();
            }
        }
        return melhor;
    }

    private CanalRelatorio telegram(Student aluno) {
        return new CanalRelatorio() {
            @Override
            public String registrar() throws Exception {
                enviarTexto(USUARIO_CLARA, "registrar aula");
                enviarTexto(USUARIO_CLARA, aluno.name());
                prepararArquivoTelegram(
                        "aula-" + aluno.id(), NormalizacaoAudioTeste.AUDIO_COM_FALA);
                enviarVoz(USUARIO_CLARA, "aula-" + aluno.id(), 1000);
                enviarCallback(USUARIO_CLARA, "v1:confal:");
                cenario.processarFila();
                return resultado(aluno, "REGISTRO_AULA", "AUDIO");
            }

            @Override
            public String responder(String texto) throws Exception {
                enviarTexto(USUARIO_CLARA, texto);
                cenario.processarFila();
                return resultado(aluno, "RESPOSTA_PERGUNTAS", "TEXTO");
            }

            @Override
            public String alterarPorTexto(String texto) throws Exception {
                enviarTexto(USUARIO_CLARA, texto);
                cenario.processarFila();
                return resultado(aluno, "ALTERACAO", "TEXTO");
            }

            @Override
            public String alterarPorAudio() throws Exception {
                prepararArquivoTelegram(
                        "pedido-" + aluno.id(), NormalizacaoAudioTeste.AUDIO_COM_FALA);
                enviarVoz(USUARIO_CLARA, "pedido-" + aluno.id(), 1000);
                cenario.processarFila();
                return resultado(aluno, "ALTERACAO", "AUDIO");
            }

            @Override
            public String aprovar() throws Exception {
                enviarTexto(USUARIO_CLARA, "aprovar");
                enviarTexto(USUARIO_CLARA, "sim");
                return jdbc.queryForObject(
                        "select status from relatorio_aula where aluno_id = ?",
                        String.class,
                        aluno.id());
            }

            @Override
            public String aprovarDeNovo() throws Exception {
                API_BOTS.resetRequests();
                enviarCallback(USUARIO_CLARA, "v1:foco:a" + relatorioDe(aluno));
                return codigoDaUltimaMensagem();
            }
        };
    }

    @Test
    void mesmoRoteiroMesmoRelatorioNosDoisCanais() throws Exception {
        Cookie sessao = entrarNaWeb();
        List<String> naWeb = roteiro(web(sessao, matteo)).executar(null);
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        List<String> noBot = roteiro(telegram(helena)).executar(null);

        assertThat(noBot).containsExactlyElementsOf(naWeb);
        assertThat(naWeb)
                .containsExactly(
                        "enviar áudio = PERGUNTAS_PENDENTES",
                        "responder = PRONTO_PARA_CONFERENCIA",
                        "alterar por texto = NOVA_VERSAO",
                        "alterar por áudio = NOVA_VERSAO",
                        "aprovar = APROVADO",
                        "aprovar de novo = RELATORIO_JA_APROVADO");

        List<String> resumoWeb = resumo(matteo, "WEB");
        assertThat(resumo(helena, "TELEGRAM")).containsExactlyElementsOf(resumoWeb);
        assertThat(resumoWeb)
                .contains("APROVADO vigente=3 aprovada=3 canalOrigem=CANAL canalAprovacao=CANAL")
                .anySatisfy(
                        l -> assertThat(l).startsWith("RESPOSTA_PERGUNTAS TEXTO APLICADA base=0"))
                .anySatisfy(l -> assertThat(l).startsWith("ALTERACAO TEXTO APLICADA base=1"))
                .anySatisfy(l -> assertThat(l).startsWith("ALTERACAO AUDIO APLICADA base=2"))
                .anySatisfy(l -> assertThat(l).startsWith("pdfs=").contains("OFICIAL"));

        // FR-056: uma linha por chamada de IA, todas ligadas ao relatório, nos dois canais
        List<String> custosWeb = custos(matteo);
        assertThat(custos(helena)).containsExactlyElementsOf(custosWeb);
        assertThat(custosWeb)
                .containsExactly(
                        "ESTRUTURACAO x1 sucesso=true duracao=true custo=true",
                        "INCORPORACAO_RESPOSTAS x1 sucesso=true duracao=true custo=true",
                        "REVISAO x2 sucesso=true duracao=true custo=true",
                        "TRANSCRICAO x2 sucesso=true duracao=true custo=true");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from chamada_ia where relatorio_id is null",
                                Integer.class))
                .as("chamadas sem relatório")
                .isZero();
        for (Student aluno : List.of(matteo, helena)) {
            assertThat(
                            jdbc.queryForObject(
                                    "select sum(custo_estimado_usd) from chamada_ia where relatorio_id = ?",
                                    BigDecimal.class,
                                    relatorioDe(aluno)))
                    .as("custo total do relatório")
                    .isNotNull();
            assertThat(
                            jdbc.queryForObject(
                                    "select tempo_processamento_ms from relatorio_aula where id = ?",
                                    Long.class,
                                    relatorioDe(aluno)))
                    .as("tempo de processamento")
                    .isGreaterThan(0L);
        }
    }

    private static RoteiroParidade roteiroDeDuasRodadas(CanalRelatorio canal) {
        return new RoteiroParidade()
                .passo("enviar áudio", c -> canal.registrar())
                .passo("1ª resposta", c -> canal.responder("Foi o Czerny."))
                .passo("2ª resposta", c -> canal.responder("Escala de Dó maior."));
    }

    /** Rodada e resultado de cada resposta às perguntas, na ordem em que foram dadas. */
    private List<String> rodadas(Student aluno) {
        return jdbc.queryForList(
                "select 'rodada ' || rodada || ' ' || resultado from interacao_relatorio"
                        + " where relatorio_id = ? and tipo = 'RESPOSTA_PERGUNTAS' order by rodada",
                String.class,
                relatorioDe(aluno));
    }

    @Test
    void duasRodadasDePerguntasEATerceiraIgnoradaNosDoisCanais() throws Exception {
        LlmProgramavelTeste programavel =
                new LlmProgramavelTeste()
                        .perguntasNasIncorporacoes(
                                List.of("E a escala?"),
                                List.of("Mais alguma coisa?"),
                                List.of("E a escala?"),
                                List.of("Mais alguma coisa?"));
        doAnswer(programavel).when(llm).generateJson(any(), any(), any());
        Cookie sessao = entrarNaWeb();
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);

        List<String> naWeb = roteiroDeDuasRodadas(web(sessao, matteo)).executar(null);
        List<String> noBot = roteiroDeDuasRodadas(telegram(helena)).executar(null);

        assertThat(noBot).containsExactlyElementsOf(naWeb);
        assertThat(naWeb)
                .containsExactly(
                        "enviar áudio = PERGUNTAS_PENDENTES",
                        "1ª resposta = PERGUNTAS_PENDENTES",
                        "2ª resposta = PRONTO_PARA_CONFERENCIA");
        List<String> resumoWeb = resumo(matteo, "WEB");
        assertThat(resumo(helena, "TELEGRAM")).containsExactlyElementsOf(resumoWeb);
        assertThat(resumoWeb.getFirst())
                .isEqualTo(
                        "AGUARDANDO_CONFERENCIA vigente=1 aprovada=- canalOrigem=CANAL canalAprovacao=-");
        assertThat(resumoWeb)
                .contains("versão 1 RESPOSTA_PERGUNTAS canal=CANAL")
                .noneMatch(l -> l.startsWith("versão 2"));
        for (Student aluno : List.of(matteo, helena)) {
            assertThat(rodadas(aluno)).containsExactly("rodada 1 APLICADA", "rodada 2 APLICADA");
            assertThat(
                            jdbc.queryForObject(
                                    "select rodadas_perguntas from relatorio_aula where id = ?",
                                    Integer.class,
                                    relatorioDe(aluno)))
                    .isEqualTo(2);
            assertThat(
                            jdbc.queryForObject(
                                    "select jsonb_array_length(perguntas_pendentes)"
                                            + " from relatorio_aula where id = ?",
                                    Integer.class,
                                    relatorioDe(aluno)))
                    .as("as perguntas da 3ª rodada são ignoradas")
                    .isZero();
        }
        // a IA recebeu a 2ª rodada com as perguntas da 1ª incorporação, em cada canal
        assertThat(programavel.incorporacoes()).hasSize(4);
        assertThat(programavel.incorporacoes().get(1).perguntas()).containsExactly("E a escala?");
        assertThat(programavel.incorporacoes().get(3).perguntas()).containsExactly("E a escala?");
    }

    private Cookie entrarNaWeb() throws Exception {
        var resultado =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + CenarioIT.CODIGO_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = resultado.getResponse().getCookie("sessao");
        return new Cookie(cookie.getName(), cookie.getValue());
    }
}
