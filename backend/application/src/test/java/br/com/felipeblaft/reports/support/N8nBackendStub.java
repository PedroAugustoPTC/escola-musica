package br.com.felipeblaft.reports.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * O backend simulado dos testes de contrato dos workflows (feature 004): um WireMock na porta fixa
 * {@link N8nTestSupport#BACKEND_STUB_PORT} que faz o papel da API interna do canal Telegram. Grava
 * o que o n8n enviou, confere o token de serviço de cada chamada e permite programar as respostas.
 */
public final class N8nBackendStub implements AutoCloseable {

    public static final String BASE = "/internal/v1/channel/telegram";
    public static final String SERVICE_TOKEN = "current-service-token-test";

    private final WireMockServer server =
            new WireMockServer(wireMockConfig().port(N8nTestSupport.BACKEND_STUB_PORT));

    public N8nBackendStub() {
        server.start();
        reset();
    }

    /** Apaga o que foi gravado e volta ao padrão: update sem ações e toda ação em PENDING. */
    public void reset() {
        server.resetAll();
        updateReturns("{\"version\":\"v1\",\"updateId\":1,\"batchId\":null,\"actions\":[]}");
        server.stubFor(
                get(urlPathMatching(BASE + "/actions/.*"))
                        .atPriority(10)
                        .willReturn(stateResponse("u1-1", "PENDING")));
        server.stubFor(
                post(urlEqualTo(BASE + "/confirmations"))
                        .atPriority(10)
                        .willReturn(json(200, "{\"version\":\"v1\",\"results\":[]}")));
    }

    // ---------------------------------------------------------------- programação

    public void updateReturns(String updateResponseJson) {
        server.stubFor(
                post(urlEqualTo(BASE + "/update"))
                        .atPriority(1)
                        .willReturn(json(200, updateResponseJson)));
    }

    public void updateStatus(int status) {
        server.stubFor(
                post(urlEqualTo(BASE + "/update"))
                        .atPriority(1)
                        .willReturn(aResponse().withStatus(status)));
    }

    /** Estado devolvido por {@code GET /actions/{id}}. */
    public void actionState(String actionId, String state) {
        server.stubFor(
                get(urlPathEqualTo(BASE + "/actions/" + actionId))
                        .atPriority(1)
                        .willReturn(stateResponse(actionId, state)));
    }

    public void actionStatus(String actionId, int status) {
        server.stubFor(
                get(urlPathEqualTo(BASE + "/actions/" + actionId))
                        .atPriority(1)
                        .willReturn(aResponse().withStatus(status)));
    }

    public void documentReturns(String documentId, byte[] pdf) {
        server.stubFor(
                get(urlPathEqualTo(BASE + "/documents/" + documentId))
                        .atPriority(1)
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/pdf")
                                        .withBody(pdf)));
    }

    public void documentStatus(String documentId, int status) {
        server.stubFor(
                get(urlPathEqualTo(BASE + "/documents/" + documentId))
                        .atPriority(1)
                        .willReturn(aResponse().withStatus(status)));
    }

    // ---------------------------------------------------------------- o que o n8n enviou

    public List<LoggedRequest> updates() {
        return server.findAll(postRequestedFor(urlEqualTo(BASE + "/update")));
    }

    public List<LoggedRequest> stateQueries() {
        return server.findAll(getRequestedFor(urlPathMatching(BASE + "/actions/.*")));
    }

    public List<LoggedRequest> documentFetches(String documentId) {
        return server.findAll(getRequestedFor(urlPathEqualTo(BASE + "/documents/" + documentId)));
    }

    public List<LoggedRequest> confirmations() {
        return server.findAll(postRequestedFor(urlEqualTo(BASE + "/confirmations")));
    }

    public List<LoggedRequest> all() {
        return server.findAll(anyRequestedFor(anyUrl()));
    }

    /** Chamadas do n8n sem o token de serviço esperado; precisa ficar vazio em todo teste. */
    public List<String> headerViolations() {
        List<String> violations = new ArrayList<>();
        for (LoggedRequest request : all()) {
            if (!SERVICE_TOKEN.equals(request.getHeader("X-N8N-Service-Token"))) {
                violations.add(
                        request.getMethod()
                                + " "
                                + request.getUrl()
                                + ": token de serviço ausente ou errado");
            }
        }
        return violations;
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

    private static ResponseDefinitionBuilder stateResponse(String actionId, String state) {
        return json(
                200,
                "{\"version\":\"v1\",\"actionId\":\""
                        + actionId
                        + "\",\"state\":\""
                        + state
                        + "\"}");
    }
}
