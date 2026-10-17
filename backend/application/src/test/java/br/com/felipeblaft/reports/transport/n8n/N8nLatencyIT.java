package br.com.felipeblaft.reports.transport.n8n;

import static br.com.felipeblaft.reports.support.N8nTestSupport.N8N_URL;
import static br.com.felipeblaft.reports.support.N8nTestSupport.awaitHealthy;
import static br.com.felipeblaft.reports.support.N8nTestSupport.post;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.N8nBackendStub;
import br.com.felipeblaft.reports.support.N8nBotApiStub;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Latência do transporte (feature 004, SC-003): em 100 updates, 95% executam a primeira ação em até
 * 2 s a partir da chegada ao n8n. Mede-se pelo horário do {@code N8nBotApiStub}; é medida em
 * máquina de CI, não garantia de produção.
 */
@Tag("n8n")
class N8nLatencyIT {

    private static final int UPDATES = 100;

    @Test
    @DisplayName(
            "SC-003: p95 do intervalo entre a chegada ao n8n e a primeira chamada à API de bots é de até 2 s")
    void ninetyFifthPercentileIsUnderTwoSeconds() {
        awaitHealthy(Duration.ofSeconds(180));
        try (N8nBackendStub backend = new N8nBackendStub();
                N8nBotApiStub botApi = new N8nBotApiStub()) {
            backend.updateReturns(
                    "{\"version\":\"v1\",\"updateId\":1,\"batchId\":\"u1\",\"actions\":[{\"actionId\":\"u1-1\",\"type\":\"MESSAGE\",\"chatId\":7001,\"text\":\"Speed\"}]}");
            List<Long> latencies = new ArrayList<>();
            for (int i = 0; i < UPDATES; i++) {
                botApi.reset();
                long arrival = System.currentTimeMillis();
                post(
                        N8N_URL + "/webhook/telegram-update",
                        Map.of("X-Telegram-Bot-Api-Secret-Token", "secret-webhook-test"),
                        "application/json",
                        "{\"update_id\":1}".getBytes(StandardCharsets.UTF_8));
                long firstCall = botApi.calls().getFirst().getLoggedDate().getTime();
                latencies.add(firstCall - arrival);
            }
            Collections.sort(latencies);
            long p95 = latencies.get((int) Math.ceil(UPDATES * 0.95) - 1);

            System.out.println(
                    "p95 da latência do n8n: "
                            + p95
                            + " ms (máximo "
                            + latencies.getLast()
                            + " ms)");
            assertThat(p95).isLessThanOrEqualTo(2_000L);
        }
    }
}
