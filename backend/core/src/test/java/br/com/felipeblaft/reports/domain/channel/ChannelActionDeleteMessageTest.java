package br.com.felipeblaft.reports.domain.channel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryChannelActionRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryJobQueue;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase.Response;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase.Result;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase.Status;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase.NewAction;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ação {@code DELETE_MESSAGE} (feature 005, US4, FR-036): apagar a mensagem em que o professor
 * digitou o CPF. Em qualquer falha final (Telegram recusou, chat indisponível, receptor antigo,
 * prazo vencido), o backend gera a compensação, uma só: um aviso ({@code
 * CPF_MESSAGE_NOT_DELETED}) no mesmo chat. O orquestrador só classifica a falha; a decisão de
 * compensar é do núcleo.
 */
class ChannelActionDeleteMessageTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UUID TEACHER = UUID.randomUUID();
    private static final long CHAT = 4567L;
    private static final String DELETE_PAYLOAD =
            "{\"type\":\"DELETE_MESSAGE\",\"chatId\":4567,\"messageId\":89}";
    private static final String NOTICE =
            "{\"type\":\"MESSAGE\",\"chatId\":4567,\"text\":\"CPF_MESSAGE_NOT_DELETED\"}";

    private final RetryPolicy policy =
            new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));

    private ChannelAction deletion(String compensation) {
        return ChannelAction.create(
                "u123-0",
                "u123",
                ChannelActionOrigin.UPDATE_REPLY,
                123L,
                CHAT,
                TEACHER,
                null,
                ChannelActionType.DELETE_MESSAGE,
                DELETE_PAYLOAD,
                compensation,
                NOW,
                policy);
    }

    // ---- domínio

    @Test
    void deletionCompensatesByAnyFinalFailureCode() {
        ChannelAction deletion = deletion(NOTICE);

        assertThat(deletion.hasCompensation()).isTrue();
        assertThat(deletion.compensatesBy(ChannelAction.MESSAGE_NOT_DELETABLE_CODE)).isTrue();
        for (String code :
                List.of(
                        "MESSAGE_NOT_DELETABLE",
                        "MESSAGE_NOT_EDITABLE",
                        "DOCUMENT_UNAVAILABLE",
                        "ACTION_NOT_SUPPORTED",
                        "CHAT_UNAVAILABLE",
                        "RATE_LIMITED",
                        "TELEGRAM_ERROR")) {
            assertThat(deletion.compensatesBy(code)).as(code).isTrue();
        }
        assertThat(deletion.compensatesBy(null)).isFalse();
        assertThat(deletion.compensatesBy(" ")).isFalse();
    }

    @Test
    void deletionThatExpiredCompensatesOnlyWhenFinalAndWithAlternative() {
        ChannelAction pending = deletion(NOTICE);
        assertThat(pending.compensatesPermanentFailure()).isFalse();

        ChannelAction expired = deletion(NOTICE);
        expired.expire(NOW.plus(Duration.ofHours(25)));
        assertThat(expired.state()).isEqualTo(ChannelActionState.PERMANENTLY_FAILED);
        assertThat(expired.compensatesPermanentFailure()).isTrue();

        ChannelAction executed = deletion(NOTICE);
        executed.confirmExecuted(NOW);
        assertThat(executed.compensatesPermanentFailure()).isFalse();

        ChannelAction withoutAlternative = deletion(null);
        withoutAlternative.expire(NOW.plus(Duration.ofHours(25)));
        assertThat(withoutAlternative.compensatesPermanentFailure()).isFalse();

        ChannelAction message =
                ChannelAction.create(
                        "p:x:0",
                        "p:x",
                        ChannelActionOrigin.PROACTIVE,
                        null,
                        CHAT,
                        TEACHER,
                        null,
                        ChannelActionType.MESSAGE,
                        "{}",
                        NOTICE,
                        NOW,
                        policy);
        message.expire(NOW.plus(Duration.ofHours(25)));
        assertThat(message.compensatesPermanentFailure()).isFalse();
    }

    @Test
    void deletionWithoutAlternativeNeverCompensates() {
        ChannelAction deletion = deletion(null);

        assertThat(deletion.hasCompensation()).isFalse();
        assertThat(deletion.compensatesBy("MESSAGE_NOT_DELETABLE")).isFalse();
    }

    @Test
    void otherTypesDoNotCompensateByMessageNotDeletable() {
        for (ChannelActionType type :
                List.of(
                        ChannelActionType.EDIT,
                        ChannelActionType.STEP_MESSAGE,
                        ChannelActionType.MESSAGE,
                        ChannelActionType.DOCUMENT,
                        ChannelActionType.CALLBACK_REPLY)) {
            ChannelAction action =
                    ChannelAction.create(
                            "p:x:0",
                            "p:x",
                            ChannelActionOrigin.PROACTIVE,
                            null,
                            CHAT,
                            TEACHER,
                            null,
                            type,
                            "{}",
                            NOTICE,
                            NOW,
                            policy);
            assertThat(action.compensatesBy("MESSAGE_NOT_DELETABLE")).as(type.name()).isFalse();
        }
    }

    @Test
    void compensationOfDeletionIsAMessageInTheSameChatWithTheSameTeacher() {
        ChannelAction compensation = deletion(NOTICE).createCompensation(NOW, policy);

        assertThat(compensation.id()).isEqualTo("u123-0-c");
        assertThat(compensation.batchId()).isEqualTo("c:u123-0");
        assertThat(compensation.origin()).isEqualTo(ChannelActionOrigin.COMPENSATION);
        assertThat(compensation.type()).isEqualTo(ChannelActionType.MESSAGE);
        assertThat(compensation.payload()).isEqualTo(NOTICE);
        assertThat(compensation.chatId()).isEqualTo(CHAT);
        assertThat(compensation.teacherId()).isEqualTo(TEACHER);
        assertThat(compensation.state()).isEqualTo(ChannelActionState.PENDING);
        assertThat(compensation.hasCompensation()).isFalse();
    }

    @Test
    void deletionPayloadCarriesNoTextAndRetentionErasesIt() {
        ChannelAction deletion = deletion(NOTICE);
        assertThat(deletion.payload()).doesNotContain("text");

        deletion.resetPayload();

        assertThat(deletion.payload()).isNull();
        assertThat(deletion.compensation()).isNull();
    }

    // ---- caso de uso (a decisão de compensar é do núcleo)

    @Test
    void failureMessageNotDeletableGeneratesOneCompensationAndFurtherRepetitionsAddNothing() {
        FixedClock clock = new FixedClock();
        InMemoryChannelActionRepository repository = new InMemoryChannelActionRepository();
        SaveChannelActionsUseCase save = new SaveChannelActionsUseCase(repository, clock, policy);
        ConfirmChannelActionsExecutionUseCase confirm =
                new ConfirmChannelActionsExecutionUseCase(
                        repository, new InMemoryJobQueue(), clock, policy);
        save.run(
                "u123",
                ChannelActionOrigin.UPDATE_REPLY,
                123L,
                List.of(
                        new NewAction(
                                "u123-0",
                                ChannelActionType.DELETE_MESSAGE,
                                CHAT,
                                TEACHER,
                                null,
                                DELETE_PAYLOAD,
                                NOTICE)));

        Result failure = new Result("u123-0", Status.FAILED, "MESSAGE_NOT_DELETABLE", null);
        Response first = confirm.run(List.of(failure));
        Response repeated = confirm.run(List.of(failure));

        assertThat(first.accepted()).isEqualTo(1);
        assertThat(repeated.accepted()).isEqualTo(1);
        ChannelAction stored = repository.stored("u123-0");
        assertThat(stored.state()).isEqualTo(ChannelActionState.FAILED);
        assertThat(stored.reasonCode()).isEqualTo("MESSAGE_NOT_DELETABLE");
        ChannelAction compensation = repository.stored("u123-0-c");
        assertThat(compensation.type()).isEqualTo(ChannelActionType.MESSAGE);
        assertThat(compensation.payload()).isEqualTo(NOTICE);
        assertThat(compensation.state()).isEqualTo(ChannelActionState.PENDING);
    }

    @Test
    void codeMessageNotDeletableIsKnownAndIsNotRewrittenToTelegramError() {
        assertThat(ConfirmChannelActionsExecutionUseCase.normalize("MESSAGE_NOT_DELETABLE"))
                .isEqualTo("MESSAGE_NOT_DELETABLE");
        assertThat(ConfirmChannelActionsExecutionUseCase.normalize("ANOTHER_NEW_CODE"))
                .isEqualTo("TELEGRAM_ERROR");
    }

    @Test
    void everyFinalFailureCodeOfTheDeletionIsCompensatedExactlyOnce() {
        for (String code :
                List.of("CHAT_UNAVAILABLE", "RATE_LIMITED", "TELEGRAM_ERROR", "SOMETHING_NEW")) {
            FixedClock clock = new FixedClock();
            InMemoryChannelActionRepository repository = new InMemoryChannelActionRepository();
            SaveChannelActionsUseCase save =
                    new SaveChannelActionsUseCase(repository, clock, policy);
            ConfirmChannelActionsExecutionUseCase confirm =
                    new ConfirmChannelActionsExecutionUseCase(
                            repository, new InMemoryJobQueue(), clock, policy);
            save.run(
                    "u200",
                    ChannelActionOrigin.UPDATE_REPLY,
                    200L,
                    List.of(
                            new NewAction(
                                    "u200-0",
                                    ChannelActionType.DELETE_MESSAGE,
                                    CHAT,
                                    TEACHER,
                                    null,
                                    DELETE_PAYLOAD,
                                    NOTICE)));

            Result failure = new Result("u200-0", Status.FAILED, code, null);
            confirm.run(List.of(failure));
            confirm.run(List.of(failure));

            assertThat(repository.all()).as(code).hasSize(2);
            assertThat(repository.stored("u200-0-c")).as(code).isNotNull();
            assertThat(repository.stored("u200-0-c").payload()).isEqualTo(NOTICE);
        }
    }

    @Test
    void oldReceiverAnswersActionNotSupportedAndTheDeletionIsCompensated() {
        FixedClock clock = new FixedClock();
        InMemoryChannelActionRepository repository = new InMemoryChannelActionRepository();
        SaveChannelActionsUseCase save = new SaveChannelActionsUseCase(repository, clock, policy);
        ConfirmChannelActionsExecutionUseCase confirm =
                new ConfirmChannelActionsExecutionUseCase(
                        repository, new InMemoryJobQueue(), clock, policy);
        save.run(
                "u124",
                ChannelActionOrigin.UPDATE_REPLY,
                124L,
                List.of(
                        new NewAction(
                                "u124-0",
                                ChannelActionType.DELETE_MESSAGE,
                                CHAT,
                                TEACHER,
                                null,
                                DELETE_PAYLOAD,
                                NOTICE)));

        Response response =
                confirm.run(
                        List.of(new Result("u124-0", Status.FAILED, "ACTION_NOT_SUPPORTED", null)));

        assertThat(response.accepted()).isEqualTo(1);
        assertThat(repository.stored("u124-0").state()).isEqualTo(ChannelActionState.FAILED);
        assertThat(repository.stored("u124-0").reasonCode()).isEqualTo("ACTION_NOT_SUPPORTED");
        assertThat(repository.stored("u124-0-c")).isNotNull();
        assertThat(repository.stored("u124-0-c").payload()).isEqualTo(NOTICE);
    }

    @Test
    void successfulDeletionIsExecutedWithoutCompensation() {
        FixedClock clock = new FixedClock();
        InMemoryChannelActionRepository repository = new InMemoryChannelActionRepository();
        SaveChannelActionsUseCase save = new SaveChannelActionsUseCase(repository, clock, policy);
        ConfirmChannelActionsExecutionUseCase confirm =
                new ConfirmChannelActionsExecutionUseCase(
                        repository, new InMemoryJobQueue(), clock, policy);
        save.run(
                "u125",
                ChannelActionOrigin.UPDATE_REPLY,
                125L,
                List.of(
                        new NewAction(
                                "u125-0",
                                ChannelActionType.DELETE_MESSAGE,
                                CHAT,
                                TEACHER,
                                null,
                                DELETE_PAYLOAD,
                                NOTICE)));

        confirm.run(List.of(new Result("u125-0", Status.EXECUTED, null, null)));

        assertThat(repository.stored("u125-0").state()).isEqualTo(ChannelActionState.EXECUTED);
        assertThat(repository.stored("u125-0-c")).isNull();
    }
}
