package br.com.felipeblaft.relatorios.entrada.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.dominio.cadastro.Professor;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.NormalizacaoAudioTeste;
import br.com.felipeblaft.relatorios.suporte.TelegramConversaBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Perguntas, conferência, alteração, aprovação e cancelamento no chat (estados #3 a #6, 5.2). */
class ConferenciaConversaIT extends TelegramConversaBase {

    private static final String LEMBRETE =
            "Texto ou áudio enviado aqui será aplicado a este relatório.";

    private UUID relatorioId;

    @BeforeEach
    void chegarAsPerguntas() throws Exception {
        Professor clara = cenario.clara();
        cenario.aluno(clara, "Matteo Ferreira", "Piano");
        cenario.aluno(clara, "Helena Prado", "Violino");
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        prepararArquivoTelegram("aula", NormalizacaoAudioTeste.AUDIO_COM_FALA);
        enviarTexto(USUARIO_CLARA, "Matteo");
        enviarVoz(USUARIO_CLARA, "aula", 1000);
        enviarCallback(USUARIO_CLARA, "v1:confal:");
        cenario.processarFila();
        relatorioId = jdbc.queryForObject("select id from relatorio_aula", UUID.class);
        API_BOTS.resetRequests();
    }

    private Map<String, Object> conversa() {
        return jdbc.queryForMap("select * from vinculo_telegram");
    }

    private String status() {
        return jdbc.queryForObject("select status from relatorio_aula", String.class);
    }

    /** Responde às perguntas e processa, chegando à conferência da versão 1. */
    private void chegarAConferencia() throws Exception {
        enviarTexto(USUARIO_CLARA, "Estudou bem; vai preparar a valsa.");
        cenario.processarFila();
        API_BOTS.resetRequests();
    }

    @Test
    void respostaPorTextoVaiDiretoParaOProcessamentoEChegaAConferenciaComPdf() throws Exception {
        enviarTexto(USUARIO_CLARA, "Estudou bem; vai preparar a valsa.");

        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("<i>Incorporando suas respostas…</i>");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from trabalho_processamento where tipo = 'RESPOSTA_PERGUNTAS' and status = 'PENDENTE'",
                                Integer.class))
                .isEqualTo(1);

        cenario.processarFila();

        List<String> textos = textosEnviados();
        String conferencia =
                textos.stream()
                        .filter(t -> t.contains("Confira o relatório antes de aprovar"))
                        .findFirst()
                        .orElseThrow();
        assertThat(conferencia)
                .startsWith(
                        "Respostas incorporadas ao relatório. Entendi: “Estudou bem; vai preparar a valsa.”")
                .contains("Aluno: Matteo Ferreira")
                .contains("Professor: Clara Souza")
                .contains("Data da aula: 21/09/2026")
                .contains("Versão 1")
                .contains("<b>Conteúdos trabalhados</b>")
                .contains("<b>Observações</b>");
        API_BOTS.verify(1, postRequestedFor(urlPathMatching(".*/sendDocument")));
        JsonNode acoes = ultimaMensagem();
        assertThat(acoes.get("text").asString()).contains(LEMBRETE);
        assertThat(botoes(acoes))
                .containsExactly("v1:aprov:1", "v1:alt:", "v1:pdf:", "v1:canc:", "v1:reg:");
        assertThat(conversa().get("versao_apresentada")).isEqualTo(1);
        assertThat(status()).isEqualTo("AGUARDANDO_CONFERENCIA");
    }

    @Test
    void respostaPorAudioNaoPedeConfirmacao() throws Exception {
        prepararArquivoTelegram("resposta", NormalizacaoAudioTeste.AUDIO_COM_FALA);

        enviarVoz(USUARIO_CLARA, "resposta", 1000);

        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("<i>Transcrevendo o pedido…</i>");
        assertThat(conversa().get("audio_pendente_file_id")).isNull();
        cenario.processarFila();
        assertThat(status()).isEqualTo("AGUARDANDO_CONFERENCIA");
        assertThat(String.join("\n", textosEnviados()))
                .contains("Respostas incorporadas ao relatório. Entendi:");
    }

    @Test
    void textoDuranteOProcessamentoInformaAEtapa() throws Exception {
        enviarTexto(USUARIO_CLARA, "Estudou bem.");
        API_BOTS.resetRequests();

        enviarTexto(USUARIO_CLARA, "mais uma coisa");

        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo(
                        "Este relatório ainda está sendo processado (Incorporando suas respostas). Aguarde a conclusão.");
    }

    @Test
    void alteracaoPorTextoGeraNovaVersaoComTextoEntendido() throws Exception {
        chegarAConferencia();

        enviarTexto(USUARIO_CLARA, "Nas observações, diga que a aula foi ótima.");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("<i>Aplicando a alteração…</i>");
        assertThat(
                        jdbc.queryForObject(
                                "select parametros->>'versaoBase' from trabalho_processamento where tipo = 'ALTERACAO'",
                                String.class))
                .isEqualTo("1");
        cenario.processarFila();

        String conferencia =
                textosEnviados().stream()
                        .filter(t -> t.contains("Confira o relatório"))
                        .findFirst()
                        .orElseThrow();
        assertThat(conferencia)
                .startsWith(
                        "Alteração aplicada. Confira a versão 2.\nEntendi: “Nas observações, diga que a aula foi ótima.”")
                .contains("Versão 2");
        assertThat(botoes(ultimaMensagem())).contains("v1:aprov:2");
        assertThat(conversa().get("versao_apresentada")).isEqualTo(2);
    }

    @Test
    void alteracaoPorAudioMostraOQueFoiEntendido() throws Exception {
        chegarAConferencia();
        prepararArquivoTelegram("pedido", NormalizacaoAudioTeste.AUDIO_COM_FALA);

        enviarVoz(USUARIO_CLARA, "pedido", 1000);
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("<i>Transcrevendo o pedido…</i>");
        cenario.processarFila();

        assertThat(String.join("\n", textosEnviados()))
                .contains("Alteração aplicada. Confira a versão 2.")
                .contains("Entendi: “Hoje trabalhamos o estudo número cinco de Czerny");
    }

    @Test
    void nomeExatoDeOutroAlunoPerguntaSeQuerTrocar() throws Exception {
        chegarAConferencia();

        enviarTexto(USUARIO_CLARA, "Helena Prado");

        JsonNode mensagem = ultimaMensagem();
        assertThat(mensagem.get("text").asString())
                .isEqualTo(
                        "Você quer trocar para Helena Prado? O relatório de Matteo Ferreira continua em aberto.");
        assertThat(rotulosBotoes(mensagem)).containsExactly("Confirmar Helena Prado", "Voltar");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from trabalho_processamento where tipo = 'ALTERACAO'",
                                Integer.class))
                .isZero();

        enviarCallback(USUARIO_CLARA, "v1:aluno:0");
        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Aula de hoje, 21/09/2026, com Helena Prado.");
        assertThat(conversa().get("relatorio_foco_id")).isNull();
        assertThat(status()).isEqualTo("AGUARDANDO_CONFERENCIA");
    }

    @Test
    void aprovarPorPalavraChavePedeConfirmacaoCitandoAVersao() throws Exception {
        chegarAConferencia();

        enviarTexto(USUARIO_CLARA, "aprovar");
        JsonNode confirmacao = ultimaMensagem();
        assertThat(confirmacao.get("text").asString())
                .isEqualTo(
                        "Aprovar a versão 1? O relatório entrará no histórico do aluno e não poderá mais ser alterado.");
        assertThat(botoes(confirmacao)).containsExactly("v1:aprovok:1", "v1:inicio:");
        assertThat(rotulosBotoes(confirmacao).getFirst()).isEqualTo("Sim, aprovar a versão 1");

        enviarTexto(USUARIO_CLARA, "sim");

        JsonNode aprovado = ultimaMensagem();
        assertThat(aprovado.get("text").asString())
                .isEqualTo(
                        "Relatório aprovado em 21/09/2026 às 10:00. Ele já consta no histórico do aluno.");
        assertThat(rotulosBotoes(aprovado)).containsExactly("Registrar outra aula", "Histórico");
        assertThat(status()).isEqualTo("APROVADO");
        Map<String, Object> estado = conversa();
        assertThat(estado.get("relatorio_foco_id")).isNull();
        assertThat(estado.get("aluno_selecionado_id")).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from documento_pdf where tipo = 'OFICIAL'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select canal_aprovacao from relatorio_aula", String.class))
                .isEqualTo("TELEGRAM");
    }

    @Test
    void aprovarPeloBotaoComVersaoAntigaMostraAVigente() throws Exception {
        chegarAConferencia();
        enviarTexto(USUARIO_CLARA, "Nas observações, diga que a aula foi ótima.");
        cenario.processarFila();
        API_BOTS.resetRequests();

        enviarCallback(USUARIO_CLARA, "v1:aprov:1");

        assertThat(textosEnviados().getFirst())
                .isEqualTo(
                        "Esta versão está desatualizada: o relatório foi alterado e agora está na versão 2. Confira a versão atual antes de continuar.");
        assertThat(botoes(ultimaMensagem())).contains("v1:aprov:2");

        enviarCallback(USUARIO_CLARA, "v1:aprov:2");
        enviarCallback(USUARIO_CLARA, "v1:aprovok:2");
        assertThat(status()).isEqualTo("APROVADO");
        assertThat(jdbc.queryForObject("select versao_aprovada from relatorio_aula", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void botaoDeConfirmacaoSemConfirmacaoPendenteExpirou() throws Exception {
        chegarAConferencia();

        enviarCallback(USUARIO_CLARA, "v1:aprovok:1");

        assertThat(textosEnviados().getFirst())
                .isEqualTo("Esta opção não vale mais. Veja abaixo a etapa atual.");
        assertThat(status()).isEqualTo("AGUARDANDO_CONFERENCIA");
    }

    @Test
    void cancelarPeloBotaoComConfirmacaoLimpaOFocoEMostraOInicio() throws Exception {
        chegarAConferencia();

        enviarCallback(USUARIO_CLARA, "v1:canc:");
        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo(
                        "Cancelar este relatório? Ele deixará de aparecer para você e não poderá ser recuperado.");
        enviarCallback(USUARIO_CLARA, "v1:cancok:");

        assertThat(textosEnviados()).contains("Relatório cancelado.");
        assertThat(ultimaMensagem().get("text").asString()).contains("Olá, Clara");
        assertThat(status()).isEqualTo("CANCELADO");
        assertThat(conversa().get("relatorio_foco_id")).isNull();
    }

    @Test
    void outroTextoDesisteDaConfirmacaoESeraUmaAlteracao() throws Exception {
        chegarAConferencia();
        enviarTexto(USUARIO_CLARA, "cancelar");

        enviarTexto(USUARIO_CLARA, "Acrescente que o aluno chegou atrasado.");

        assertThat(ultimaMensagem().get("text").asString())
                .isEqualTo("<i>Aplicando a alteração…</i>");
        assertThat(status()).isEqualTo("AGUARDANDO_CONFERENCIA");
    }

    @Test
    void botaoPdfReenviaODocumento() throws Exception {
        chegarAConferencia();

        enviarCallback(USUARIO_CLARA, "v1:pdf:");

        API_BOTS.verify(1, postRequestedFor(urlPathMatching(".*/sendDocument")));
    }

    @Test
    void falhaDeProcessamentoOfereceTentarDeNovo() throws Exception {
        jdbc.update(
                "update relatorio_aula set status = 'FALHA_PROCESSAMENTO', etapa_processamento = null, motivo_falha = 'TESTE'");

        enviarTexto(USUARIO_CLARA, "e agora?");
        assertThat(ultimaMensagem().get("text").asString())
                .startsWith("Não foi possível concluir o relatório.");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:tent:", "v1:canc:");

        enviarCallback(USUARIO_CLARA, "v1:tent:");

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from trabalho_processamento where tipo = 'NOVA_TENTATIVA'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(textosEditados()).contains("<i>Estruturando o relatório…</i>");
    }

    @Test
    void registrarAulaLimpaOFocoSemCancelar() throws Exception {
        chegarAConferencia();

        enviarTexto(USUARIO_CLARA, "registrar aula");

        assertThat(ultimaMensagem().get("text").asString()).isEqualTo("Para qual aluno?");
        assertThat(conversa().get("relatorio_foco_id")).isNull();
        assertThat(status()).isEqualTo("AGUARDANDO_CONFERENCIA");
    }

    @Test
    void continuarNoChatPoeORelatorioEmFoco() throws Exception {
        chegarAConferencia();
        enviarTexto(USUARIO_CLARA, "início");
        assertThat(conversa().get("relatorio_foco_id")).isNull();
        assertThat(botoes(ultimaMensagem())).contains("v1:foco:a" + relatorioId);
        API_BOTS.resetRequests();

        enviarCallback(USUARIO_CLARA, "v1:foco:a" + relatorioId);

        assertThat(String.join("\n", textosEnviados()))
                .contains("Confira o relatório antes de aprovar");
        assertThat(conversa().get("relatorio_foco_id")).isEqualTo(relatorioId);
    }
}
