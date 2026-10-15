package br.com.felipeblaft.reports.outbound.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.OrchestratorDeliveryBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.channel.DeliverPendingActionsUseCase;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * V8 a V11, C9, C11, SC-004: entrega proativa ao orquestrador (WireMock) com retentativa, ordem por
 * chat, portão de confirmação, idempotência e métricas. O tempo é do {@code RelogioAjustavel}; o
 * despachante agendado fica desligado (intervalo de 1 h) e os ciclos são chamados pelo teste.
 */
@ExtendWith(OutputCaptureExtension.class)
class ChannelActionsDeliveryIT extends OrchestratorDeliveryBase {

    private double gauge(String name) {
        return metrics.get(name).gauge().value();
    }

    /** Os contadores são do contexto compartilhado: cada teste enxerga só o que ele mesmo somou. */
    private final java.util.Map<String, Double> baseLine = new java.util.HashMap<>();

    private static String key(String name, String... tags) {
        return name + java.util.Arrays.toString(tags);
    }

    private double total(String name, String... tags) {
        var search = metrics.find(name).tags(tags).counter();
        return search == null ? 0 : search.count();
    }

    @org.junit.jupiter.api.BeforeEach
    void markBaseLine() {
        for (String[] counter :
                new String[][] {
                    {"channel_deliveries_total", "result", "accepts"},
                    {"channel_deliveries_total", "result", "refused"},
                    {"channel_permanent_failure_actions_total"},
                    {"channel_confirmations_total", "outcome", "FAILED", "code", "TELEGRAM_ERROR"}
                }) {
            String[] tags = java.util.Arrays.copyOfRange(counter, 1, counter.length);
            baseLine.put(key(counter[0], tags), total(counter[0], tags));
        }
    }

    private double counter(String name, String... tags) {
        return total(name, tags) - baseLine.getOrDefault(key(name, tags), 0.0);
    }

    private void confirmEverythingThroughExecutor(JsonNode batch) throws Exception {
        orchestratorExecutor().run(batch.get("actions"));
    }

    // ---- V8 / C9: orquestrador fora do ar e volta

    @Test
    void whenDownStaysPendingResendsWithSameIdsAtGrowingIntervalsAndDeliversOnReturn()
            throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        answer(503);

        generateNoticeOf(matteo);

        String actionId = actionIds().get(0);
        assertThat(actionId).startsWith("p:result:");
        task.dispatch();
        assertThat(state(actionId)).isEqualTo("PENDING");
        // o processamento do relatório não depende do orquestrador (FR-015)
        assertThat(jdbc.queryForObject("select status from lesson_report", String.class))
                .isEqualTo("AWAITING_ANSWERS");
        Instant first =
                jdbc.queryForObject("select next_attempt_at from channel_action", Timestamp.class)
                        .toInstant();
        // 30 s ±10%
        assertThat(Duration.between(clock.now(), first).toSeconds()).isBetween(26L, 34L);

        clock.advance(Duration.ofSeconds(35));
        task.dispatch();
        Instant second =
                jdbc.queryForObject("select next_attempt_at from channel_action", Timestamp.class)
                        .toInstant();
        assertThat(Duration.between(clock.now(), second).toSeconds()).isBetween(53L, 67L);

        clock.advance(Duration.ofSeconds(70));
        accept();
        task.dispatch();

        assertThat(state(actionId)).isEqualTo("DELIVERED");
        List<LoggedRequest> received = deliveries();
        assertThat(received).hasSize(3);
        List<String> sentIds = new ArrayList<>();
        List<Integer> attempts = new ArrayList<>();
        for (LoggedRequest delivery : received) {
            JsonNode batch = body(delivery);
            assertThat(batch.path("origin").asString()).isEqualTo("PROACTIVE");
            assertThat(delivery.getHeader("Idempotency-Key"))
                    .isEqualTo(batch.path("batchId").asString());
            assertThat(delivery.getHeader("X-Channel-Delivery-Token"))
                    .isEqualTo("orchestrator-secret");
            sentIds.add(batch.get("actions").get(0).path("actionId").asString());
            attempts.add(batch.path("attempt").asInt());
        }
        assertThat(sentIds).containsOnly(actionId);
        assertThat(attempts).containsExactly(1, 2, 3);
        assertThat(counter("channel_deliveries_total", "result", "refused")).isEqualTo(2);
        assertThat(counter("channel_deliveries_total", "result", "accepts")).isEqualTo(1);
    }

    @Test
    void refusalTimeoutAndConnectionErrorStayPendingUntilDeadline(CapturedOutput output)
            throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        String actionId = actionIds().get(0);

        answer(500);
        task.dispatch();
        assertThat(state(actionId)).isEqualTo("PENDING");

        clock.advance(Duration.ofMinutes(1));
        answer(404);
        task.dispatch();
        assertThat(state(actionId)).isEqualTo("PENDING");

        clock.advance(Duration.ofMinutes(5));
        replyWithDelay(200, 4000); // além do timeout de resposta (1,5 s)
        task.dispatch();
        assertThat(state(actionId)).isEqualTo("PENDING");

        clock.advance(Duration.ofMinutes(20));
        ORCHESTRATOR.resetMappings();
        ORCHESTRATOR.stubFor(
                post(urlPathEqualTo(PATH))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        task.dispatch();
        assertThat(state(actionId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select attempts from channel_action", Integer.class))
                .isEqualTo(4);

        // depois de 24 h sem aceite: falha definitiva, sem dado de aluno no log e com métrica
        clock.advance(Duration.ofHours(25));
        accept();
        task.dispatch();

        assertThat(state(actionId)).isEqualTo("PERMANENTLY_FAILED");
        assertThat(jdbc.queryForObject("select reason_code from channel_action", String.class))
                .isEqualTo("DEADLINE_EXPIRED");
        assertThat(counter("channel_permanent_failure_actions_total")).isEqualTo(1);
        assertThat(output.getOut() + output.getErr())
                .contains("channel-action-permanent-failure actionId=" + actionId)
                .doesNotContain("Matteo", "perguntas pendentes")
                .doesNotContain("orchestrator-secret", "secret-in-path-123");
        assertThat(deliveries()).hasSize(4);
    }

    @Test
    void nextAttemptNeverExceedsDeadlineAndPermanentFailureComesAtDeadline() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        String actionId = actionIds().get(0);

        answer(500);
        task.dispatch();
        clock.advance(Duration.ofHours(24).minusSeconds(10));
        task.dispatch();

        // a espera de 1 min passaria do prazo: a próxima tentativa é o próprio prazo
        assertThat(state(actionId)).isEqualTo("PENDING");
        assertThat(
                        jdbc.queryForObject(
                                "select next_attempt_at = deadline_at from channel_action where id = ?",
                                Boolean.class,
                                actionId))
                .isTrue();

        clock.advance(Duration.ofSeconds(10));
        task.dispatch();

        assertThat(state(actionId)).isEqualTo("PERMANENTLY_FAILED");
        assertThat(
                        jdbc.queryForObject(
                                "select finalized_at = deadline_at and reason_code = 'DEADLINE_EXPIRED'"
                                        + " from channel_action where id = ?",
                                Boolean.class,
                                actionId))
                .isTrue();
        assertThat(counter("channel_permanent_failure_actions_total")).isEqualTo(1);
    }

    // ---- V9: ordem por chat, portão de confirmação, chats independentes

    @Test
    void sameChatBatchWaitsForConfirmationOrTimeoutAndDistinctChatsFollowInParallel()
            throws Exception {
        Teacher bruno = scenario.bruno();
        Student davi = scenario.student(bruno, "Davi Nunes", "Bateria");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        link(USER_BRUNO, ScenarioIT.CODE_BRUNO);
        generateNoticeOf(matteo);
        generateNoticeOf(helena);
        register.run(bruno.id(), davi.id(), webAudio(), Channel.WEB);
        scenario.processQueue();
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isEqualTo(3);

        task.dispatch();

        // um lote de Clara e o de Bruno; o segundo lote de Clara espera
        List<LoggedRequest> first = deliveries();
        assertThat(first).hasSize(2);
        List<Long> chats = new ArrayList<>();
        for (LoggedRequest delivery : first) {
            chats.add(body(delivery).get("actions").get(0).path("chatId").asLong());
        }
        assertThat(chats).containsExactlyInAnyOrder(USER_CLARA, USER_BRUNO);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from channel_action where state = 'PENDING'",
                                Integer.class))
                .isEqualTo(1);

        // o n8n confirma o primeiro lote de Clara: o segundo é liberado antes do timeout
        JsonNode claraFirst = null;
        for (LoggedRequest delivery : first) {
            JsonNode batch = body(delivery);
            if (batch.get("actions").get(0).path("chatId").asLong() == USER_CLARA) {
                claraFirst = batch;
            }
        }
        confirmEverythingThroughExecutor(claraFirst);
        task.dispatch();
        assertThat(deliveries()).hasSize(3);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from channel_action where state = 'PENDING'",
                                Integer.class))
                .isZero();
    }

    @Test
    void withoutConfirmationGateReleasesAfter60Seconds() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        generateNoticeOf(helena);

        task.dispatch();
        assertThat(deliveries()).hasSize(1);

        clock.advance(Duration.ofSeconds(59));
        task.dispatch();
        assertThat(deliveries()).hasSize(1);

        clock.advance(Duration.ofSeconds(2));
        task.dispatch();
        assertThat(deliveries()).hasSize(2);
        // métrica de ações entregues e ainda sem confirmação além da espera máxima
        clock.advance(Duration.ofSeconds(5));
        assertThat(gauge("channel_unconfirmed_actions_total")).isEqualTo(1);
    }

    // ---- V10: idempotência do evento, concorrência, vínculo

    @Test
    void notifyingSameJobTwiceGeneratesOnlyOneAction() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        var job =
                queue.searchById(jdbc.queryForObject("select id from processing_job", UUID.class))
                        .orElseThrow();

        notification.notifyResult(job);
        notification.notifyResult(job);

        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void twoConcurrentDispatchersDeliverBatchOnlyOnce() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        replyWithDelay(200, 600);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DeliverPendingActionsUseCase.Cycle>> cycles = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            cycles.add(
                    pool.submit(
                            () -> {
                                start.await();
                                return deliver.run(20);
                            }));
        }
        start.countDown();
        int accepted = 0;
        for (Future<DeliverPendingActionsUseCase.Cycle> cycle : cycles) {
            accepted += cycle.get().acceptedBatches();
        }
        pool.shutdown();

        assertThat(accepted).isEqualTo(1);
        assertThat(deliveries()).hasSize(1);
    }

    @Test
    void undoneLinkDiscardsActionWithoutDelivery() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        jdbc.update("delete from telegram_link");

        task.dispatch();

        String actionId = actionIds().get(0);
        assertThat(state(actionId)).isEqualTo("DISCARDED");
        assertThat(jdbc.queryForObject("select reason_code from channel_action", String.class))
                .isEqualTo("LINK_UNDONE");
        assertThat(deliveries()).isEmpty();
    }

    // ---- V11: corrida e reinício

    @Test
    void confirmationArrivingDuringDispatchHttpPrevails() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        String actionId = actionIds().get(0);
        replyWithDelay(200, 1000);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> confirmation =
                pool.submit(
                        () -> {
                            Thread.sleep(300);
                            mvc.perform(
                                    org.springframework.test.web.servlet.request
                                            .MockMvcRequestBuilders.post(
                                                    "/internal/v1/channel/telegram/confirmations")
                                            .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    "{\"version\":\"v1\",\"results\":[{\"actionId\":\""
                                                            + actionId
                                                            + "\",\"outcome\":\"EXECUTED\"}]}"));
                            return null;
                        });

        task.dispatch();
        confirmation.get();
        pool.shutdown();

        assertThat(state(actionId)).isEqualTo("EXECUTED");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from channel_action where state = 'DELIVERED'",
                                Integer.class))
                .isZero();
    }

    @Test
    void dispatcherThatDiesLeavesBatchRedeliverableWithSameIdsWhenLeaseExpires() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        String actionId = actionIds().get(0);
        // outro despachante reservou o lote (lease de 60 s) e morreu antes de entregar
        assertThat(
                        actions.reserveNextDueBatch(
                                clock.now(), Duration.ofSeconds(60), clock.now().plusSeconds(60)))
                .hasSize(1);

        task.dispatch();
        assertThat(deliveries()).isEmpty();

        clock.advance(Duration.ofSeconds(61));
        task.dispatch();

        assertThat(deliveries()).hasSize(1);
        assertThat(body(deliveries().get(0)).get("actions").get(0).path("actionId").asString())
                .isEqualTo(actionId);
    }

    // ---- idempotência do receptor (FR-013, SC-004)

    @Test
    void sameBatchDeliveredTwiceToReceiverRunsEachActionOnlyOnce() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        answer(500);
        task.dispatch();
        clock.advance(Duration.ofMinutes(1));
        accept();
        task.dispatch();
        API_BOTS.resetRequests();
        JsonNode batch = body(deliveries().getLast());

        confirmEverythingThroughExecutor(batch);
        confirmEverythingThroughExecutor(batch); // reentrega do mesmo lote (ex.: n8n reiniciou)

        assertThat(calls("sendMessage")).hasSize(1);
        assertThat(sentTexts().get(0))
                .isEqualTo("O relatório de Matteo Ferreira tem perguntas pendentes.");
        assertThat(state(actionIds().get(0))).isEqualTo("EXECUTED");
    }

    // ---- métricas

    @Test
    void outboxAndConfirmationMetrics() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        generateNoticeOf(helena);

        assertThat(gauge("channel_pending_actions")).isEqualTo(2);
        clock.advance(Duration.ofSeconds(90));
        assertThat(gauge("channel_oldest_pending_action_seconds")).isEqualTo(90);

        task.dispatch();
        assertThat(gauge("channel_pending_actions")).isEqualTo(1);
        assertThat(counter("channel_deliveries_total", "result", "accepts")).isEqualTo(1);

        String delivered =
                jdbc.queryForObject(
                        "select id from channel_action where state = 'DELIVERED'", String.class);
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/internal/v1/channel/telegram/confirmations")
                        .header("X-N8N-Service-Token", TOKEN_SERVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"version\":\"v1\",\"results\":[{\"actionId\":\""
                                        + delivered
                                        + "\",\"outcome\":\"FAILED\",\"reasonCode\":\"ANY_NEW_CODE\"}]}"));
        assertThat(
                        counter(
                                "channel_confirmations_total",
                                "outcome",
                                "FAILED",
                                "code",
                                "TELEGRAM_ERROR"))
                .isEqualTo(1);
    }
}
