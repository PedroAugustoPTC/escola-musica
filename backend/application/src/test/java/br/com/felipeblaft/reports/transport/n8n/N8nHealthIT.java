package br.com.felipeblaft.reports.transport.n8n;

import static br.com.felipeblaft.reports.support.N8nTestSupport.N8N_URL;
import static br.com.felipeblaft.reports.support.N8nTestSupport.awaitHealthy;
import static br.com.felipeblaft.reports.support.N8nTestSupport.get;
import static org.assertj.core.api.Assertions.assertThat;

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
}
