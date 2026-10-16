package br.com.felipeblaft.reports.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A API de bots simulada dos testes de contrato dos workflows (feature 004): um WireMock na porta
 * fixa {@link N8nTestSupport#BOT_API_STUB_PORT}. Registra as chamadas do n8n na ordem em que
 * chegaram (método, corpo e horário) e permite programar o erro do Telegram de cada método.
 */
public final class N8nBotApiStub implements AutoCloseable {

    public static final String TOKEN = "123456:TEST";

    private final WireMockServer server =
            new WireMockServer(wireMockConfig().port(N8nTestSupport.BOT_API_STUB_PORT));

    public N8nBotApiStub() {
        server.start();
        reset();
    }

    public void reset() {
        server.resetAll();
        server.stubFor(
                any(urlPathMatching("/bot.*/.*"))
                        .atPriority(10)
                        .willReturn(json(200, "{\"ok\":true,\"result\":{\"message_id\":4242}}")));
    }

    /** Programa o erro do Telegram para um método (por exemplo {@code editMessageText}). */
    public void failMethod(String method, int status, String description) {
        server.stubFor(
                any(urlPathMatching("/bot.*/" + method))
                        .atPriority(1)
                        .willReturn(
                                json(
                                        status,
                                        "{\"ok\":false,\"error_code\":"
                                                + status
                                                + ",\"description\":\""
                                                + description
                                                + "\"}")));
    }

    /** Chamadas na ordem de chegada. */
    public List<LoggedRequest> calls() {
        List<LoggedRequest> found = new ArrayList<>(server.findAll(anyRequestedFor(anyUrl())));
        found.sort(Comparator.comparing(LoggedRequest::getLoggedDate));
        return found;
    }

    /** Nomes dos métodos chamados, na ordem. */
    public List<String> methods() {
        return calls().stream()
                .map(request -> request.getUrl().substring(request.getUrl().lastIndexOf('/') + 1))
                .toList();
    }

    public List<LoggedRequest> callsOf(String method) {
        return calls().stream().filter(request -> request.getUrl().endsWith("/" + method)).toList();
    }

    @Override
    public void close() {
        server.stop();
    }

    private static ResponseDefinitionBuilder json(int status, String body) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
