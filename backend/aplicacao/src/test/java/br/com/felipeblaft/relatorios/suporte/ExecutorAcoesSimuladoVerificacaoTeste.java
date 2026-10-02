package br.com.felipeblaft.relatorios.suporte;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.suporte.ExecutorAcoesSimuladoTeste.ClienteInterno;
import br.com.felipeblaft.relatorios.suporte.ExecutorAcoesSimuladoTeste.Resultado;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * O executor simulado é a especificação executável do workflow do orquestrador: este teste garante
 * que ele segue as obrigações do contrato (§4) antes de servir de régua para o backend (FR-003a).
 */
class ExecutorAcoesSimuladoVerificacaoTeste {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private WireMockServer apiBots;
    private final Map<String, ClienteInterno.Resposta> respostasGet = new HashMap<>();
    private final List<String> chamadasGet = new ArrayList<>();
    private final List<String> confirmacoes = new ArrayList<>();
    private ExecutorAcoesSimuladoTeste executor;

    @BeforeEach
    void preparar() {
        apiBots = new WireMockServer(wireMockConfig().dynamicPort());
        apiBots.start();
        apiBots.stubFor(
                post(urlPathEqualTo("/botT/sendMessage"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"ok\":true,\"result\":{\"message_id\":77}}")));
        executor =
                new ExecutorAcoesSimuladoTeste(
                        new ClienteInterno() {
                            @Override
                            public Resposta get(String caminho) {
                                chamadasGet.add(caminho);
                                return respostasGet.getOrDefault(
                                        caminho, new Resposta(404, new byte[0]));
                            }

                            @Override
                            public Resposta post(String caminho, String corpoJson) {
                                confirmacoes.add(corpoJson);
                                return new Resposta(
                                        200,
                                        "{\"versao\":\"v1\",\"aceitas\":1,\"ignoradas\":[]}"
                                                .getBytes(StandardCharsets.UTF_8));
                            }
                        },
                        apiBots.baseUrl() + "/botT");
    }

    @AfterEach
    void encerrar() {
        apiBots.stop();
    }

    private static JsonNode acoes(String json) {
        return JSON.readTree(json);
    }

    private void erroDoTelegram(String metodo, int status, String descricao) {
        apiBots.stubFor(
                post(urlPathEqualTo("/botT/" + metodo))
                        .willReturn(
                                aResponse()
                                        .withStatus(status)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"ok\":false,\"description\":\""
                                                        + descricao
                                                        + "\"}")));
    }

    @Test
    void tipoDesconhecidoEPuladoConfirmadoComoNaoSuportadoECampoExtraEIgnorado() throws Exception {
        executor.executar(
                acoes(
                        """
                        [{"acaoId":"u1-0","tipo":"FUTURA","campoNovo":1},
                         {"acaoId":"u1-1","tipo":"MENSAGEM","chatId":5,"texto":"oi","campoExtra":"x"}]
                        """));

        assertThat(executor.informados)
                .containsExactly(
                        new Resultado("u1-0", "FALHOU", "ACAO_NAO_SUPORTADA", null),
                        new Resultado("u1-1", "EXECUTADA", null, 77L));
        apiBots.verify(
                1,
                postRequestedFor(urlPathEqualTo("/botT/sendMessage"))
                        .withRequestBody(
                                equalToJson("{\"chat_id\":5,\"text\":\"oi\"}", true, true)));
        assertThat(confirmacoes).hasSize(1);
    }

    @Test
    void naoParaNaPrimeiraFalha() throws Exception {
        erroDoTelegram("editMessageText", 403, "Forbidden: bot was blocked by the user");
        executor.executar(
                acoes(
                        """
                        [{"acaoId":"u1-0","tipo":"EDICAO","chatId":5,"mensagemId":9,"texto":"a"},
                         {"acaoId":"u1-1","tipo":"MENSAGEM","chatId":5,"texto":"b"}]
                        """));
        assertThat(executor.informados)
                .containsExactly(
                        new Resultado("u1-0", "FALHOU", "CHAT_INDISPONIVEL", null),
                        new Resultado("u1-1", "EXECUTADA", null, 77L));
    }

    @Test
    void classificaOsErrosDoTelegramConformeATabelaDoContrato() {
        assertThat(
                        ExecutorAcoesSimuladoTeste.classificar(
                                400, "Bad Request: message is not modified"))
                .isNull();
        assertThat(
                        ExecutorAcoesSimuladoTeste.classificar(
                                400, "Bad Request: message to edit not found"))
                .isEqualTo("MENSAGEM_NAO_EDITAVEL");
        assertThat(
                        ExecutorAcoesSimuladoTeste.classificar(
                                400, "Bad Request: message can't be edited"))
                .isEqualTo("MENSAGEM_NAO_EDITAVEL");
        assertThat(ExecutorAcoesSimuladoTeste.classificar(403, "Forbidden"))
                .isEqualTo("CHAT_INDISPONIVEL");
        assertThat(ExecutorAcoesSimuladoTeste.classificar(400, "Bad Request: chat not found"))
                .isEqualTo("CHAT_INDISPONIVEL");
        assertThat(ExecutorAcoesSimuladoTeste.classificar(429, "Too Many Requests"))
                .isEqualTo("LIMITE_DE_TAXA");
        assertThat(ExecutorAcoesSimuladoTeste.classificar(500, null)).isEqualTo("ERRO_TELEGRAM");
        assertThat(ExecutorAcoesSimuladoTeste.classificar(400, "algo inesperado"))
                .isEqualTo("ERRO_TELEGRAM");
    }

    @Test
    void mensagemNaoModificadaNumaEdicaoEConfirmadaComoExecutada() throws Exception {
        erroDoTelegram("editMessageText", 400, "Bad Request: message is not modified");
        executor.executar(
                acoes(
                        "[{\"acaoId\":\"u1-0\",\"tipo\":\"EDICAO\",\"chatId\":5,\"mensagemId\":9,\"texto\":\"a\"}]"));
        assertThat(executor.informados)
                .containsExactly(new Resultado("u1-0", "EXECUTADA", null, 9L));
    }

    @Test
    void edicaoDeMensagemInexistenteViraMensagemNaoEditavelELimiteDeTaxaESeparado()
            throws Exception {
        erroDoTelegram("editMessageText", 400, "Bad Request: message to edit not found");
        erroDoTelegram("sendMessage", 429, "Too Many Requests: retry after 5");
        executor.executar(
                acoes(
                        """
                        [{"acaoId":"u1-0","tipo":"EDICAO","chatId":5,"mensagemId":9,"texto":"a"},
                         {"acaoId":"u1-1","tipo":"MENSAGEM","chatId":5,"texto":"b"}]
                        """));
        assertThat(executor.informados)
                .containsExactly(
                        new Resultado("u1-0", "FALHOU", "MENSAGEM_NAO_EDITAVEL", null),
                        new Resultado("u1-1", "FALHOU", "LIMITE_DE_TAXA", null));
    }

    @Test
    void acaoJaTratadaEPuladaPelaConsultaAoBackend() throws Exception {
        respostasGet.put(
                "/interno/v1/canal/telegram/acoes/u1-0",
                new ClienteInterno.Resposta(
                        200,
                        "{\"versao\":\"v1\",\"acaoId\":\"u1-0\",\"estado\":\"EXECUTADA\"}"
                                .getBytes(StandardCharsets.UTF_8)));
        respostasGet.put(
                "/interno/v1/canal/telegram/acoes/u1-1",
                new ClienteInterno.Resposta(
                        200,
                        "{\"versao\":\"v1\",\"acaoId\":\"u1-1\",\"estado\":\"ENTREGUE\"}"
                                .getBytes(StandardCharsets.UTF_8)));
        executor.executar(
                acoes(
                        """
                        [{"acaoId":"u1-0","tipo":"MENSAGEM","chatId":5,"texto":"a"},
                         {"acaoId":"u1-1","tipo":"MENSAGEM","chatId":5,"texto":"b"}]
                        """));
        apiBots.verify(1, postRequestedFor(urlPathEqualTo("/botT/sendMessage")));
        assertThat(executor.informados).extracting(Resultado::acaoId).containsExactly("u1-1");
    }

    @Test
    void documentoIndisponivelFazUmaUnicaTentativaEConfirmaFalha() throws Exception {
        executor.executar(
                acoes(
                        "[{\"acaoId\":\"u1-0\",\"tipo\":\"DOCUMENTO\",\"chatId\":5,"
                                + "\"documentoId\":\""
                                + "A".repeat(43)
                                + "\",\"nomeArquivo\":\"r.pdf\","
                                + "\"tipoConteudo\":\"application/pdf\"}]"));
        assertThat(executor.informados)
                .containsExactly(new Resultado("u1-0", "FALHOU", "DOCUMENTO_INDISPONIVEL", null));
        assertThat(chamadasGet.stream().filter(c -> c.contains("/documentos/")).count())
                .isEqualTo(1);
        apiBots.verify(0, postRequestedFor(urlPathEqualTo("/botT/sendDocument")));
    }

    @Test
    void documentoDisponivelVaiComoMultipartComOsBytes() throws Exception {
        apiBots.stubFor(
                post(urlPathEqualTo("/botT/sendDocument"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"ok\":true,\"result\":{\"message_id\":3}}")));
        respostasGet.put(
                "/interno/v1/canal/telegram/documentos/" + "A".repeat(43),
                new ClienteInterno.Resposta(
                        200, "%PDF-1.7 conteudo".getBytes(StandardCharsets.UTF_8)));
        executor.executar(
                acoes(
                        "[{\"acaoId\":\"u1-0\",\"tipo\":\"DOCUMENTO\",\"chatId\":5,"
                                + "\"documentoId\":\""
                                + "A".repeat(43)
                                + "\",\"nomeArquivo\":\"r.pdf\","
                                + "\"tipoConteudo\":\"application/pdf\",\"legenda\":\"Segue\"}]"));
        assertThat(executor.informados)
                .extracting(Resultado::situacao)
                .containsExactly("EXECUTADA");
        var enviado =
                apiBots.findAll(postRequestedFor(urlPathEqualTo("/botT/sendDocument"))).get(0);
        assertThat(enviado.getBodyAsString())
                .contains("%PDF-1.7 conteudo", "filename=\"r.pdf\"", "name=\"caption\"", "Segue");
    }

    @Test
    void etapaSemIdEnviaMensagemNovaEEtapaComIdEditaEAmbasInformamOMensagemId() throws Exception {
        apiBots.stubFor(
                post(urlPathEqualTo("/botT/editMessageText"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"ok\":true,\"result\":true}")));
        executor.executar(
                acoes(
                        """
                        [{"acaoId":"p:e:0","tipo":"MENSAGEM_ETAPA","chatId":5,"texto":"a","trabalhoId":"t"},
                         {"acaoId":"p:e:1","tipo":"MENSAGEM_ETAPA","chatId":5,"texto":"b","trabalhoId":"t","editarMensagemId":55}]
                        """));
        apiBots.verify(1, postRequestedFor(urlPathEqualTo("/botT/sendMessage")));
        apiBots.verify(1, postRequestedFor(urlPathEqualTo("/botT/editMessageText")));
        assertThat(executor.informados)
                .containsExactly(
                        new Resultado("p:e:0", "EXECUTADA", null, 77L),
                        new Resultado("p:e:1", "EXECUTADA", null, 55L));
    }

    @Test
    void tecladoViraReplyMarkupERespostaDeCallbackVaiParaAnswerCallbackQuery() throws Exception {
        apiBots.stubFor(
                post(urlPathEqualTo("/botT/answerCallbackQuery"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"ok\":true,\"result\":true}")));
        executor.executar(
                acoes(
                        """
                        [{"acaoId":"u1-0","tipo":"RESPOSTA_CALLBACK","callbackId":"cb1"},
                         {"acaoId":"u1-1","tipo":"MENSAGEM","chatId":5,"texto":"x",
                          "teclado":{"linhas":[[{"rotulo":"Sim","dados":"v1:sim:"}]]}}]
                        """));
        apiBots.verify(
                postRequestedFor(urlPathEqualTo("/botT/answerCallbackQuery"))
                        .withRequestBody(equalToJson("{\"callback_query_id\":\"cb1\"}")));
        apiBots.verify(
                postRequestedFor(urlPathEqualTo("/botT/sendMessage"))
                        .withRequestBody(
                                equalToJson(
                                        "{\"reply_markup\":{\"inline_keyboard\":[[{\"text\":\"Sim\",\"callback_data\":\"v1:sim:\"}]]}}",
                                        true,
                                        true)));
    }

    @Test
    void semAcoesNaoConfirmaNada() throws Exception {
        executor.executar(acoes("[]"));
        assertThat(confirmacoes).isEmpty();
    }
}
