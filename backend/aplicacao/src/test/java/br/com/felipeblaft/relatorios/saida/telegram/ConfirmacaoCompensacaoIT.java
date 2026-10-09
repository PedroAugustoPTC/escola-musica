package br.com.felipeblaft.relatorios.saida.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso.NovaAcao;
import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.EntregaOrquestradorBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * V7, C10, FR-007b, FR-017: o backend decide a compensação quando uma edição falha por mensagem não
 * editável; "message is not modified" é sucesso; falha de edição de etapa regrava o id da mensagem
 * pela compensação; código desconhecido vira {@code ERRO_TELEGRAM} sem derrubar as demais
 * confirmações.
 */
class ConfirmacaoCompensacaoIT extends EntregaOrquestradorBase {

    @Autowired GravarAcoesCanalCasoDeUso gravar;

    private void stubTelegram(String metodo, int status, String corpo) {
        API_BOTS.stubFor(
                post(urlPathMatching(".*/" + metodo))
                        .willReturn(
                                aResponse()
                                        .withStatus(status)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(corpo)));
    }

    private MvcResult confirmar(String resultadosJson) throws Exception {
        return mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/interno/v1/canal/telegram/confirmacoes")
                                .header("X-N8N-Service-Token", TOKEN_SERVICO)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"versao\":\"v1\",\"resultados\":["
                                                + resultadosJson
                                                + "]}"))
                .andReturn();
    }

    /** Edição proativa de uma mensagem do professor, com a alternativa de mensagem nova. */
    private String publicarEdicao() {
        gravar.executar(
                "p:edicao:1",
                OrigemAcaoCanal.PROATIVA,
                null,
                List.of(
                        new NovaAcao(
                                "p:edicao:1:0",
                                TipoAcaoCanal.EDICAO,
                                USUARIO_CLARA,
                                clara.id(),
                                null,
                                "{\"tipo\":\"EDICAO\",\"chatId\":"
                                        + USUARIO_CLARA
                                        + ",\"mensagemId\":900,\"texto\":\"Texto da edição\","
                                        + "\"teclado\":{\"linhas\":[]}}",
                                "{\"tipo\":\"MENSAGEM\",\"chatId\":"
                                        + USUARIO_CLARA
                                        + ",\"texto\":\"Texto da edição\"}")));
        return "p:edicao:1:0";
    }

    @Test
    void edicaoNaoEditavelGeraCompensacaoComOMesmoConteudoEntregueUmaVezSemRepetirAEdicao()
            throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        stubTelegram(
                "editMessageText",
                400,
                "{\"ok\":false,\"description\":\"Bad Request: message to edit not found\"}");
        String edicao = publicarEdicao();

        drenar(6);

        assertThat(estado(edicao)).isEqualTo("FALHOU");
        assertThat(
                        jdbc.queryForObject(
                                "select codigo_motivo from acao_canal where id = ?",
                                String.class,
                                edicao))
                .isEqualTo("MENSAGEM_NAO_EDITAVEL");
        assertThat(estado(edicao + "-c")).isEqualTo("EXECUTADA");
        assertThat(
                        jdbc.queryForObject(
                                "select lote_id from acao_canal where id = ?",
                                String.class,
                                edicao + "-c"))
                .isEqualTo("c:" + edicao);
        assertThat(chamadas("editMessageText")).hasSize(1);
        assertThat(textosEnviados()).containsExactly("Texto da edição");
        // a compensação foi entregue uma só vez, como lote próprio
        assertThat(entregas()).hasSize(2);
        assertThat(corpo(entregas().get(1)).path("origem").asString()).isEqualTo("COMPENSACAO");
        assertThat(corpo(entregas().get(1)).get("acoes").get(0).path("acaoId").asString())
                .isEqualTo(edicao + "-c");
    }

    /** PDF proativo de um documento que não existe mais, com o aviso e o botão como alternativa. */
    private String publicarDocumentoIndisponivel() {
        gravar.executar(
                "p:pdf:1",
                OrigemAcaoCanal.PROATIVA,
                null,
                List.of(
                        new NovaAcao(
                                "p:pdf:1:0",
                                TipoAcaoCanal.DOCUMENTO,
                                USUARIO_CLARA,
                                clara.id(),
                                null,
                                "{\"tipo\":\"DOCUMENTO\",\"chatId\":"
                                        + USUARIO_CLARA
                                        + ",\"documentoId\":\""
                                        + "Z".repeat(43)
                                        + "\",\"nomeArquivo\":\"relatorio.pdf\","
                                        + "\"tipoConteudo\":\"application/pdf\"}",
                                "{\"tipo\":\"MENSAGEM\",\"chatId\":"
                                        + USUARIO_CLARA
                                        + ",\"texto\":\"Não consegui enviar o PDF agora.\","
                                        + "\"teclado\":{\"linhas\":[[{\"rotulo\":\"Ver PDF\","
                                        + "\"dados\":\"v1:pdf:\"}]]}}")));
        return "p:pdf:1:0";
    }

    @Test
    void pdfIndisponivelEntregaOAvisoComOBotaoUmaSoVezSemReenviarODocumento() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        String documento = publicarDocumentoIndisponivel();

        drenar(6);

        assertThat(estado(documento)).isEqualTo("FALHOU");
        assertThat(
                        jdbc.queryForObject(
                                "select codigo_motivo from acao_canal where id = ?",
                                String.class,
                                documento))
                .isEqualTo("DOCUMENTO_INDISPONIVEL");
        assertThat(estado(documento + "-c")).isEqualTo("EXECUTADA");
        assertThat(textosEnviados()).containsExactly("Não consegui enviar o PDF agora.");
        assertThat(botoes(ultimaMensagem())).containsExactly("v1:pdf:");
        assertThat(chamadas("sendDocument")).isEmpty();
        // a compensação é um lote próprio, entregue uma só vez e sem o documento
        assertThat(entregas()).hasSize(2);
        assertThat(corpo(entregas().get(1)).path("origem").asString()).isEqualTo("COMPENSACAO");
        assertThat(corpo(entregas().get(1)).get("acoes").get(0).path("acaoId").asString())
                .isEqualTo(documento + "-c");
        assertThat(corpo(entregas().get(1)).toString()).doesNotContain("documentoId");
    }

    @Test
    void compensacaoDePdfQueFalhaNaoGeraOutra() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        stubTelegram(
                "sendMessage",
                403,
                "{\"ok\":false,\"description\":\"Forbidden: bot was blocked by the user\"}");
        String documento = publicarDocumentoIndisponivel();

        drenar(8);

        assertThat(estado(documento + "-c")).isEqualTo("FALHOU");
        assertThat(jdbc.queryForObject("select count(*) from acao_canal", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void mensagemNaoModificadaEExecutadaESemCompensacao() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        stubTelegram(
                "editMessageText",
                400,
                "{\"ok\":false,\"description\":\"Bad Request: message is not modified\"}");
        String edicao = publicarEdicao();

        drenar(6);

        assertThat(estado(edicao)).isEqualTo("EXECUTADA");
        assertThat(jdbc.queryForObject("select count(*) from acao_canal", Integer.class))
                .isEqualTo(1);
        assertThat(textosEnviados()).isEmpty();
    }

    @Test
    void compensacaoQueFalhaNaoGeraOutra() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        stubTelegram(
                "editMessageText",
                400,
                "{\"ok\":false,\"description\":\"Bad Request: message to edit not found\"}");
        stubTelegram(
                "sendMessage",
                403,
                "{\"ok\":false,\"description\":\"Forbidden: bot was blocked by the user\"}");
        String edicao = publicarEdicao();

        drenar(8);

        assertThat(estado(edicao + "-c")).isEqualTo("FALHOU");
        assertThat(
                        jdbc.queryForObject(
                                "select codigo_motivo from acao_canal where id = ?",
                                String.class,
                                edicao + "-c"))
                .isEqualTo("CHAT_INDISPONIVEL");
        assertThat(jdbc.queryForObject("select count(*) from acao_canal", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void falhaNaEdicaoDeEtapaEnviaNovaMensagemERegravaOIdNoTrabalho() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        TrabalhoProcessamento trabalho =
                TrabalhoProcessamento.novo(
                        TipoTrabalho.REGISTRO_AULA,
                        clara.id(),
                        matteo.id(),
                        null,
                        null,
                        Canal.TELEGRAM,
                        Meio.AUDIO,
                        EtapaTrabalho.ENVIANDO,
                        relogio.agora());
        trabalho.definirParametro(TrabalhoProcessamento.MENSAGEM_ETAPA, "55");
        fila.enfileirar(trabalho);
        trabalho.avancar(EtapaTrabalho.TRANSCREVENDO);
        // a mensagem de etapa 55 foi apagada pelo professor: a edição vai falhar
        stubTelegram(
                "editMessageText",
                400,
                "{\"ok\":false,\"description\":\"Bad Request: message to edit not found\"}");
        stubTelegram("sendMessage", 200, "{\"ok\":true,\"result\":{\"message_id\":777}}");

        notificacao.notificarEtapa(trabalho);
        drenar(6);

        String etapa = idsDasAcoes().get(0);
        assertThat(etapa).startsWith("p:etapa:" + trabalho.id() + ":TRANSCREVENDO:");
        assertThat(estado(etapa)).isEqualTo("FALHOU");
        assertThat(corpo(entregas().get(0)).get("acoes").get(0).path("editarMensagemId").asLong())
                .isEqualTo(55L);
        assertThat(estado(etapa + "-c")).isEqualTo("EXECUTADA");
        assertThat(mensagemDaEtapa(trabalho.id())).isEqualTo("777");

        // a etapa seguinte edita a mensagem NOVA, sem abrir outra
        stubTelegram("editMessageText", 200, "{\"ok\":true,\"result\":true}");
        API_BOTS.resetRequests();
        trabalho.avancar(EtapaTrabalho.ESTRUTURANDO);
        notificacao.notificarEtapa(trabalho);
        drenar(6);

        JsonNode proxima = corpo(entregas().get(entregas().size() - 1));
        assertThat(proxima.get("acoes").get(0).path("editarMensagemId").asLong()).isEqualTo(777L);
        assertThat(chamadas("sendMessage")).isEmpty();
        assertThat(chamadas("editMessageText")).hasSize(1);
        assertThat(chamadas("editMessageText").get(0).path("message_id").asLong()).isEqualTo(777L);
    }

    private String mensagemDaEtapa(UUID trabalho) {
        return jdbc.queryForObject(
                "select parametros->>'mensagemEtapa' from trabalho_processamento where id = ?",
                String.class,
                trabalho);
    }

    @Test
    void etapaExecutadaSemMensagemIdE400ENaoFinalizaMasAplicaAsIrmas() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        TrabalhoProcessamento trabalho =
                TrabalhoProcessamento.novo(
                        TipoTrabalho.REGISTRO_AULA,
                        clara.id(),
                        matteo.id(),
                        null,
                        null,
                        Canal.TELEGRAM,
                        Meio.AUDIO,
                        EtapaTrabalho.TRANSCREVENDO,
                        relogio.agora());
        gerarAvisoDe(helena);
        fila.enfileirar(trabalho); // depois do processamento da fila: este trabalho não tem áudio
        notificacao.notificarEtapa(trabalho);
        tarefa.despachar();
        tarefa.despachar();
        List<String> ids = idsDasAcoes();
        String etapa = ids.stream().filter(i -> i.startsWith("p:etapa:")).findFirst().orElseThrow();
        String aviso =
                ids.stream().filter(i -> i.startsWith("p:resultado:")).findFirst().orElseThrow();

        MvcResult resultado =
                confirmar(
                        "{\"acaoId\":\""
                                + etapa
                                + "\",\"situacao\":\"EXECUTADA\"},"
                                + "{\"acaoId\":\""
                                + aviso
                                + "\",\"situacao\":\"EXECUTADA\"}");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(estado(etapa)).isNotEqualTo("EXECUTADA");
        assertThat(estado(aviso)).isEqualTo("EXECUTADA");
    }

    @Test
    void codigoDesconhecidoViraErroTelegramSemPerderAsConfirmacoesIrmas() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        gerarAvisoDe(matteo);
        gerarAvisoDe(helena);
        tarefa.despachar();
        String primeira = idsDasAcoes().get(0);

        MvcResult resultado =
                confirmar(
                        "{\"acaoId\":\""
                                + primeira
                                + "\",\"situacao\":\"FALHOU\",\"codigoMotivo\":\"NOVO_CODIGO_DO_N8N\"},"
                                + "{\"acaoId\":\"u1-9\",\"situacao\":\"EXECUTADA\"}");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode corpo = JSON.readTree(resultado.getResponse().getContentAsString());
        assertThat(corpo.path("aceitas").asInt()).isEqualTo(1);
        assertThat(corpo.path("ignoradas")).hasSize(1);
        assertThat(
                        jdbc.queryForObject(
                                "select codigo_motivo from acao_canal where id = ?",
                                String.class,
                                primeira))
                .isEqualTo("ERRO_TELEGRAM");
    }

    @Test
    void confirmacaoRepetidaDivergenteOuDesconhecidaVaiParaIgnoradas() throws Exception {
        vincular(USUARIO_CLARA, CenarioIT.CODIGO_CLARA);
        gerarAvisoDe(matteo);
        tarefa.despachar();
        String acao = idsDasAcoes().get(0);

        String ok = "{\"acaoId\":\"" + acao + "\",\"situacao\":\"EXECUTADA\"}";
        assertThat(
                        JSON.readTree(confirmar(ok).getResponse().getContentAsString())
                                .path("aceitas")
                                .asInt())
                .isEqualTo(1);
        // repetida idêntica: sem efeito; divergente e desconhecida: ignoradas
        JsonNode segunda =
                JSON.readTree(
                        confirmar(
                                        ok
                                                + ",{\"acaoId\":\""
                                                + acao
                                                + "\",\"situacao\":\"FALHOU\",\"codigoMotivo\":\"ERRO_TELEGRAM\"},"
                                                + "{\"acaoId\":\"p:nao:existe:0\",\"situacao\":\"EXECUTADA\"}")
                                .getResponse()
                                .getContentAsString());
        assertThat(segunda.path("aceitas").asInt()).isEqualTo(1);
        assertThat(segunda.path("ignoradas")).hasSize(2);
        assertThat(estado(acao)).isEqualTo("EXECUTADA");
        assertThat(API_BOTS.findAll(postRequestedFor(urlPathMatching(".*/sendMessage")))).isEmpty();
    }
}
