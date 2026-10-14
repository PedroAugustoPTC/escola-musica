package br.com.felipeblaft.reports.usecase.channel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryChannelActionRepository;
import br.com.felipeblaft.reports.doubles.InMemoryChannelDocumentRepository;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase.NewAction;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CleanChannelActionsUseCaseTest {

    private final FixedClock clock = new FixedClock();
    private final InMemoryChannelActionRepository actions = new InMemoryChannelActionRepository();
    private final InMemoryChannelDocumentRepository documents =
            new InMemoryChannelDocumentRepository();
    private final CleanChannelActionsUseCase clean =
            new CleanChannelActionsUseCase(
                    actions, documents, clock, Duration.ofHours(24), Duration.ofDays(7));

    private void finalizedAction(String id) {
        new SaveChannelActionsUseCase(
                        actions,
                        clock,
                        new RetryPolicy(
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(15),
                                Duration.ofHours(24)))
                .run(
                        "p:" + id,
                        ChannelActionOrigin.PROACTIVE,
                        null,
                        List.of(
                                new NewAction(
                                        id,
                                        ChannelActionType.MESSAGE,
                                        1L,
                                        UUID.randomUUID(),
                                        null,
                                        "{\"text\":\"segredo\"}",
                                        "{\"text\":\"alt\"}")));
        actions.stored(id).confirmExecuted(clock.now());
    }

    private void deliveredReply(String id, long updateId) {
        new SaveChannelActionsUseCase(
                        actions,
                        clock,
                        new RetryPolicy(
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(15),
                                Duration.ofHours(24)))
                .run(
                        "u" + updateId,
                        ChannelActionOrigin.UPDATE_REPLY,
                        updateId,
                        List.of(
                                new NewAction(
                                        id,
                                        ChannelActionType.MESSAGE,
                                        1L,
                                        null,
                                        null,
                                        "{\"text\":\"nome do aluno\"}",
                                        "{\"text\":\"alt\"}")));
    }

    @Test
    void deliveredNeverConfirmedExpiresAtDeadlineAndEntersRetention() {
        deliveredReply("u1-e0", 1L);

        clock.advance(Duration.ofHours(23));
        assertThat(clean.run().expiredActions()).isZero();
        assertThat(actions.stored("u1-e0").state()).isEqualTo(ChannelActionState.DELIVERED);
        assertThat(actions.stored("u1-e0").payload()).isNotNull();

        clock.advance(Duration.ofHours(1));
        assertThat(clean.run().expiredActions()).isEqualTo(1);
        assertThat(actions.stored("u1-e0").state())
                .isEqualTo(ChannelActionState.PERMANENTLY_FAILED);
        assertThat(actions.stored("u1-e0").reasonCode())
                .isEqualTo(ChannelAction.CODE_WITHOUT_CONFIRMATION);
        // o conteúdo só some depois da retenção, contada a partir da expiração
        assertThat(actions.stored("u1-e0").payload()).isNotNull();

        clock.advance(Duration.ofHours(24).plusSeconds(1));
        CleanChannelActionsUseCase.Result reset = clean.run();
        assertThat(reset.expiredActions()).isZero();
        assertThat(reset.resetPayloads()).isEqualTo(1);
        assertThat(actions.stored("u1-e0").payload()).isNull();
        assertThat(actions.stored("u1-e0").compensation()).isNull();

        clock.advance(Duration.ofDays(7));
        assertThat(clean.run().removedRegistrations()).isEqualTo(1);
        assertThat(actions.stored("u1-e0")).isNull();
    }

    @Test
    void doesNotExpireConfirmedNorPendingAction() {
        deliveredReply("u1-e0", 1L);
        actions.stored("u1-e0").confirmExecuted(clock.now());
        finalizedAction("p:a:0");
        clock.advance(Duration.ofHours(30));

        assertThat(clean.run().expiredActions()).isZero();
        assertThat(actions.stored("u1-e0").state()).isEqualTo(ChannelActionState.EXECUTED);
        assertThat(actions.stored("p:a:0").state()).isEqualTo(ChannelActionState.EXECUTED);
    }

    @Test
    void clearsContentAfter24HAndDeletesRecordAfter7Days() {
        finalizedAction("p:a:0");
        clean.run();
        assertThat(actions.stored("p:a:0").payload()).isNotNull();

        clock.advance(Duration.ofHours(24).plusSeconds(1));
        CleanChannelActionsUseCase.Result first = clean.run();
        assertThat(first.resetPayloads()).isEqualTo(1);
        assertThat(actions.stored("p:a:0").payload()).isNull();
        assertThat(actions.stored("p:a:0").compensation()).isNull();
        assertThat(actions.stored("p:a:0")).isNotNull();

        clock.advance(Duration.ofDays(7));
        CleanChannelActionsUseCase.Result second = clean.run();
        assertThat(second.removedRegistrations()).isEqualTo(1);
        assertThat(actions.stored("p:a:0")).isNull();
    }

    @Test
    void doesNotTouchNonFinalizedAction() {
        finalizedAction("p:a:0");
        new SaveChannelActionsUseCase(
                        actions,
                        clock,
                        new RetryPolicy(
                                Duration.ofSeconds(30),
                                Duration.ofMinutes(15),
                                Duration.ofDays(30)))
                .run(
                        "p:b",
                        ChannelActionOrigin.PROACTIVE,
                        null,
                        List.of(
                                new NewAction(
                                        "p:b:0",
                                        ChannelActionType.MESSAGE,
                                        1L,
                                        UUID.randomUUID(),
                                        null,
                                        "{}",
                                        null)));
        clock.advance(Duration.ofDays(10));
        clean.run();
        assertThat(actions.stored("p:b:0")).isNotNull();
        assertThat(actions.stored("p:b:0").payload()).isNotNull();
    }

    @Test
    void removeExpiredDocuments() {
        documents.emit(
                new ChannelDocument(
                        "a".repeat(64),
                        UUID.randomUUID(),
                        ReportType.LESSON,
                        UUID.randomUUID(),
                        null,
                        "x.pdf",
                        clock.now(),
                        clock.now().plus(Duration.ofHours(25))));
        assertThat(clean.run().removedDocuments()).isZero();
        clock.advance(Duration.ofHours(26));
        assertThat(clean.run().removedDocuments()).isEqualTo(1);
        assertThat(documents.data).isEmpty();
    }

    @Test
    void withNothingToCleanReturnsZeros() {
        assertThat(clean.run()).isEqualTo(new CleanChannelActionsUseCase.Result(0, 0, 0, 0));
    }
}
