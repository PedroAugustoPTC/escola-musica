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
                "p:resultado:x:0",
                "p:resultado:x",
                ChannelActionOrigin.PROATIVA,
                null,
                10L,
                TEACHER,
                JOB,
                type,
                "{\"texto\":\"a\"}",
                compensation,
                NOW,
                policy);
    }

    private ChannelAction response() {
        return ChannelAction.create(
                "u1-0",
                "u1",
                ChannelActionOrigin.RESPOSTA_UPDATE,
                1L,
                10L,
                null,
                null,
                ChannelActionType.MENSAGEM,
                "{}",
                null,
                NOW,
                policy);
    }

    @Test
    void replyToUpdateStartsDelivered() {
        ChannelAction action = response();
        assertThat(action.state()).isEqualTo(ChannelActionState.ENTREGUE);
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
                                        ChannelActionOrigin.RESPOSTA_UPDATE,
                                        null,
                                        10L,
                                        null,
                                        null,
                                        ChannelActionType.MENSAGEM,
                                        "{}",
                                        null,
                                        NOW,
                                        policy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void proactiveStartsDueAndPending() {
        ChannelAction action = proactive(ChannelActionType.MENSAGEM, null);
        assertThat(action.state()).isEqualTo(ChannelActionState.PENDENTE);
        assertThat(action.nextAttemptAt()).isEqualTo(NOW);
        assertThat(action.deliveredAt()).isNull();
        assertThat(action.teacherId()).isEqualTo(TEACHER);
        assertThat(action.jobId()).isEqualTo(JOB);
        assertThat(action.version()).isZero();
    }

    @Test
    void deliveryFailureReschedulesAccordingToPolicy() {
        ChannelAction action = proactive(ChannelActionType.MENSAGEM, null);
        action.registerDeliveryFailure(NOW, policy);
        assertThat(action.state()).isEqualTo(ChannelActionState.PENDENTE);
        assertThat(action.attempts()).isEqualTo(1);
        assertThat(action.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        action.registerDeliveryFailure(NOW.plusSeconds(30), policy);
        assertThat(action.attempts()).isEqualTo(2);
        assertThat(action.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30 + 60));
    }

    @Test
    void deliveryFailureAfterDeadlineIsPermanentFailure() {
        ChannelAction action = proactive(ChannelActionType.MENSAGEM, null);
        Instant afterDeadline = NOW.plus(Duration.ofHours(24));
        action.registerDeliveryFailure(afterDeadline, policy);
        assertThat(action.state()).isEqualTo(ChannelActionState.FALHA_DEFINITIVA);
        assertThat(action.reasonCode()).isEqualTo(ChannelAction.DEADLINE_EXPIRED_CODE);
        assertThat(action.finalizedAt()).isEqualTo(afterDeadline);
        assertThat(action.nextAttemptAt()).isNull();
    }

    @Test
    void deliveryFailureDoesNotTouchActionThatIsNotPending() {
        ChannelAction action = response();
        action.registerDeliveryFailure(NOW, policy);
        assertThat(action.state()).isEqualTo(ChannelActionState.ENTREGUE);
        assertThat(action.attempts()).isZero();
    }

    @Test
    void reservationPushesNextAttemptOnlyWhenPending() {
        ChannelAction action = proactive(ChannelActionType.MENSAGEM, null);
        action.reserveUntil(NOW.plusSeconds(60));
        assertThat(action.nextAttemptAt()).isEqualTo(NOW.plusSeconds(60));
        action.markDelivered(NOW);
        action.reserveUntil(NOW.plusSeconds(999));
        assertThat(action.nextAttemptAt()).isNull();
    }

    @Test
    void acceptMarksDeliveredAndCounts() {
        ChannelAction action = proactive(ChannelActionType.MENSAGEM, null);
        action.markDelivered(NOW.plusSeconds(5));
        assertThat(action.state()).isEqualTo(ChannelActionState.ENTREGUE);
        assertThat(action.deliveredAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(action.attempts()).isEqualTo(1);
        assertThat(action.nextAttemptAt()).isNull();
    }

    @Test
    void acceptDoesNotTouchAlreadyConfirmedAction() {
        ChannelAction action = proactive(ChannelActionType.MENSAGEM, null);
        action.confirmExecuted(NOW);
        action.markDelivered(NOW.plusSeconds(1));
        assertThat(action.state()).isEqualTo(ChannelActionState.EXECUTADA);
        assertThat(action.deliveredAt()).isNull();
    }

    @Test
    void executedConfirmationIsIdempotent() {
        ChannelAction action = response();
        assertThat(action.confirmExecuted(NOW)).isEqualTo(ConfirmationResult.APLICADA);
        assertThat(action.state()).isEqualTo(ChannelActionState.EXECUTADA);
        assertThat(action.finalizedAt()).isEqualTo(NOW);
        assertThat(action.confirmExecuted(NOW.plusSeconds(9)))
                .isEqualTo(ConfirmationResult.REPETIDA);
        assertThat(action.finalizedAt()).isEqualTo(NOW);
    }

    @Test
    void divergentConfirmationOfFinalStateIsIgnored() {
        ChannelAction action = response();
        action.confirmExecuted(NOW);
        assertThat(action.confirmFailure("ERRO_TELEGRAM", NOW))
                .isEqualTo(ConfirmationResult.DIVERGENTE);
        assertThat(action.state()).isEqualTo(ChannelActionState.EXECUTADA);
        assertThat(action.reasonCode()).isNull();
    }

    @Test
    void failureConfirmationIsIdempotentAndDivergentWhenCodeChanges() {
        ChannelAction action = response();
        assertThat(action.confirmFailure("CHAT_INDISPONIVEL", NOW))
                .isEqualTo(ConfirmationResult.APLICADA);
        assertThat(action.state()).isEqualTo(ChannelActionState.FALHOU);
        assertThat(action.reasonCode()).isEqualTo("CHAT_INDISPONIVEL");
        assertThat(action.confirmFailure("CHAT_INDISPONIVEL", NOW.plusSeconds(1)))
                .isEqualTo(ConfirmationResult.REPETIDA);
        assertThat(action.confirmFailure("ERRO_TELEGRAM", NOW.plusSeconds(1)))
                .isEqualTo(ConfirmationResult.DIVERGENTE);
        assertThat(action.confirmExecuted(NOW.plusSeconds(1)))
                .isEqualTo(ConfirmationResult.DIVERGENTE);
        assertThat(action.reasonCode()).isEqualTo("CHAT_INDISPONIVEL");
    }

    @Test
    void confirmationArrivingBeforeAcceptGoesFromPendingToFinal() {
        ChannelAction action = proactive(ChannelActionType.MENSAGEM, null);
        assertThat(action.confirmFailure("LIMITE_DE_TAXA", NOW))
                .isEqualTo(ConfirmationResult.APLICADA);
        assertThat(action.state()).isEqualTo(ChannelActionState.FALHOU);
        assertThat(action.nextAttemptAt()).isNull();
    }

    @Test
    void confirmationOfActionFinalizedForOtherReasonIsDivergent() {
        ChannelAction discarded = proactive(ChannelActionType.MENSAGEM, null);
        discarded.discard(NOW);
        assertThat(discarded.confirmExecuted(NOW)).isEqualTo(ConfirmationResult.DIVERGENTE);
        assertThat(discarded.confirmFailure("X", NOW)).isEqualTo(ConfirmationResult.DIVERGENTE);
        assertThat(discarded.state()).isEqualTo(ChannelActionState.DESCARTADA);
    }

    @Test
    void confirmFailureRequiresCode() {
        assertThatThrownBy(() -> response().confirmFailure(null, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void discardOnlyValidWhenPending() {
        ChannelAction pending = proactive(ChannelActionType.MENSAGEM, null);
        pending.discard(NOW);
        assertThat(pending.state()).isEqualTo(ChannelActionState.DESCARTADA);
        assertThat(pending.reasonCode()).isEqualTo(ChannelAction.LINK_UNDONE_CODE);

        ChannelAction delivered = response();
        delivered.discard(NOW);
        assertThat(delivered.state()).isEqualTo(ChannelActionState.ENTREGUE);
    }

    @Test
    void expiresOnlyWhenNotFinalizedAndDeadlinePassed() {
        ChannelAction action = proactive(ChannelActionType.MENSAGEM, null);
        action.expire(NOW.plusSeconds(10));
        assertThat(action.state()).isEqualTo(ChannelActionState.PENDENTE);
        assertThat(action.expired(NOW.plus(Duration.ofHours(24)))).isTrue();
        action.expire(NOW.plus(Duration.ofHours(24)));
        assertThat(action.state()).isEqualTo(ChannelActionState.FALHA_DEFINITIVA);
        assertThat(action.expired(NOW.plus(Duration.ofHours(48)))).isFalse();
        action.expire(NOW.plus(Duration.ofHours(48)));
        assertThat(action.state()).isEqualTo(ChannelActionState.FALHA_DEFINITIVA);
    }

    @Test
    void deliveryFailureNeverSchedulesAfterDeadline() {
        ChannelAction action = proactive(ChannelActionType.MENSAGEM, null);
        Instant deadline = NOW.plus(Duration.ofHours(24));
        action.registerDeliveryFailure(deadline.minusSeconds(10), policy);
        assertThat(action.state()).isEqualTo(ChannelActionState.PENDENTE);
        assertThat(action.nextAttemptAt()).isEqualTo(deadline);

        action.expire(deadline);
        assertThat(action.state()).isEqualTo(ChannelActionState.FALHA_DEFINITIVA);
        assertThat(action.reasonCode()).isEqualTo(ChannelAction.DEADLINE_EXPIRED_CODE);
        assertThat(action.finalizedAt()).isEqualTo(deadline);
    }

    @Test
    void deliveryFailureBeforeDeadlineKeepsPolicyWait() {
        ChannelAction action = proactive(ChannelActionType.MENSAGEM, null);
        action.registerDeliveryFailure(NOW, policy);
        assertThat(action.nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void deliveredWithoutConfirmationExpiresAtDeadlineWithOwnCode() {
        ChannelAction action = response();
        Instant deadline = NOW.plus(Duration.ofHours(24));
        assertThat(action.expireWithoutConfirmation(deadline.minusSeconds(1))).isFalse();
        assertThat(action.state()).isEqualTo(ChannelActionState.ENTREGUE);

        assertThat(action.expireWithoutConfirmation(deadline)).isTrue();
        assertThat(action.state()).isEqualTo(ChannelActionState.FALHA_DEFINITIVA);
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
        ChannelAction pending = proactive(ChannelActionType.MENSAGEM, null);
        assertThat(pending.expireWithoutConfirmation(muchLater)).isFalse();
        assertThat(pending.state()).isEqualTo(ChannelActionState.PENDENTE);

        ChannelAction executed = response();
        executed.confirmExecuted(NOW);
        assertThat(executed.expireWithoutConfirmation(muchLater)).isFalse();
        assertThat(executed.state()).isEqualTo(ChannelActionState.EXECUTADA);
        assertThat(executed.reasonCode()).isNull();
    }

    @Test
    void lateConfirmationOfExpiredActionIsDivergent() {
        ChannelAction action = response();
        action.expireWithoutConfirmation(NOW.plus(Duration.ofHours(24)));
        assertThat(action.confirmExecuted(NOW.plus(Duration.ofHours(25))))
                .isEqualTo(ConfirmationResult.DIVERGENTE);
        assertThat(action.reasonCode()).isEqualTo(ChannelAction.CODE_WITHOUT_CONFIRMATION);
    }

    @Test
    void editCompensationBecomesMessageInSameChat() {
        ChannelAction edit = proactive(ChannelActionType.EDICAO, "{\"texto\":\"alternativa\"}");
        assertThat(edit.hasCompensation()).isTrue();
        assertThat(edit.compensationId()).isEqualTo("p:resultado:x:0-c");
        ChannelAction compensation = edit.createCompensation(NOW, policy);
        assertThat(compensation.id()).isEqualTo("p:resultado:x:0-c");
        assertThat(compensation.batchId()).isEqualTo("c:p:resultado:x:0");
        assertThat(compensation.origin()).isEqualTo(ChannelActionOrigin.COMPENSACAO);
        assertThat(compensation.type()).isEqualTo(ChannelActionType.MENSAGEM);
        assertThat(compensation.payload()).isEqualTo("{\"texto\":\"alternativa\"}");
        assertThat(compensation.chatId()).isEqualTo(10L);
        assertThat(compensation.teacherId()).isEqualTo(TEACHER);
        assertThat(compensation.state()).isEqualTo(ChannelActionState.PENDENTE);
        assertThat(compensation.hasCompensation()).isFalse();
    }

    @Test
    void documentCompensatesOnlyForUnavailableDocumentAndEditOnlyForNonEditableMessage() {
        ChannelAction document = proactive(ChannelActionType.DOCUMENTO, "{\"texto\":\"aviso\"}");
        assertThat(document.compensatesBy("DOCUMENTO_INDISPONIVEL")).isTrue();
        assertThat(document.compensatesBy("MENSAGEM_NAO_EDITAVEL")).isFalse();
        assertThat(document.compensatesBy("ERRO_TELEGRAM")).isFalse();

        ChannelAction edit = proactive(ChannelActionType.EDICAO, "{\"texto\":\"x\"}");
        assertThat(edit.compensatesBy("MENSAGEM_NAO_EDITAVEL")).isTrue();
        assertThat(edit.compensatesBy("DOCUMENTO_INDISPONIVEL")).isFalse();

        assertThat(
                        proactive(ChannelActionType.DOCUMENTO, null)
                                .compensatesBy("DOCUMENTO_INDISPONIVEL"))
                .isFalse();
        assertThat(document.createCompensation(NOW, policy).compensatesBy("DOCUMENTO_INDISPONIVEL"))
                .isFalse();
    }

    @Test
    void documentCompensationBecomesMessageInSameChatWithoutBringingDocument() {
        ChannelAction document =
                ChannelAction.create(
                        "p:resultado:x:3",
                        "p:resultado:x",
                        ChannelActionOrigin.PROATIVA,
                        null,
                        10L,
                        TEACHER,
                        JOB,
                        ChannelActionType.DOCUMENTO,
                        "{\"documentoId\":\"abc\",\"nomeArquivo\":\"a.pdf\"}",
                        "{\"tipo\":\"MENSAGEM\",\"chatId\":10,\"texto\":\"Aviso\"}",
                        NOW,
                        policy);
        ChannelAction compensation = document.createCompensation(NOW, policy);
        assertThat(compensation.id()).isEqualTo("p:resultado:x:3-c");
        assertThat(compensation.batchId()).isEqualTo("c:p:resultado:x:3");
        assertThat(compensation.origin()).isEqualTo(ChannelActionOrigin.COMPENSACAO);
        assertThat(compensation.type()).isEqualTo(ChannelActionType.MENSAGEM);
        assertThat(compensation.payload()).doesNotContain("documentoId", "a.pdf");
        assertThat(compensation.chatId()).isEqualTo(10L);
        assertThat(compensation.teacherId()).isEqualTo(TEACHER);
        assertThat(compensation.state()).isEqualTo(ChannelActionState.PENDENTE);
    }

    @Test
    void stepCompensationRemainsStep() {
        ChannelAction step = proactive(ChannelActionType.MENSAGEM_ETAPA, "{\"texto\":\"etapa\"}");
        assertThat(step.createCompensation(NOW, policy).type())
                .isEqualTo(ChannelActionType.MENSAGEM_ETAPA);
    }

    @Test
    void actionWithoutCompensationDoesNotCreate() {
        ChannelAction action = proactive(ChannelActionType.EDICAO, null);
        assertThat(action.hasCompensation()).isFalse();
        assertThatThrownBy(() -> action.createCompensation(NOW, policy))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resetPayloadDeletesOContent() {
        ChannelAction action = proactive(ChannelActionType.EDICAO, "{}");
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
        assertThat(ChannelActionState.PENDENTE.finalized()).isFalse();
        assertThat(ChannelActionState.ENTREGUE.finalized()).isFalse();
        for (ChannelActionState final_ :
                new ChannelActionState[] {
                    ChannelActionState.EXECUTADA,
                    ChannelActionState.FALHOU,
                    ChannelActionState.FALHA_DEFINITIVA,
                    ChannelActionState.DESCARTADA
                }) {
            assertThat(final_.finalized()).isTrue();
        }
    }
}
