package br.com.felipeblaft.reports.transport.n8n;

import static br.com.felipeblaft.reports.support.N8nTestSupport.N8N_URL;
import static br.com.felipeblaft.reports.support.N8nTestSupport.awaitHealthy;
import static br.com.felipeblaft.reports.support.N8nTestSupport.isRunning;
import static br.com.felipeblaft.reports.support.N8nTestSupport.killN8n;
import static br.com.felipeblaft.reports.support.N8nTestSupport.post;
import static br.com.felipeblaft.reports.support.N8nTestSupport.restartN8n;
import static br.com.felipeblaft.reports.support.N8nTestSupport.startN8n;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.N8nBackendStub;
import br.com.felipeblaft.reports.support.N8nBotApiStub;
import br.com.felipeblaft.reports.support.N8nTestSupport.Reply;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Queda e reinício do n8n no meio do trabalho (feature 004, FR-028b, SC-006, contrato dos workflows
 * N13, N13b e N13c): ação já confirmada nunca repete, a única ação em voo pode repetir uma vez, o
 * reinício planejado volta a receber updates em até 60 s e o backend fora do ar vira {@code 503}. O
 * backend e a API de bots são simulados; o que o backend "sabe" depois da queda é programado a
 * partir do que o n8n de fato confirmou antes de cair.
 */
@Tag("n8n")
class N8nResilienceIT {

    private static final String WEBHOOK_SECRET = "secret-webhook-test";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static N8nBackendStub backend;
    private static N8nBotApiStub botApi;

    @BeforeAll
    static void startStubs() {
        awaitHealthy(Duration.ofSeconds(180));
        backend = new N8nBackendStub();
        botApi = new N8nBotApiStub();
    }

    @AfterAll
    static void stopStubs() {
        backend.close();
        botApi.close();
        if (!isRunning()) {
            startN8n();
        }
        awaitHealthy(Duration.ofSeconds(180));
    }

    @BeforeEach
    void prepare() {
        if (!isRunning()) {
            startN8n();
        }
        awaitHealthy(Duration.ofSeconds(180));
        backend.reset();
        botApi.reset();
    }

    // ---------------------------------------------------------------- apoio

    private static String messages(int batch, String... texts) {
        List<String> actions = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            actions.add(
                    "{\"actionId\":\"u%d-%d\",\"type\":\"MESSAGE\",\"chatId\":7001,\"text\":\"%s\"}"
                            .formatted(batch, i + 1, texts[i]));
        }
        return String.join(",", actions);
    }

    private static Reply sendUpdate(int batch, String actions) {
        backend.updateReturns(
                "{\"version\":\"v1\",\"updateId\":%d,\"batchId\":\"u%d\",\"actions\":[%s]}"
                        .formatted(batch, batch, actions));
        return post(
                N8N_URL + "/webhook/telegram-update",
                Map.of("X-Telegram-Bot-Api-Secret-Token", WEBHOOK_SECRET),
                "application/json",
                ("{\"update_id\":" + batch + "}").getBytes(StandardCharsets.UTF_8));
    }

    private static Reply deliverBatch(int batch, String... texts) {
        return N8nDeliveryWorkflowIT.deliver(
                N8nDeliveryWorkflowIT.DELIVERY_TOKEN,
                "u" + batch,
                N8nDeliveryWorkflowIT.batch("u" + batch, texts));
    }

    /**
     * Dispara a chamada em outra thread; a queda do n8n faz a chamada falhar, o que aqui não
     * importa.
     */
    private static CompletableFuture<Reply> async(Supplier<Reply> call) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return call.get();
                    } catch (RuntimeException expectedWhenN8nDies) {
                        return null;
                    }
                });
    }

    private static void await(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("a condição não aconteceu a tempo");
    }

    private static List<String> confirmedIds() {
        List<String> ids = new ArrayList<>();
        for (LoggedRequest confirmation : backend.confirmations()) {
            JsonNode result = JSON.readTree(confirmation.getBodyAsString()).get("results").get(0);
            ids.add(result.get("actionId").asString());
        }
        return ids;
    }

    private static void crashAndRestart() {
        killN8n();
        startN8n();
        awaitHealthy(Duration.ofSeconds(180));
    }

    // ---------------------------------------------------------------- N13b: queda entre ações

    @Test
    @DisplayName(
            "N13b (update): queda entre as ações; ao reenviar, a confirmada não repete e as restantes chegam uma vez")
    void updateKilledBetweenActionsResumesWithoutRepeating() {
        backend.delayState("u51-2", 20_000);
        CompletableFuture<Reply> inFlight =
                async(() -> sendUpdate(51, messages(51, "One", "Two", "Three")));
        await(() -> backend.queriedActionIds().contains("u51-2"), Duration.ofSeconds(30));
        assertThat(confirmedIds()).containsExactly("u51-1");
        List<String> before = botApi.sentTexts();

        crashAndRestart();
        inFlight.join();
        backend.reset();
        backend.actionState("u51-1", "EXECUTED");
        Reply resent = sendUpdate(51, messages(51, "One", "Two", "Three"));

        assertThat(resent.status()).isEqualTo(200);
        assertThat(before).containsExactly("One");
        assertThat(botApi.sentTexts()).containsExactly("One", "Two", "Three");
        assertThat(confirmedIds()).containsExactly("u51-2", "u51-3");
    }

    @Test
    @DisplayName("N13b (lote proativo): queda entre as ações; o backend reentrega e nada repete")
    void batchKilledBetweenActionsResumesWithoutRepeating() {
        backend.delayState("u52-2", 20_000);
        CompletableFuture<Reply> inFlight = async(() -> deliverBatch(52, "One", "Two", "Three"));
        await(() -> backend.queriedActionIds().contains("u52-2"), Duration.ofSeconds(30));
        assertThat(confirmedIds()).containsExactly("u52-1");

        crashAndRestart();
        inFlight.join();
        backend.reset();
        backend.actionState("u52-1", "EXECUTED");
        Reply redelivered = deliverBatch(52, "One", "Two", "Three");

        assertThat(redelivered.status()).isEqualTo(200);
        assertThat(botApi.sentTexts()).containsExactly("One", "Two", "Three");
        assertThat(confirmedIds()).containsExactly("u52-2", "u52-3");
    }

    // ---------------------------------------------------------------- N13c: janela em voo

    @Test
    @DisplayName(
            "N13c: queda com a ação em voo; ela repete no máximo uma vez e as demais seguem a regra de N13b")
    void killedWhileAnActionIsInFlightRepeatsAtMostOnce() throws Exception {
        botApi.delayWhenBodyContains("sendMessage", "Two", 20_000);
        CompletableFuture<Reply> inFlight =
                async(() -> sendUpdate(53, messages(53, "One", "Two", "Three")));
        await(() -> backend.queriedActionIds().contains("u53-2"), Duration.ofSeconds(30));
        Thread.sleep(700);
        assertThat(confirmedIds()).containsExactly("u53-1");
        List<String> before = botApi.sentTexts();

        crashAndRestart();
        inFlight.join();
        backend.reset();
        botApi.reset();
        backend.actionState("u53-1", "EXECUTED");
        Reply resent = sendUpdate(53, messages(53, "One", "Two", "Three"));

        assertThat(resent.status()).isEqualTo(200);
        List<String> all = new ArrayList<>(before);
        all.addAll(botApi.sentTexts());
        assertThat(all.stream().filter("One"::equals).count())
                .as("a confirmada nunca repete")
                .isEqualTo(1);
        assertThat(all.stream().filter("Two"::equals).count())
                .as("a ação em voo repete no máximo uma vez (comportamento documentado)")
                .isBetween(1L, 2L);
        assertThat(all.stream().filter("Three"::equals).count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- reinício planejado

    @Test
    @DisplayName(
            "reinício planejado volta a receber updates em até 60 s e a mensagem do intervalo é respondida uma vez")
    void plannedRestartResumesWithin60SecondsAndAnswersOnce() throws Exception {
        long start = System.nanoTime();
        Thread restart = new Thread(() -> restartN8n());
        restart.start();

        Reply answered = null;
        long elapsedSeconds = 0;
        List<String> attempts = new ArrayList<>();
        while (answered == null || answered.status() != 200) {
            try {
                answered = sendUpdate(54, messages(54, "Resumed"));
            } catch (RuntimeException refused) {
                answered = null;
            }
            elapsedSeconds = (System.nanoTime() - start) / 1_000_000_000L;
            attempts.add(
                    elapsedSeconds
                            + " s: "
                            + (answered == null ? "sem conexão" : "status " + answered.status())
                            + ", mensagens: "
                            + botApi.sentTexts());
            assertThat(elapsedSeconds)
                    .as("o n8n precisa voltar a receber em até 60 s")
                    .isLessThanOrEqualTo(60L);
            Thread.sleep(500);
        }
        restart.join();

        assertThat(botApi.sentTexts()).as("tentativas: " + attempts).containsExactly("Resumed");
        System.out.println("n8n voltou a receber updates em " + elapsedSeconds + " s");
    }

    // ---------------------------------------------------------------- backend fora do ar

    @Test
    @DisplayName(
            "backend fora do ar responde 503 ao Telegram e a mensagem é respondida quando ele volta")
    void backendDownAnswers503ThenAnswersWhenItReturns() {
        backend.close();
        Reply refused;
        try {
            refused =
                    post(
                            N8N_URL + "/webhook/telegram-update",
                            Map.of("X-Telegram-Bot-Api-Secret-Token", WEBHOOK_SECRET),
                            "application/json",
                            "{\"update_id\":55}".getBytes(StandardCharsets.UTF_8));
        } finally {
            backend = new N8nBackendStub();
        }
        assertThat(refused.status()).isEqualTo(503);
        assertThat(botApi.calls()).isEmpty();

        Reply again = sendUpdate(55, messages(55, "Back"));

        assertThat(again.status()).isEqualTo(200);
        assertThat(botApi.sentTexts()).containsExactly("Back");
    }

    // ---------------------------------------------------------------- lote lento (R-07)

    @Test
    @DisplayName("lote lento abaixo de 30 s é aceito uma vez e não duplica nada")
    void slowBatchBelowThirtySecondsHasNoDuplicate() {
        botApi.delayWhenBodyContains("sendMessage", "Slow", 2_000);

        long begin = System.nanoTime();
        Reply reply = deliverBatch(56, "Slow1", "Slow2", "Slow3");
        long seconds = (System.nanoTime() - begin) / 1_000_000_000L;

        assertThat(reply.status()).isEqualTo(200);
        assertThat(seconds).isGreaterThanOrEqualTo(6L).isLessThan(30L);
        assertThat(botApi.sentTexts()).containsExactly("Slow1", "Slow2", "Slow3");
        assertThat(confirmedIds()).containsExactly("u56-1", "u56-2", "u56-3");
    }
}
