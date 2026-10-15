package br.com.felipeblaft.reports.domain.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChannelActionTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final UUID TEACHER = UUID.randomUUID();
    private static final UUID JOB = UUID.randomUUID();

    private final RetryPolicy policy =
            new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));

    private ChannelAction proactive(ChannelActionType type, String compensation) {
        return ChannelAction.create(
                "p:result:x:0",
                "p:result:x",
                ChannelActionOrigin.PROACTIVE,
                null,
                10L,
                TEACHER,
                JOB,
                type,
                "{\"text\":\"a\"}",
                compensation,
                NOW,
                policy);
    }

    private ChannelAction response() {
        return ChannelAction.create(
                "u1-0",
                "u1",
                ChannelActionOrigin.UPDATE_REPLY,
                1L,
                10L,
                null,
                null,
                ChannelActionType.MESSAGE,
                "{}",
                null,
                NOW,
                policy);
    }

    @Test
    void replyToUpdateStartsDelivered() {
        ChannelAction action = response();
        assertThat(action.state()).isEqualTo(ChannelActionState.DELIVERED);
        assertThat(action.deliveredAt()).isEqualTo(NOW);
        assertThat(action.nextAttemptAt()).isNull();
        assertThat(action.attempts()).isZero();
        assertThat(action.deadlineAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(action.channel())
                .isEqualTo(br.com.felipeblaft.reports.domain.common.Channel.TELEGRAM);
    }

    @Test
    void replyToUpdateRequiresUpdateId() {
        assertThatThrownBy(
                        () ->
                                ChannelAction.create(
                                        "u1-0",
                                        "u1",
                                        ChannelActionOrigin.UPDATE_REPLY,
                                        null,
                                        10L,
                                        null,
                                        null,
                                        ChannelActionType.MESSAGE,
                                        "{}",
                                        null,
                                        NOW,
                                        policy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void proactiveStartsDueAndPending() {
        ChannelAction action = proactive(ChannelActionType.MESSAGE, null);
        assertThat(action.state()).isEqualTo(ChannelActionState.PENDING);
        assertThat(action.nextAttemptAt()).isEqualTo(NOW);
        assertThat(action.deliveredAt()).isNull();
        assertThat(action.teacherId()).isEqualTo(TEACHER);
        assertThat(action.jobId()).isEqualTo(JOB);
        assertThat(action.version()).isZero();
    }

    @Test
    void deliveryFailureReschedulesAccordingToPolicy() {
        ChannelAction action = proactive(ChannelActionType.MESSAGE, null);
        action.registerDeliveryFailure(NOW, policy);
        assertThat(action.state()).isEqualTo(ChannelActionState.PENDING);
        assertThat(action.attempts()).isEqualTo(1);
        assertThat(action.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        action.registerDeliveryFailure(NOW.plusSeconds(30), policy);
        assertThat(action.attempts()).isEqualTo(2);
        assertThat(action.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30 + 60));
    }

    @Test
    void deliveryFailureAfterDeadlineIsPermanentFailure() {
        ChannelAction action = proactive(ChannelActionType.MESSAGE, null);
        Instant afterDeadline = NOW.plus(Duration.ofHours(24));
        action.registerDeliveryFailure(afterDeadline, policy);
        assertThat(action.state()).isEqualTo(ChannelActionState.PERMANENTLY_FAILED);
        assertThat(action.reasonCode()).isEqualTo(ChannelAction.DEADLINE_EXPIRED_CODE);
        assertThat(action.finalizedAt()).isEqualTo(afterDeadline);
        assertThat(action.nextAttemptAt()).isNull();
    }

    @Test
    void deliveryFailureDoesNotTouchActionThatIsNotPending() {
        ChannelAction action = response();
        action.registerDeliveryFailure(NOW, policy);
        assertThat(action.state()).isEqualTo(ChannelActionState.DELIVERED);
        assertThat(action.attempts()).isZero();
    }

    @Test
    void reservationPushesNextAttemptOnlyWhenPending() {
        ChannelAction action = proactive(ChannelActionType.MESSAGE, null);
        action.reserveUntil(NOW.plusSeconds(60));
        assertThat(action.nextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
        action.markDelivered(NOW);
        action.reserveUntil(NOW.plusSeconds(999));
        assertThat(action.nextAttemptAt()).isNull();
    }

    @Test
    void acceptMarksDeliveredAndCounts() {
        ChannelAction action = proactive(ChannelActionType.MESSAGE, null);
        action.markDelivered(NOW.plusSeconds(5));
        assertThat(action.state()).isEqualTo(ChannelActionState.DELIVERED);
        assertThat(action.deliveredAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(action.attempts()).isEqualTo(1);
        assertThat(action.nextAttemptAt()).isNull();
    }

    @Test
    void acceptDoesNotTouchAlreadyConfirmedAction() {
        ChannelAction action = proactive(ChannelActionType.MESSAGE, null);
        action.confirmExecuted(NOW);
        action.markDelivered(NOW.plusSeconds(1));
        assertThat(action.state()).isEqualTo(ChannelActionState.EXECUTED);
        assertThat(action.deliveredAt()).isNull();
    }

    @Test
    void executedConfirmationIsIdempotent() {
        ChannelAction action = response();
        assertThat(action.confirmExecuted(NOW)).isEqualTo(ConfirmationResult.APPLIED);
        assertThat(action.state()).isEqualTo(ChannelActionState.EXECUTED);
        assertThat(action.finalizedAt()).isEqualTo(NOW);
        assertThat(action.confirmExecuted(NOW.plusSeconds(9)))
                .isEqualTo(ConfirmationResult.REPEATED);
        assertThat(action.finalizedAt()).isEqualTo(NOW);
    }

    @Test
    void divergentConfirmationOfFinalStateIsIgnored() {
        ChannelAction action = response();
        action.confirmExecuted(NOW);
        assertThat(action.confirmFailure("TELEGRAM_ERROR", NOW))
                .isEqualTo(ConfirmationResult.DIVERGENT);
        assertThat(action.state()).isEqualTo(ChannelActionState.EXECUTED);
        assertThat(action.reasonCode()).isNull();
    }

    @Test
    void failureConfirmationIsIdempotentAndDivergentWhenCodeChanges() {
        ChannelAction action = response();
        assertThat(action.confirmFailure("CHAT_UNAVAILABLE", NOW))
                .isEqualTo(ConfirmationResult.APPLIED);
        assertThat(action.state()).isEqualTo(ChannelActionState.FAILED);
        assertThat(action.reasonCode()).isEqualTo("CHAT_UNAVAILABLE");
        assertThat(action.confirmFailure("CHAT_UNAVAILABLE", NOW.plusSeconds(1)))
                .isEqualTo(ConfirmationResult.REPEATED);
        assertThat(action.confirmFailure("TELEGRAM_ERROR", NOW.plusSeconds(1)))
                .isEqualTo(ConfirmationResult.DIVERGENT);
        assertThat(action.confirmExecuted(NOW.plusSeconds(1)))
                .isEqualTo(ConfirmationResult.DIVERGENT);
        assertThat(action.reasonCode()).isEqualTo("CHAT_UNAVAILABLE");
    }

    @Test
    void confirmationArrivingBeforeAcceptGoesFromPendingToFinal() {
        ChannelAction action = proactive(ChannelActionType.MESSAGE, null);
        assertThat(action.confirmFailure("RATE_LIMITED", NOW))
                .isEqualTo(ConfirmationResult.APPLIED);
        assertThat(action.state()).isEqualTo(ChannelActionState.FAILED);
        assertThat(action.nextAttemptAt()).isNull();
    }

    @Test
    void confirmationOfActionFinalizedForOtherReasonIsDivergent() {
        ChannelAction discarded = proactive(ChannelActionType.MESSAGE, null);
        discarded.discard(NOW);
        assertThat(discarded.confirmExecuted(NOW)).isEqualTo(ConfirmationResult.DIVERGENT);
        assertThat(discarded.confirmFailure("X", NOW)).isEqualTo(ConfirmationResult.DIVERGENT);
        assertThat(discarded.state()).isEqualTo(ChannelActionState.DISCARDED);
    }

    @Test
    void confirmFailureRequiresCode() {
        assertThatThrownBy(() -> response().confirmFailure(null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void discardOnlyValidWhenPending() {
        ChannelAction pending = proactive(ChannelActionType.MESSAGE, null);
        pending.discard(NOW);
        assertThat(pending.state()).isEqualTo(ChannelActionState.DISCARDED);
        assertThat(pending.reasonCode()).isEqualTo(ChannelAction.LINK_UNDONE_CODE);

        ChannelAction delivered = response();
        delivered.discard(NOW);
        assertThat(delivered.state()).isEqualTo(ChannelActionState.DELIVERED);
    }

    @Test
    void expiresOnlyWhenNotFinalizedAndDeadlinePassed() {
        ChannelAction action = proactive(ChannelActionType.MESSAGE, null);
        action.expire(NOW.plusSeconds(10));
        assertThat(action.state()).isEqualTo(ChannelActionState.PENDING);
        assertThat(action.expired(NOW.plus(Duration.ofHours(24)))).isTrue();
        action.expire(NOW.plus(Duration.ofHours(24)));
        assertThat(action.state()).isEqualTo(ChannelActionState.PERMANENTLY_FAILED);
        assertThat(action.expired(NOW.plus(Duration.ofHours(48)))).isFalse();
        action.expire(NOW.plus(Duration.ofHours(48)));
        assertThat(action.state()).isEqualTo(ChannelActionState.PERMANENTLY_FAILED);
    }

    @Test
    void deliveryFailureNeverSchedulesAfterDeadline() {
        ChannelAction action = proactive(ChannelActionType.MESSAGE, null);
        Instant deadline = NOW.plus(Duration.ofHours(24));
        action.registerDeliveryFailure(deadline.minusSeconds(10), policy);
        assertThat(action.state()).isEqualTo(ChannelActionState.PENDING);
        assertThat(action.nextAttemptAt()).isEqualTo(deadline);

        action.expire(deadline);
        assertThat(action.state()).isEqualTo(ChannelActionState.PERMANENTLY_FAILED);
        assertThat(action.reasonCode()).isEqualTo(ChannelAction.DEADLINE_EXPIRED_CODE);
        assertThat(action.finalizedAt()).isEqualTo(deadline);
    }

    @Test
    void deliveryFailureBeforeDeadlineKeepsPolicyWait() {
        ChannelAction action = proactive(ChannelActionType.MESSAGE, null);
        action.registerDeliveryFailure(NOW, policy);
        assertThat(action.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void deliveredWithoutConfirmationExpiresAtDeadlineWithOwnCode() {
        ChannelAction action = response();
        Instant deadline = NOW.plus(Duration.ofHours(24));
        assertThat(action.expireWithoutConfirmation(deadline.minusSeconds(1))).isFalse();
        assertThat(action.state()).isEqualTo(ChannelActionState.DELIVERED);

        assertThat(action.expireWithoutConfirmation(deadline)).isTrue();
        assertThat(action.state()).isEqualTo(ChannelActionState.PERMANENTLY_FAILED);
        assertThat(action.reasonCode()).isEqualTo(ChannelAction.CODE_WITHOUT_CONFIRMATION);
        assertThat(action.finalizedAt()).isEqualTo(deadline);
        assertThat(action.nextAttemptAt()).isNull();

        action.resetPayload();
        assertThat(action.payload()).isNull();
        assertThat(action.compensation()).isNull();
    }

    @Test
    void expireWithoutConfirmationOnlyActsOnDelivered() {
        Instant muchLater = NOW.plus(Duration.ofDays(3));
        ChannelAction pending = proactive(ChannelActionType.MESSAGE, null);
        assertThat(pending.expireWithoutConfirmation(muchLater)).isFalse();
        assertThat(pending.state()).isEqualTo(ChannelActionState.PENDING);

        ChannelAction executed = response();
        executed.confirmExecuted(NOW);
        assertThat(executed.expireWithoutConfirmation(muchLater)).isFalse();
        assertThat(executed.state()).isEqualTo(ChannelActionState.EXECUTED);
        assertThat(executed.reasonCode()).isNull();
    }

    @Test
    void lateConfirmationOfExpiredActionIsDivergent() {
        ChannelAction action = response();
        action.expireWithoutConfirmation(NOW.plus(Duration.ofHours(24)));
        assertThat(action.confirmExecuted(NOW.plus(Duration.ofHours(25))))
                .isEqualTo(ConfirmationResult.DIVERGENT);
        assertThat(action.reasonCode()).isEqualTo(ChannelAction.CODE_WITHOUT_CONFIRMATION);
    }

    @Test
    void editCompensationBecomesMessageInSameChat() {
        ChannelAction edit = proactive(ChannelActionType.EDIT, "{\"text\":\"alternativa\"}");
        assertThat(edit.hasCompensation()).isTrue();
        assertThat(edit.compensationId()).isEqualTo("p:result:x:0-c");
        ChannelAction compensation = edit.createCompensation(NOW, policy);
        assertThat(compensation.id()).isEqualTo("p:result:x:0-c");
        assertThat(compensation.batchId()).isEqualTo("c:p:result:x:0");
        assertThat(compensation.origin()).isEqualTo(ChannelActionOrigin.COMPENSATION);
        assertThat(compensation.type()).isEqualTo(ChannelActionType.MESSAGE);
        assertThat(compensation.payload()).isEqualTo("{\"text\":\"alternativa\"}");
        assertThat(compensation.chatId()).isEqualTo(10L);
        assertThat(compensation.teacherId()).isEqualTo(TEACHER);
        assertThat(compensation.state()).isEqualTo(ChannelActionState.PENDING);
        assertThat(compensation.hasCompensation()).isFalse();
    }

    @Test
    void documentCompensatesOnlyForUnavailableDocumentAndEditOnlyForNonEditableMessage() {
        ChannelAction document = proactive(ChannelActionType.DOCUMENT, "{\"text\":\"aviso\"}");
        assertThat(document.compensatesBy("DOCUMENT_UNAVAILABLE")).isTrue();
        assertThat(document.compensatesBy("MESSAGE_NOT_EDITABLE")).isFalse();
        assertThat(document.compensatesBy("TELEGRAM_ERROR")).isFalse();

        ChannelAction edit = proactive(ChannelActionType.EDIT, "{\"text\":\"x\"}");
        assertThat(edit.compensatesBy("MESSAGE_NOT_EDITABLE")).isTrue();
        assertThat(edit.compensatesBy("DOCUMENT_UNAVAILABLE")).isFalse();

        assertThat(
                        proactive(ChannelActionType.DOCUMENT, null)
                                .compensatesBy("DOCUMENT_UNAVAILABLE"))
                .isFalse();
        assertThat(document.createCompensation(NOW, policy).compensatesBy("DOCUMENT_UNAVAILABLE"))
                .isFalse();
    }

    @Test
    void documentCompensationBecomesMessageInSameChatWithoutBringingDocument() {
        ChannelAction document =
                ChannelAction.create(
                        "p:result:x:3",
                        "p:result:x",
                        ChannelActionOrigin.PROACTIVE,
                        null,
                        10L,
                        TEACHER,
                        JOB,
                        ChannelActionType.DOCUMENT,
                        "{\"documentId\":\"abc\",\"fileName\":\"a.pdf\"}",
                        "{\"type\":\"MESSAGE\",\"chatId\":10,\"text\":\"Aviso\"}",
                        NOW,
                        policy);
        ChannelAction compensation = document.createCompensation(NOW, policy);
        assertThat(compensation.id()).isEqualTo("p:result:x:3-c");
        assertThat(compensation.batchId()).isEqualTo("c:p:result:x:3");
        assertThat(compensation.origin()).isEqualTo(ChannelActionOrigin.COMPENSATION);
        assertThat(compensation.type()).isEqualTo(ChannelActionType.MESSAGE);
        assertThat(compensation.payload()).doesNotContain("documentId", "a.pdf");
        assertThat(compensation.chatId()).isEqualTo(10L);
        assertThat(compensation.teacherId()).isEqualTo(TEACHER);
        assertThat(compensation.state()).isEqualTo(ChannelActionState.PENDING);
    }

    @Test
    void stepCompensationRemainsStep() {
        ChannelAction step = proactive(ChannelActionType.STEP_MESSAGE, "{\"text\":\"etapa\"}");
        assertThat(step.createCompensation(NOW, policy).type())
                .isEqualTo(ChannelActionType.STEP_MESSAGE);
    }

    @Test
    void actionWithoutCompensationDoesNotCreate() {
        ChannelAction action = proactive(ChannelActionType.EDIT, null);
        assertThat(action.hasCompensation()).isFalse();
        assertThatThrownBy(() -> action.createCompensation(NOW, policy))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resetPayloadDeletesOContent() {
        ChannelAction action = proactive(ChannelActionType.EDIT, "{}");
        action.resetPayload();
        assertThat(action.payload()).isNull();
        assertThat(action.compensation()).isNull();
    }

    @Test
    void versionIsControlledByRepository() {
        ChannelAction action = response();
        action.defineVersion(4);
        assertThat(action.version()).isEqualTo(4);
    }

    @Test
    void finalStates() {
        assertThat(ChannelActionState.PENDING.finalized()).isFalse();
        assertThat(ChannelActionState.DELIVERED.finalized()).isFalse();
        for (ChannelActionState final_ :
                new ChannelActionState[] {
                    ChannelActionState.EXECUTED,
                    ChannelActionState.FAILED,
                    ChannelActionState.PERMANENTLY_FAILED,
                    ChannelActionState.DISCARDED
                }) {
            assertThat(final_.finalized()).isTrue();
        }
    }
}
