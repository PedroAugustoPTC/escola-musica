package br.com.felipeblaft.reports.transport.n8n;

import static br.com.felipeblaft.reports.support.N8nTestSupport.WEB_URL;
import static br.com.felipeblaft.reports.support.N8nTestSupport.awaitUrl;
import static br.com.felipeblaft.reports.support.N8nTestSupport.insecureGet;
import static br.com.felipeblaft.reports.support.N8nTestSupport.insecurePost;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.N8nTestSupport.Reply;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * O n8n não é alcançável pela entrada pública (feature 004, R-02, FR-002): o Caddy real, com o
 * Caddyfile do repositório, responde com o SPA ou 404 para os caminhos do n8n, nunca com a resposta
 * dele.
 */
@Tag("n8n")
class N8nExposureIT {

    @BeforeAll
    static void waitForTheEntryPoint() {
        awaitUrl(WEB_URL + "/", true, Duration.ofSeconds(90));
    }

    @Test
    @DisplayName("POST /webhook/telegram-update não chega ao n8n")
    void updateWebhookIsNotRouted() {
        Reply reply = insecurePost(WEB_URL + "/webhook/telegram-update", "application/json", "{}");

        assertNotFromN8n(reply);
    }

    @Test
    @DisplayName("GET /webhook/transport-health/live não chega ao n8n")
    void healthWebhookIsNotRouted() {
        assertNotFromN8n(insecureGet(WEB_URL + "/webhook/transport-health/live"));
    }

    @Test
    @DisplayName("GET /healthz não chega ao n8n")
    void healthzIsNotRouted() {
        assertNotFromN8n(insecureGet(WEB_URL + "/healthz"));
    }

    @Test
    @DisplayName("GET /rest/settings não chega ao n8n")
    void restIsNotRouted() {
        assertNotFromN8n(insecureGet(WEB_URL + "/rest/settings"));
    }

    private static void assertNotFromN8n(Reply reply) {
        assertThat(reply.status()).isIn(200, 404, 405);
        // o n8n responde JSON; o Caddy devolve o SPA (HTML) ou corpo vazio
        assertThat(reply.contentType()).doesNotContain("json");
        assertThat(reply.body().stripLeading()).doesNotStartWith("{");
    }
}
