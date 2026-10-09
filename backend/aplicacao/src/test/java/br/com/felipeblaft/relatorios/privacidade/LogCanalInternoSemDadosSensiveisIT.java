package br.com.felipeblaft.relatorios.privacidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso;
import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso.NovaAcao;
import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.entrada.telegram.InterpretadorConversaTelegram;
import br.com.felipeblaft.relatorios.suporte.CenarioIT;
import br.com.felipeblaft.relatorios.suporte.EntregaOrquestradorBase;
import br.com.felipeblaft.relatorios.suporte.NormalizacaoAudioTeste;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * V16, FR-029, SC-006: com os logs em DEBUG, a conversa pela API interna, a entrega, a retentativa,
 * a falha definitiva, a compensação e as requisições recusadas não registram CPF, nome de aluno,
 * texto de relatório, transcrição nem payload, e nenhum valor sensível semeado aparece: token de
 * serviço (em formato que o mascarador não reconhece), segredo do webhook do Telegram, token e URL
 * do orquestrador (com segredo no caminho) e um {@code documentoId} real. Também os corpos de
 * {@code 401/404}.
 */
class LogCanalInternoSemDadosSensiveisIT extends EntregaOrquestradorBase {

    private static final String RESPOSTA = "Resposta sigilosa sobre a rotina de estudo da semana";
    private static final String BASE = "/interno/v1/canal/telegram";

    @Autowired GravarAcoesCanalCasoDeUso gravar;

    @MockitoSpyBean InterpretadorConversaTelegram interpretador;

    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();
    private Logger raiz;
    private Level nivelRaiz;
    private Logger aplicacao;
    private Level nivelAplicacao;

    @BeforeEach
    void capturarLogs() {
        raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        aplicacao = (Logger) LoggerFactory.getLogger("br.com.felipeblaft");
        nivelRaiz = raiz.getLevel();
        nivelAplicacao = aplicacao.getLevel();
        raiz.setLevel(Level.INFO);
        aplicacao.setLevel(Level.DEBUG);
        eventos.start();
        raiz.addAppender(eventos);
    }

    @AfterEach
    void restaurarLogs() {
        raiz.detachAppender(eventos);
        eventos.stop();
        raiz.setLevel(nivelRaiz);
        aplicacao.setLevel(nivelAplicacao);
    }

    private String textoDosLogs() {
        StringBuilder texto = new StringBuilder();
        for (ILoggingEvent evento : List.copyOf(eventos.list)) {
            texto.append(evento.getFormattedMessage()).append('\n');
            evento.getMDCPropertyMap()
                    .forEach((k, v) -> texto.append(k).append('=').append(v).append('\n'));
            if (evento.getKeyValuePairs() != null) {
                evento.getKeyValuePairs()
                        .forEach(p -> texto.append(p.key).append('=').append(p.value).append('\n'));
            }
            if (evento.getThrowableProxy() != null) {
                texto.append(ThrowableProxyUtil.asString(evento.getThrowableProxy())).append('\n');
            }
        }
        return texto.toString();
    }

    private MvcResult atualizacao(String json, String token, String segredo) throws Exception {
        var requisicao =
                post(BASE + "/atualizacao").contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) {
            requisicao.header("X-N8N-Service-Token", token);
        }
        if (segredo != null) {
            requisicao.header("X-Telegram-Bot-Api-Secret-Token", segredo);
        }
        return mvc.perform(requisicao).andReturn();
    }

    private JsonNode pelaApiInterna(String json) throws Exception {
        MvcResult resultado = atualizacao(json, TOKEN_SERVICO, SEGREDO);
        JsonNode corpo = JSON.readTree(resultado.getResponse().getContentAsString());
        executorDoOrquestrador().executar(corpo.get("acoes"));
        return corpo.get("acoes");
    }

    @Test
    void excecaoDoInterpretadorComDadosDoAlunoNaoApareceNosLogsNemNaResposta() throws Exception {
        doThrow(
                        new IllegalStateException(
                                "Matteo Ferreira CPF 123.456.789-09 Czerny e a valsa",
                                new IllegalArgumentException("causa: Matteo " + RESPOSTA)))
                .when(interpretador)
                .interpretar(any());
        long update = proximoUpdate();

        MvcResult resultado =
                atualizacao(
                        mensagemJson(update, USUARIO_CLARA, "\"text\": \"oi\""),
                        TOKEN_SERVICO,
                        SEGREDO);

        String resposta = resultado.getResponse().getContentAsString();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(resposta).contains("Ocorreu um erro inesperado");
        String logs = textoDosLogs();
        assertThat(logs)
                .contains("telegram-erro-interpretacao updateId=" + update)
                .contains("IllegalStateException");
        for (String proibido :
                List.of("Matteo", "123.456.789-09", "Czerny", "valsa", RESPOSTA, "causa:")) {
            assertThat(logs).as("log sem " + proibido).doesNotContain(proibido);
            assertThat(resposta).as("resposta sem " + proibido).doesNotContain(proibido);
        }
        assertThat(
                        eventos.list.stream()
                                .filter(e -> e.getFormattedMessage().contains("erro-interpretacao"))
                                .allMatch(e -> e.getThrowableProxy() == null))
                .as("sem stack trace")
                .isTrue();
    }

    @Test
    void conversaEntregaRetentativaFalhaDefinitivaECompensacaoNaoVazamDados() throws Exception {
        List<MvcResult> recusadas = new java.util.ArrayList<>();

        // ---- requisições recusadas e ilegíveis
        for (MvcResult recusada :
                List.of(
                        atualizacao("{}", null, SEGREDO),
                        atualizacao("{}", "tok-teste-123", SEGREDO),
                        atualizacao("{}", TOKEN_SERVICO, "segredo-errado"),
                        atualizacao("{ dado do aluno Matteo nao e json", TOKEN_SERVICO, SEGREDO),
                        mvc.perform(
                                        get(BASE + "/documentos/" + "Z".repeat(43))
                                                .header("X-N8N-Service-Token", TOKEN_SERVICO))
                                .andReturn(),
                        mvc.perform(
                                        get(BASE + "/acoes/u0-0")
                                                .header("X-N8N-Service-Token", "tok-teste-123"))
                                .andReturn())) {
            recusadas.add(recusada);
        }

        // ---- conversa interna: vínculo, áudio, perguntas, respostas, conferência e PDF
        enviarTexto(
                USUARIO_CLARA,
                CenarioIT.CODIGO_CLARA); // vínculo pelo webhook (já coberto no outro IT)
        prepararArquivoTelegram("aula", NormalizacaoAudioTeste.AUDIO_COM_FALA);
        pelaApiInterna(mensagemJson(proximoUpdate(), USUARIO_CLARA, "\"text\": \"Matteo\""));
        pelaApiInterna(
                mensagemJson(
                        proximoUpdate(),
                        USUARIO_CLARA,
                        "\"voice\": {\"file_id\": \"aula\", \"file_unique_id\": \"u-aula\", \"mime_type\": \"audio/ogg\", \"file_size\": 1000, \"duration\": 30}"));
        pelaApiInterna(
                """
                {"update_id": %d, "callback_query": {"id": "cb1", "data": "v1:confal:",
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                """
                        .formatted(proximoUpdate(), USUARIO_CLARA, USUARIO_CLARA));
        cenario.processarFila();
        drenar(10);
        pelaApiInterna(
                mensagemJson(proximoUpdate(), USUARIO_CLARA, "\"text\": \"" + RESPOSTA + "\""));
        cenario.processarFila();
        drenar(10);
        JsonNode pdf =
                pelaApiInterna(
                                """
                                {"update_id": %d, "callback_query": {"id": "cb2", "data": "v1:pdf:",
                                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                                """
                                        .formatted(proximoUpdate(), USUARIO_CLARA, USUARIO_CLARA))
                        .get(1);
        String documentoId = pdf.path("documentoId").asString();
        assertThat(documentoId).hasSize(43);

        // ---- entrega com retentativa, compensação e falha definitiva
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
                                        + ",\"mensagemId\":9,\"texto\":\"Texto sigiloso da edição\",\"teclado\":{\"linhas\":[]}}",
                                "{\"tipo\":\"MENSAGEM\",\"chatId\":"
                                        + USUARIO_CLARA
                                        + ",\"texto\":\"Texto sigiloso da edição\"}")));
        responder(503);
        tarefa.despachar();
        relogio.avancar(Duration.ofMinutes(2));
        aceitar();
        API_BOTS.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(
                                com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching(
                                        ".*/editMessageText"))
                        .willReturn(
                                com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                                        .withStatus(400)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"ok\":false,\"description\":\"Bad Request: message to edit not found\"}")));
        drenar(6);
        gravar.executar(
                "p:edicao:2",
                OrigemAcaoCanal.PROATIVA,
                null,
                List.of(
                        new NovaAcao(
                                "p:edicao:2:0",
                                TipoAcaoCanal.MENSAGEM,
                                USUARIO_CLARA,
                                clara.id(),
                                null,
                                "{\"tipo\":\"MENSAGEM\",\"chatId\":"
                                        + USUARIO_CLARA
                                        + ",\"texto\":\"Texto sigiloso que vai expirar\"}",
                                null)));
        responder(500);
        relogio.avancar(Duration.ofHours(25));
        tarefa.despachar();

        // ---- os logs de tudo isso
        String logs = textoDosLogs();
        assertThat(eventos.list).as("a captura de logs funcionou").isNotEmpty();
        assertThat(logs).contains("canal-acao-falha-definitiva acaoId=p:edicao:2:0");
        Map<String, String> proibidos =
                Map.ofEntries(
                        Map.entry("nome de aluno", "Matteo"),
                        Map.entry("nome de aluno (Helena)", "Helena"),
                        Map.entry("código de acesso", CenarioIT.CODIGO_CLARA),
                        Map.entry("token do bot", TOKEN),
                        Map.entry("token de serviço atual", TOKEN_SERVICO),
                        Map.entry("token de serviço próximo", "token-servico-proximo-teste"),
                        Map.entry("token de serviço errado semeado", "tok-teste-123"),
                        Map.entry("segredo do webhook", SEGREDO),
                        Map.entry("segredo do webhook errado", "segredo-errado"),
                        Map.entry("token do orquestrador", "segredo-do-orquestrador"),
                        Map.entry("segredo no caminho da URL", "segredo-no-caminho-123"),
                        Map.entry("host e porta da URL", "localhost:" + ORQUESTRADOR.port()),
                        Map.entry("documentoId", documentoId),
                        Map.entry("resposta do professor", RESPOSTA),
                        Map.entry("texto de ação", "Texto sigiloso"),
                        Map.entry("texto de conferência", "Confira o relatório"),
                        Map.entry("conteúdo do relatório", "Czerny"));
        proibidos.forEach(
                (descricao, valor) -> assertThat(logs).as(descricao).doesNotContain(valor));
        // corpos de 401/404 são vazios; o do update ilegível (200) não ecoa o que foi enviado
        for (MvcResult recusada : recusadas) {
            int status = recusada.getResponse().getStatus();
            String corpo = recusada.getResponse().getContentAsString();
            if (status == 401 || status == 404) {
                assertThat(corpo).as("corpo do status %d", status).isEmpty();
            } else {
                assertThat(corpo).doesNotContain("Matteo", "dado do aluno");
            }
        }
        // a URL do orquestrador (com segredo no caminho) nunca aparece em log, nem como parte de
        // exceção
        assertThat(logs).doesNotContain("/webhook/");
    }
}
