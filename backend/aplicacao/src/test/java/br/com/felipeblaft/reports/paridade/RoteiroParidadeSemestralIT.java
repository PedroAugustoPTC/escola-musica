package br.com.felipeblaft.reports.paridade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.configuracao.CatalogoMensagens;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.suporte.CenarioIT;
import br.com.felipeblaft.reports.suporte.TelegramConversaBase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Paridade do semestral (R-16, SC-006): período sem aulas, geração, alteração, aprovação e nova
 * geração recusada, pela Web e pelo bot; o semestral resultante é igual, salvo o canal.
 */
class RoteiroParidadeSemestralIT extends TelegramConversaBase {

    @Autowired CatalogoMensagens catalogo;

    private Teacher clara;
    private Student matteo;
    private Student helena;
    private Cookie sessao;

    interface CanalSemestral {
        String gerarSemAulas() throws Exception;

        String gerar() throws Exception;

        String alterar(String texto) throws Exception;

        String aprovar() throws Exception;

        String gerarDeNovo() throws Exception;
    }

    @BeforeEach
    void carregar() throws Exception {
        clara = cenario.clara();
        matteo = cenario.aluno(clara, "Matteo Ferreira", "Piano");
        helena = cenario.aluno(clara, "Helena Prado", "Violino");
        for (Student aluno : List.of(matteo, helena)) {
            cenario.aulaAprovada(clara, aluno, LocalDate.of(2026, 3, 10), "Escala de Dó");
            cenario.aulaAprovada(clara, aluno, LocalDate.of(2026, 5, 20), "Estudo nº 5");
        }
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        cenario.tempoPorTrabalho = java.time.Duration.ofSeconds(2);
        MvcResult entrada =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + CenarioIT.CODIGO_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = entrada.getResponse().getCookie("sessao");
        sessao = new Cookie(cookie.getName(), cookie.getValue());
    }

    @AfterEach
    void restaurarRelogio() {
        cenario.tempoPorTrabalho = java.time.Duration.ZERO;
    }

    /** Uma linha por tarefa de IA do relatório: quantas chamadas e se os dados de custo vieram. */
    private List<String> custos(Student aluno) {
        return jdbc.queryForList(
                "select tarefa || ' x' || count(*) || ' sucesso=' || bool_and(sucesso)"
                        + " || ' duracao=' || bool_and(duracao_ms is not null)"
                        + " || ' custo=' || bool_and(custo_estimado_usd is not null)"
                        + " from chamada_ia where relatorio_tipo = 'SEMESTRAL' and relatorio_id = ?"
                        + " group by tarefa order by tarefa",
                String.class,
                semestral(aluno));
    }

    private UUID semestral(Student aluno) {
        return jdbc.queryForObject(
                "select id from relatorio_semestral where aluno_id = ? and status <> 'CANCELADO'",
                UUID.class,
                aluno.id());
    }

    private int vigente(Student aluno) {
        return jdbc.queryForObject(
                "select versao_vigente from relatorio_semestral where id = ?",
                Integer.class,
                semestral(aluno));
    }

    private String resultado(Student aluno, String tipo) {
        return jdbc.queryForObject(
                "select resultado_codigo from trabalho_processamento where aluno_id = ? and tipo = ?",
                String.class,
                aluno.id(),
                tipo);
    }

    private String webPost(String caminho, Object corpo) throws Exception {
        MvcResult resultado =
                mvc.perform(
                                post("/api/v1" + caminho)
                                        .cookie(sessao)
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(JSON.writeValueAsString(corpo)))
                        .andReturn();
        return resultado.getResponse().getStatus() / 100 == 2
                ? "ACEITO"
                : JSON.readTree(resultado.getResponse().getContentAsString())
                        .path("codigo")
                        .asString();
    }

    private CanalSemestral web(Student aluno) {
        return new CanalSemestral() {
            @Override
            public String gerarSemAulas() throws Exception {
                return webPost(
                        "/alunos/" + aluno.id() + "/relatorios-semestrais",
                        Map.of("periodoChave", "2026-2"));
            }

            @Override
            public String gerar() throws Exception {
                webPost(
                        "/alunos/" + aluno.id() + "/relatorios-semestrais",
                        Map.of("periodoChave", "2026-1"));
                cenario.processarFila();
                return resultado(aluno, "CONSOLIDACAO_SEMESTRAL");
            }

            @Override
            public String alterar(String texto) throws Exception {
                webPost(
                        "/relatorios-semestrais/" + semestral(aluno) + "/alteracoes",
                        Map.of("texto", texto, "versaoBase", vigente(aluno)));
                cenario.processarFila();
                return resultado(aluno, "ALTERACAO");
            }

            @Override
            public String aprovar() throws Exception {
                webPost(
                        "/relatorios-semestrais/" + semestral(aluno) + "/aprovacao",
                        Map.of("versaoConfirmada", vigente(aluno)));
                return jdbc.queryForObject(
                        "select status from relatorio_semestral where aluno_id = ?",
                        String.class,
                        aluno.id());
            }

            @Override
            public String gerarDeNovo() throws Exception {
                return webPost(
                        "/alunos/" + aluno.id() + "/relatorios-semestrais",
                        Map.of("periodoChave", "2026-1"));
            }
        };
    }

    /** Código do catálogo cujo texto (sem placeholders) é o da última mensagem do bot. */
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

    private CanalSemestral bot(Student aluno) {
        return new CanalSemestral() {
            private void escolherAluno() throws Exception {
                enviarTexto(USUARIO_CLARA, "semestral");
                enviarTexto(USUARIO_CLARA, aluno.name());
            }

            @Override
            public String gerarSemAulas() throws Exception {
                escolherAluno();
                enviarCallback(USUARIO_CLARA, "v1:per:0");
                return codigoDaUltimaMensagem();
            }

            @Override
            public String gerar() throws Exception {
                escolherAluno();
                enviarCallback(USUARIO_CLARA, "v1:per:1");
                enviarCallback(USUARIO_CLARA, "v1:gerar:");
                cenario.processarFila();
                return resultado(aluno, "CONSOLIDACAO_SEMESTRAL");
            }

            @Override
            public String alterar(String texto) throws Exception {
                enviarTexto(USUARIO_CLARA, texto);
                cenario.processarFila();
                return resultado(aluno, "ALTERACAO");
            }

            @Override
            public String aprovar() throws Exception {
                enviarTexto(USUARIO_CLARA, "aprovar");
                enviarTexto(USUARIO_CLARA, "sim");
                return jdbc.queryForObject(
                        "select status from relatorio_semestral where aluno_id = ?",
                        String.class,
                        aluno.id());
            }

            @Override
            public String gerarDeNovo() throws Exception {
                escolherAluno();
                enviarCallback(USUARIO_CLARA, "v1:per:1");
                return codigoDaUltimaMensagem();
            }
        };
    }

    private static List<String> roteiro(CanalSemestral canal) throws Exception {
        List<String> registro = new ArrayList<>();
        registro.add("período sem aulas = " + canal.gerarSemAulas());
        registro.add("gerar = " + canal.gerar());
        registro.add("alterar = " + canal.alterar("Parecer: excelente semestre."));
        registro.add("aprovar = " + canal.aprovar());
        registro.add("gerar de novo = " + canal.gerarDeNovo());
        return registro;
    }

    private List<String> resumo(Student aluno, String canal) {
        UUID id =
                jdbc.queryForObject(
                        "select id from relatorio_semestral where aluno_id = ?",
                        UUID.class,
                        aluno.id());
        List<String> linhas = new ArrayList<>();
        linhas.add(
                jdbc.queryForObject(
                        "select status || ' vigente=' || versao_vigente || ' aprovada=' || versao_aprovada"
                                + " || ' aulas=' || quantidade_aulas_consideradas || ' periodo=' || periodo_chave"
                                + " || ' origem=' || canal_origem || ' aprovacao=' || canal_aprovacao"
                                + " || ' parecer=' || (conteudo ->> 'parecerFinal')"
                                + " from relatorio_semestral where id = ?",
                        String.class,
                        id));
        linhas.addAll(
                jdbc.queryForList(
                        "select tipo || ' ' || meio || ' ' || resultado || ' base=' || versao_base || ' canal=' || canal"
                                + " from interacao_relatorio where relatorio_id = ? order by versao_base",
                        String.class,
                        id));
        linhas.addAll(
                jdbc.queryForList(
                        "select 'versão ' || v.numero || ' ' || v.origem || ' ' || d.tipo from versao_relatorio v"
                                + " join documento_pdf d on d.versao_id = v.id where v.relatorio_id = ?"
                                + " order by v.numero, d.tipo",
                        String.class,
                        id));
        return linhas.stream().map(l -> l.replace(canal, "CANAL")).toList();
    }

    @Test
    void mesmoRoteiroMesmoSemestralNosDoisCanais() throws Exception {
        List<String> naWeb = roteiro(web(matteo));
        List<String> noBot = roteiro(bot(helena));

        assertThat(noBot).containsExactlyElementsOf(naWeb);
        assertThat(naWeb)
                .containsExactly(
                        "período sem aulas = SEM_AULAS_APROVADAS_NO_PERIODO",
                        "gerar = PRONTO_PARA_CONFERENCIA",
                        "alterar = NOVA_VERSAO",
                        "aprovar = APROVADO",
                        "gerar de novo = SEMESTRAL_JA_APROVADO");
        List<String> resumoWeb = resumo(matteo, "WEB");
        assertThat(resumo(helena, "TELEGRAM")).containsExactlyElementsOf(resumoWeb);
        assertThat(resumoWeb.getFirst())
                .isEqualTo(
                        "APROVADO vigente=2 aprovada=2 aulas=2 periodo=2026-1 origem=CANAL aprovacao=CANAL"
                                + " parecer=Parecer: excelente semestre.");
        assertThat(resumoWeb)
                .contains("versão 2 ALTERACAO OFICIAL", "versão 1 CONSOLIDACAO CONFERENCIA");

        // FR-056: a consolidação e a revisão ligadas ao relatório, nos dois canais
        List<String> custosWeb = custos(matteo);
        assertThat(custos(helena)).containsExactlyElementsOf(custosWeb);
        assertThat(custosWeb)
                .containsExactly(
                        "CONSOLIDACAO x1 sucesso=true duracao=true custo=true",
                        "REVISAO x1 sucesso=true duracao=true custo=true");
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
                                    java.math.BigDecimal.class,
                                    semestral(aluno)))
                    .isNotNull();
            assertThat(
                            jdbc.queryForObject(
                                    "select tempo_processamento_ms from relatorio_semestral where id = ?",
                                    Long.class,
                                    semestral(aluno)))
                    .as("tempo de processamento")
                    .isGreaterThan(0L);
        }
    }
}
