package br.com.felipeblaft.reports.transport.n8n.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.OrchestratorDeliveryBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Idempotência ponta a ponta pelo n8n real (feature 004, FR-028a, SC-011): os mesmos cenários rodam
 * com cada update entregue uma vez e com cada update e cada lote proativo entregues duas vezes; a
 * transcrição do chat (todas as chamadas à API de bots, na ordem) precisa ser
 * <strong>idêntica</strong>, sem nenhuma mensagem a mais. As transcrições ficam em {@code
 * target/n8n-transcripts}.
 */
class N8nIdempotencyIT extends OrchestratorDeliveryBase {

    @Test
    @DisplayName("conversa: a transcrição é a mesma com update entregue duas vezes")
    void conversationTranscriptDoesNotChangeWithDuplicateUpdates() throws Exception {
        Assumptions.assumeTrue("n8n".equals(TRANSPORT));
        boolean original = n8nDuplicates;
        try {
            n8nDuplicates = false;
            List<String> once = conversation();
            n8nDuplicates = true;
            resetScenario();
            List<String> twice = conversation();

            save("conversation", once, twice);
            assertThat(once).isNotEmpty();
            assertThat(twice).isEqualTo(once);
        } finally {
            n8nDuplicates = original;
        }
    }

    @Test
    @DisplayName("aviso proativo: a transcrição é a mesma com o lote entregue duas vezes")
    void proactiveTranscriptDoesNotChangeWithDuplicateBatches() throws Exception {
        Assumptions.assumeTrue("n8n".equals(TRANSPORT));
        boolean original = n8nDuplicates;
        try {
            n8nDuplicates = false;
            List<String> once = proactive();
            n8nDuplicates = true;
            resetScenario();
            List<String> twice = proactive();

            save("proactive", once, twice);
            assertThat(once).isNotEmpty();
            assertThat(twice).isEqualTo(once);
        } finally {
            n8nDuplicates = original;
        }
    }

    // ---------------------------------------------------------------- cenários

    private List<String> conversation() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        sendText(USER_CLARA, "menu");
        sendCallback(USER_CLARA, "v1:home:");
        sendText(USER_CLARA, "início");
        return transcript();
    }

    private List<String> proactive() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        drain(5);
        return transcript();
    }

    /**
     * Todas as chamadas à API de bots, na ordem: método e corpo (só o método, se for multipart).
     */
    private static List<String> transcript() {
        List<LoggedRequest> requests =
                new ArrayList<>(
                        API_BOTS.getAllServeEvents().stream().map(e -> e.getRequest()).toList());
        requests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));
        List<String> lines = new ArrayList<>();
        for (LoggedRequest request : requests) {
            String method = request.getUrl().substring(request.getUrl().lastIndexOf('/') + 1);
            boolean json =
                    request.getHeader("Content-Type") != null
                            && request.getHeader("Content-Type").startsWith("application/json");
            // o id do toque e os UUIDs dos relatórios mudam entre as rodadas; o resto da transcrição não
            String body =
                    json
                            ? request.getBodyAsString()
                                    .replaceAll(
                                            "\"callback_query_id\":\"cb[0-9]+\"",
                                            "\"callback_query_id\":\"cb\"")
                                    .replaceAll(
                                            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                                            "<uuid>")
                            : "[multipart]";
            lines.add(method + " " + body);
        }
        return lines;
    }

    private static void save(String scenario, List<String> once, List<String> twice)
            throws Exception {
        Path directory = Path.of("target", "n8n-transcripts");
        Files.createDirectories(directory);
        Files.write(directory.resolve(scenario + "-once.txt"), once);
        Files.write(directory.resolve(scenario + "-twice.txt"), twice);
    }
}
