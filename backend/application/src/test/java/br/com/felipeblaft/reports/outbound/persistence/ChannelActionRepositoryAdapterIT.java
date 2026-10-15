package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ChannelActionRepositoryAdapterIT extends IntegrationBase {

    private static final Instant T0 = Instant.parse("2026-10-02T12:00:00Z");
    private static final Duration CONFIRMATION = Duration.ofSeconds(60);

    private final RetryPolicy policy =
            new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));

    @Autowired ChannelActionRepository actions;
    @Autowired ChannelDocumentRepository documents;

    private ChannelAction proactive(String batchId, int pos, long chatId) {
        return ChannelAction.create(
                batchId + ":" + pos,
                batchId,
                ChannelActionOrigin.PROACTIVE,
                null,
                chatId,
                UUID.randomUUID(),
                null,
                ChannelActionType.MESSAGE,
                "{\"text\":\"" + batchId + pos + "\"}",
                "{\"text\":\"alt\"}",
                T0,
                policy);
    }

    private List<ChannelAction> batch(String batchId, long chatId, int quantity) {
        List<ChannelAction> batch = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            batch.add(proactive(batchId, i, chatId));
        }
        return batch;
    }

    @Test
    void saveIsIdempotentByIdAndNeverOverwrites() {
        ChannelAction original = proactive("p:a", 0, 1);
        assertThat(actions.saveBatch(List.of(original))).isEqualTo(1);
        ChannelAction other =
                ChannelAction.create(
                        "p:a:0",
                        "p:a",
                        ChannelActionOrigin.PROACTIVE,
                        null,
                        1,
                        UUID.randomUUID(),
                        null,
                        ChannelActionType.MESSAGE,
                        "{\"text\":\"diferente\"}",
                        null,
                        T0.plusSeconds(5),
                        policy);
        assertThat(actions.saveBatch(List.of(other, proactive("p:a", 1, 1)))).isEqualTo(1);

        ChannelAction read = actions.searchById("p:a:0").orElseThrow();
        assertThat(read.payload()).contains("p:a0");
        assertThat(read.compensation()).contains("alt");
        assertThat(read.state()).isEqualTo(ChannelActionState.PENDING);
        assertThat(read.createdAt()).isEqualTo(T0);
        assertThat(read.version()).isZero();
        assertThat(actions.searchById("missing")).isEmpty();
    }

    @Test
    void updateRepliesAreReadPerUpdateInCreationOrder() {
        List<ChannelAction> responses = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            responses.add(
                    ChannelAction.create(
                            "u7-" + i,
                            "u7",
                            ChannelActionOrigin.UPDATE_REPLY,
                            7L,
                            55,
                            null,
                            null,
                            ChannelActionType.MESSAGE,
                            "{}",
                            null,
                            T0,
                            policy));
        }
        actions.saveBatch(responses);
        assertThat(actions.searchByUpdate(7L))
                .extracting(ChannelAction::id)
                .containsExactly("u7-0", "u7-1", "u7-2");
        assertThat(actions.searchByBatch("u7"))
                .extracting(ChannelAction::id)
                .containsExactly("u7-0", "u7-1", "u7-2");
        assertThat(actions.searchByUpdate(8L)).isEmpty();
        assertThat(actions.searchByUpdate(7L).get(0).state())
                .isEqualTo(ChannelActionState.DELIVERED);
        assertThat(actions.searchByUpdate(7L).get(0).deliveredAt()).isEqualTo(T0);
    }

    @Test
    void reserveReturnsWholeBatchAndLeasePreventsSecondReserve() {
        actions.saveBatch(batch("p:a", 1, 3));
        Instant lease = T0.plusSeconds(60);

        List<ChannelAction> reserved = actions.reserveNextDueBatch(T0, CONFIRMATION, lease);

        assertThat(reserved)
                .extracting(ChannelAction::id)
                .containsExactly("p:a:0", "p:a:1", "p:a:2");
        assertThat(reserved).allSatisfy(a -> assertThat(a.version()).isEqualTo(1));
        assertThat(reserved).allSatisfy(a -> assertThat(a.nextAttemptAt()).isEqualTo(lease));
        assertThat(
                        actions.reserveNextDueBatch(
                                T0.plusSeconds(59), CONFIRMATION, lease.plusSeconds(60)))
                .isEmpty();
        // o lease venceu: o mesmo lote volta, com os mesmos ids
        assertThat(
                        actions.reserveNextDueBatch(
                                T0.plusSeconds(60), CONFIRMATION, lease.plusSeconds(60)))
                .extracting(ChannelAction::id)
                .containsExactly("p:a:0", "p:a:1", "p:a:2");
    }

    @Test
    void doesNotReserveActionNotYetDue() {
        actions.saveBatch(batch("p:a", 1, 1));
        assertThat(
                        actions.reserveNextDueBatch(
                                T0.minusSeconds(1), CONFIRMATION, T0.plusSeconds(60)))
                .isEmpty();
    }

    @Test
    void twoConcurrentConsumersNeverReserveSameBatch() throws Exception {
        int batches = 6;
        for (int i = 0; i < batches; i++) {
            actions.saveBatch(batch("p:l" + i, 100 + i, 3));
        }
        int consumers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(consumers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<List<ChannelAction>>>> futures = new ArrayList<>();
        for (int c = 0; c < consumers; c++) {
            futures.add(
                    pool.submit(
                            () -> {
                                start.await();
                                List<List<ChannelAction>> my = new ArrayList<>();
                                for (int i = 0; i < batches; i++) {
                                    List<ChannelAction> reserved =
                                            actions.reserveNextDueBatch(
                                                    T0, CONFIRMATION, T0.plusSeconds(60));
                                    if (!reserved.isEmpty()) {
                                        my.add(reserved);
                                    }
                                }
                                return my;
                            }));
        }
        start.countDown();
        Set<String> ids = new HashSet<>();
        int reservedBatches = 0;
        for (Future<List<List<ChannelAction>>> future : futures) {
            for (List<ChannelAction> reserved : future.get()) {
                reservedBatches++;
                assertThat(reserved).hasSize(3);
                assertThat(reserved.stream().map(ChannelAction::batchId).distinct()).hasSize(1);
                for (ChannelAction action : reserved) {
                    assertThat(ids.add(action.id()))
                            .as("ação %s reservada duas vezes", action.id())
                            .isTrue();
                }
            }
        }
        pool.shutdown();
        assertThat(reservedBatches).isEqualTo(batches);
        assertThat(ids).hasSize(batches * 3);
    }

    @Test
    void perChatOrderGateWaitsForConfirmationOrTimeout() {
        actions.saveBatch(batch("p:a", 1, 1));
        actions.saveBatch(batch("p:b", 1, 1));
        actions.saveBatch(batch("p:c", 2, 1));

        List<ChannelAction> first =
                actions.reserveNextDueBatch(T0, CONFIRMATION, T0.plusSeconds(60));
        assertThat(first).extracting(ChannelAction::batchId).containsExactly("p:a");
        // o chat 1 está atrás do lote A (ainda pendente); o chat 2 é independente
        List<ChannelAction> second =
                actions.reserveNextDueBatch(T0, CONFIRMATION, T0.plusSeconds(60));
        assertThat(second).extracting(ChannelAction::batchId).containsExactly("p:c");
        assertThat(actions.reserveNextDueBatch(T0, CONFIRMATION, T0.plusSeconds(60))).isEmpty();

        // A é aceito (ENTREGUE) mas não confirmado: B espera até o timeout do portão
        ChannelAction a = first.get(0);
        a.markDelivered(T0);
        assertThat(actions.updateIfVersion(a, a.version())).isTrue();
        Instant afterLease = T0.plusSeconds(61);
        assertThat(actions.reserveNextDueBatch(T0.plusSeconds(30), CONFIRMATION, afterLease))
                .isEmpty();
        assertThat(
                        actions.reserveNextDueBatch(
                                afterLease, CONFIRMATION, afterLease.plusSeconds(60)))
                .extracting(ChannelAction::batchId)
                .containsExactly("p:b");
    }

    @Test
    void confirmationReleasesNextChatBatchBeforeTimeout() {
        actions.saveBatch(batch("p:a", 1, 1));
        actions.saveBatch(batch("p:b", 1, 1));
        ChannelAction a = actions.reserveNextDueBatch(T0, CONFIRMATION, T0.plusSeconds(60)).get(0);
        a.markDelivered(T0);
        actions.updateIfVersion(a, a.version());
        a = actions.searchById(a.id()).orElseThrow();
        a.confirmExecuted(T0.plusSeconds(2));
        assertThat(actions.updateIfVersion(a, a.version())).isTrue();

        assertThat(actions.reserveNextDueBatch(T0.plusSeconds(3), CONFIRMATION, T0.plusSeconds(63)))
                .extracting(ChannelAction::batchId)
                .containsExactly("p:b");
    }

    @Test
    void conditionalUpdateAdvancesVersionAndOnlyAcceptsReadVersion() {
        actions.saveBatch(batch("p:a", 1, 1));
        ChannelAction read = actions.searchById("p:a:0").orElseThrow();
        ChannelAction concurrent = actions.searchById("p:a:0").orElseThrow();

        read.confirmExecuted(T0);
        assertThat(actions.updateIfVersion(read, 0)).isTrue();
        assertThat(read.version()).isEqualTo(1);

        concurrent.markDelivered(T0);
        assertThat(actions.updateIfVersion(concurrent, 0)).isFalse();
        assertThat(concurrent.version()).isZero();

        ChannelAction saved = actions.searchById("p:a:0").orElseThrow();
        assertThat(saved.state()).isEqualTo(ChannelActionState.EXECUTED);
        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.finalizedAt()).isEqualTo(T0);
    }

    @Test
    void rereadAlwaysSeesDatabaseState() {
        actions.saveBatch(batch("p:a", 1, 1));
        jdbc.update(
                "update channel_action set state = 'EXECUTED', version = version + 1 where id = 'p:a:0'");
        assertThat(actions.searchById("p:a:0").orElseThrow().state())
                .isEqualTo(ChannelActionState.EXECUTED);
        jdbc.update(
                "update channel_action set state = 'FAILED', version = version + 1 where id = 'p:a:0'");
        assertThat(actions.searchById("p:a:0").orElseThrow().state())
                .isEqualTo(ChannelActionState.FAILED);
    }

    @Test
    void permanentFailureAndDiscardAreSavedWithReason() {
        actions.saveBatch(batch("p:a", 1, 2));
        ChannelAction a = actions.searchById("p:a:0").orElseThrow();
        a.discard(T0.plusSeconds(1));
        actions.updateIfVersion(a, 0);
        ChannelAction b = actions.searchById("p:a:1").orElseThrow();
        b.expire(T0.plus(Duration.ofHours(25)));
        actions.updateIfVersion(b, 0);

        assertThat(actions.searchById("p:a:0").orElseThrow().reasonCode()).isEqualTo("LINK_UNDONE");
        ChannelAction failure = actions.searchById("p:a:1").orElseThrow();
        assertThat(failure.state()).isEqualTo(ChannelActionState.PERMANENTLY_FAILED);
        assertThat(failure.reasonCode()).isEqualTo("DEADLINE_EXPIRED");
        assertThat(failure.nextAttemptAt()).isNull();
    }

    @Test
    void purgeClearsContentAfterRetentionAndRemovesRecordAfterwards() {
        actions.saveBatch(batch("p:a", 1, 2));
        ChannelAction finalized = actions.searchById("p:a:0").orElseThrow();
        finalized.confirmExecuted(T0);
        actions.updateIfVersion(finalized, 0);

        ChannelActionRepository.Purge nothing =
                actions.purge(T0.minusSeconds(1), T0.minusSeconds(1));
        assertThat(nothing).isEqualTo(new ChannelActionRepository.Purge(0, 0));

        ChannelActionRepository.Purge reset = actions.purge(T0.plusSeconds(1), T0.minusSeconds(1));
        assertThat(reset.resetPayloads()).isEqualTo(1);
        ChannelAction withoutContent = actions.searchById("p:a:0").orElseThrow();
        assertThat(withoutContent.payload()).isNull();
        assertThat(withoutContent.compensation()).isNull();
        // a pendente não é tocada
        assertThat(actions.searchById("p:a:1").orElseThrow().payload()).isNotNull();

        ChannelActionRepository.Purge removed = actions.purge(T0.plusSeconds(1), T0.plusSeconds(1));
        assertThat(removed.removedRegistrations()).isEqualTo(1);
        assertThat(actions.searchById("p:a:0")).isEmpty();
        assertThat(actions.searchById("p:a:1")).isPresent();
    }

    @Test
    void pendingItemsCountOnlyPendingOnesAndReportTheOldest() {
        assertThat(actions.pending(T0)).isEqualTo(new ChannelActionRepository.Pending(0, null, 0));
        actions.saveBatch(batch("p:a", 1, 3));
        ChannelAction f = actions.searchById("p:a:0").orElseThrow();
        f.confirmExecuted(T0);
        actions.updateIfVersion(f, 0);
        ChannelAction e = actions.searchById("p:a:1").orElseThrow();
        e.markDelivered(T0);
        actions.updateIfVersion(e, 0);
        ChannelActionRepository.Pending pending = actions.pending(T0.plusSeconds(60));
        assertThat(pending.quantity()).isEqualTo(1);
        assertThat(pending.oldestCreatedAt()).isEqualTo(T0);
        assertThat(pending.withoutConfirmation()).isEqualTo(1);
        assertThat(actions.pending(T0).withoutConfirmation()).isZero();
    }

    @Test
    void channelDocumentIssuesSearchesByHashAndPurgesExpired() {
        UUID teacher = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        String hash = ChannelDocument.hashDoToken("test-token");
        documents.emit(
                new ChannelDocument(
                        hash,
                        teacher,
                        ReportType.SEMESTER,
                        report,
                        3,
                        "s-v3.pdf",
                        T0,
                        T0.plusSeconds(100)));
        documents.emit(
                new ChannelDocument(
                        ChannelDocument.hashDoToken("other"),
                        teacher,
                        ReportType.LESSON,
                        report,
                        null,
                        "a.pdf",
                        T0,
                        T0.plusSeconds(10)));

        ChannelDocument read = documents.searchByHash(hash).orElseThrow();
        assertThat(read.teacherId()).isEqualTo(teacher);
        assertThat(read.reportType()).isEqualTo(ReportType.SEMESTER);
        assertThat(read.version()).isEqualTo(3);
        assertThat(read.expiresAt()).isEqualTo(T0.plusSeconds(100));
        assertThat(documents.searchByHash("x".repeat(64))).isEmpty();
        assertThat(
                        documents
                                .searchByHash(ChannelDocument.hashDoToken("other"))
                                .orElseThrow()
                                .official())
                .isTrue();

        assertThat(documents.purgeExpired(T0.plusSeconds(50))).isEqualTo(1);
        assertThat(documents.searchByHash(ChannelDocument.hashDoToken("other"))).isEmpty();
        assertThat(documents.searchByHash(hash)).isPresent();
    }
}
