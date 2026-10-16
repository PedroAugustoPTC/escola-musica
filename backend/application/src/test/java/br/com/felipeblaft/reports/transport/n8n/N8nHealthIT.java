package br.com.felipeblaft.reports.transport.n8n;

import static br.com.felipeblaft.reports.support.N8nTestSupport.N8N_URL;
import static br.com.felipeblaft.reports.support.N8nTestSupport.awaitHealthy;
import static br.com.felipeblaft.reports.support.N8nTestSupport.get;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.N8nBackendStub;
import br.com.felipeblaft.reports.support.N8nBotApiStub;
import br.com.felipeblaft.reports.support.N8nTestSupport.Reply;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Saúde do transporte (feature 004, R-10, contrato §5). Esta classe cobre o {@code live}; o {@code
 * deep} entra com o backend simulado na fase seguinte.
 */
@Tag("n8n")
class N8nHealthIT {

    @Test
    @DisplayName("live responde 200 sem chamar o backend, que nem está no ar")
    void liveAnswersWithoutTheBackend() {
        awaitHealthy(Duration.ofSeconds(180));

        // nenhum servidor escuta em BACKEND_INTERNAL_URL neste teste: a saúde do n8n não depende
        // dele (R-10)
        Reply live = get(N8N_URL + "/webhook/transport-health/live");

        assertThat(live.status()).isEqualTo(200);
        assertThat(live.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    @DisplayName("o corpo do live não traz token, URL nem versão")
    void liveBodyHasNoSecretsOrUrls() {
        awaitHealthy(Duration.ofSeconds(180));

        Reply live = get(N8N_URL + "/webhook/transport-health/live");

        assertThat(live.body())
                .doesNotContain("http")
                .doesNotContain("token")
                .doesNotContain("2.43");
    }

    // ---------------------------------------------------------------- deep (N14)

    private static Reply deep() {
        awaitHealthy(Duration.ofSeconds(180));
        return get(N8N_URL + "/webhook/transport-health/deep");
    }

    @Test
    @DisplayName("N14: deep com backend e API de bots saudáveis responde 200")
    void deepIsUpWhenEverythingIsUp() {
        try (N8nBackendStub backend = new N8nBackendStub();
                N8nBotApiStub botApi = new N8nBotApiStub()) {
            backend.actionStatus("u0-0", 404);

            Reply deep = deep();

            assertThat(deep.status()).isEqualTo(200);
            assertThat(deep.body())
                    .isEqualTo("{\"status\":\"UP\",\"backend\":\"ok\",\"botApi\":\"ok\"}");
            assertThat(backend.headerViolations()).isEmpty();
        }
    }

    @Test
    @DisplayName("N14: backend com 401 aparece como unauthorized")
    void deepReportsUnauthorizedBackend() {
        try (N8nBackendStub backend = new N8nBackendStub();
                N8nBotApiStub botApi = new N8nBotApiStub()) {
            backend.actionStatus("u0-0", 401);

            Reply deep = deep();

            assertThat(deep.status()).isEqualTo(503);
            assertThat(deep.body())
                    .contains("\"backend\":\"unauthorized\"")
                    .contains("\"botApi\":\"ok\"");
        }
    }

    @Test
    @DisplayName("N14: getMe com falha aparece como botApi down")
    void deepReportsBotApiDown() {
        try (N8nBackendStub backend = new N8nBackendStub();
                N8nBotApiStub botApi = new N8nBotApiStub()) {
            backend.actionStatus("u0-0", 404);
            botApi.failMethod("getMe", 401, "Unauthorized");

            Reply deep = deep();

            assertThat(deep.status()).isEqualTo(503);
            assertThat(deep.body()).contains("\"backend\":\"ok\"").contains("\"botApi\":\"down\"");
        }
    }

    @Test
    @DisplayName("N14: nada no ar aparece como down nos dois e o corpo nunca traz token nem URL")
    void deepReportsEverythingDownWithoutLeaking() {
        Reply deep = deep();

        assertThat(deep.status()).isEqualTo(503);
        assertThat(deep.body())
                .contains("\"backend\":\"down\"")
                .contains("\"botApi\":\"down\"")
                .doesNotContain("http")
                .doesNotContain("123456")
                .doesNotContain("current-service-token-test");
    }
}
