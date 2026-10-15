package br.com.felipeblaft.reports.usecase.channel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryChannelActionRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryJobQueue;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase.Response;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase.Result;
import br.com.felipeblaft.reports.usecase.channel.ConfirmChannelActionsExecutionUseCase.Status;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase.NewAction;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmChannelActionsExecutionUseCaseTest {

    private final FixedClock clock = new FixedClock();
    private final InMemoryChannelActionRepository repository =
            new InMemoryChannelActionRepository();
    private final InMemoryJobQueue queue = new InMemoryJobQueue();
    private final RetryPolicy policy =
            new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));
    private final SaveChannelActionsUseCase save =
            new SaveChannelActionsUseCase(repository, clock, policy);
    private final ConfirmChannelActionsExecutionUseCase confirm =
            new ConfirmChannelActionsExecutionUseCase(repository, queue, clock, policy);

    private static Result ok(String id) {
        return new Result(id, Status.EXECUTED, null, null);
    }

    private static Result failure(String id, String code) {
        return new Result(id, Status.FAILED, code, null);
    }

    private void response(String... ids) {
        save.run(
                "u1",
                ChannelActionOrigin.UPDATE_REPLY,
                1L,
                java.util.Arrays.stream(ids)
                        .map(
                                id ->
                                        new NewAction(
                                                id,
                                                ChannelActionType.MESSAGE,
                                                1L,
                                                null,
                                                null,
                                                "{\"text\":\"a\"}",
                                                null))
                        .toList());
    }

    private ProcessingJob step(UUID teacher) {
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        teacher,
                        UUID.randomUUID(),
                        null,
                        null,
                        Channel.TELEGRAM,
                        Medium.AUDIO,
                        JobStep.UPLOADING,
                        clock.now());
        queue.enqueue(job);
        return job;
    }

    // ---- caminho básico e idempotência

    @Test
    void executedAndFailedBecomeFinalStatesWithReason() {
        response("u1-0", "u1-1");
        Response response = confirm.run(List.of(ok("u1-0"), failure("u1-1", "CHAT_UNAVAILABLE")));
        assertThat(response.accepted()).isEqualTo(2);
        assertThat(response.ignored()).isEmpty();
        assertThat(response.contractViolation()).isFalse();
        assertThat(repository.stored("u1-0").state()).isEqualTo(ChannelActionState.EXECUTED);
        assertThat(repository.stored("u1-1").state()).isEqualTo(ChannelActionState.FAILED);
        assertThat(repository.stored("u1-1").reasonCode()).isEqualTo("CHAT_UNAVAILABLE");
    }

    @Test
    void repeatedIdenticalConfirmationHasNoEffect() {
        response("u1-0");
        confirm.run(List.of(ok("u1-0")));
        int version = repository.stored("u1-0").version();
        Response second = confirm.run(List.of(ok("u1-0")));
        assertThat(second.accepted()).isEqualTo(1);
        assertThat(repository.stored("u1-0").version()).isEqualTo(version);
    }

    @Test
    void divergentUnknownOrInvalidConfirmationGoesToIgnored() {
        response("u1-0", "u1-1");
        confirm.run(List.of(ok("u1-0")));
        Response response =
                confirm.run(
                        java.util.Arrays.asList(
                                failure("u1-0", "TELEGRAM_ERROR"), // divergente do já registrado
                                ok("u9-9"), // desconhecida
                                new Result("u1-1", Status.FAILED, null, null), // sem motivo
                                new Result("u1-1", null, null, null), // sem situação
                                new Result(null, Status.EXECUTED, null, null),
                                new Result("", Status.EXECUTED, null, null),
                                new Result(
                                        "texto livre com espaços de aluno",
                                        Status.EXECUTED,
                                        null,
                                        null),
                                null,
                                ok("u1-1")));
        assertThat(response.accepted()).isEqualTo(1);
        assertThat(response.ignored()).containsExactly("u1-0", "u9-9", "u1-1", "u1-1");
        assertThat(repository.stored("u1-1").state()).isEqualTo(ChannelActionState.EXECUTED);
    }

    @Test
    void invalidItemDoesNotBringDownTheOthers() {
        response("u1-0", "u1-1");
        Response response =
                confirm.run(List.of(new Result("u1-0", Status.FAILED, " ", null), ok("u1-1")));
        assertThat(response.accepted()).isEqualTo(1);
        assertThat(repository.stored("u1-0").state()).isEqualTo(ChannelActionState.DELIVERED);
        assertThat(repository.stored("u1-1").state()).isEqualTo(ChannelActionState.EXECUTED);
    }

    @Test
    void discardedOrPermanentlyFailedActionIgnoresConfirmation() {
        save.run(
                "p:a",
                ChannelActionOrigin.PROACTIVE,
                null,
                List.of(
                        new NewAction(
                                "p:a:0",
                                ChannelActionType.MESSAGE,
                                1L,
                                UUID.randomUUID(),
                                null,
                                "{}",
                                null),
                        new NewAction(
                                "p:a:1",
                                ChannelActionType.MESSAGE,
                                1L,
                                UUID.randomUUID(),
                                null,
                                "{}",
                                null)));
        repository.stored("p:a:0").discard(clock.now());
        repository.stored("p:a:1").expire(clock.now().plus(Duration.ofHours(30)));
        Response response = confirm.run(List.of(ok("p:a:0"), ok("p:a:1")));
        assertThat(response.accepted()).isZero();
        assertThat(response.ignored()).containsExactly("p:a:0", "p:a:1");
    }

    // ---- código desconhecido

    @Test
    void unknownCodeOrFreeTextBecomesTelegramError() {
        response("u1-0", "u1-1", "u1-2");
        Response response =
                confirm.run(
                        List.of(
                                failure("u1-0", "NEW_CODE_FROM_N8N"),
                                failure("u1-1", "texto livre da Maria"),
                                ok("u1-2")));
        assertThat(response.accepted()).isEqualTo(3);
        assertThat(repository.stored("u1-0").reasonCode()).isEqualTo("TELEGRAM_ERROR");
        assertThat(repository.stored("u1-1").reasonCode()).isEqualTo("TELEGRAM_ERROR");
        assertThat(repository.stored("u1-2").state()).isEqualTo(ChannelActionState.EXECUTED);
    }

    // ---- compensação

    private void editWithCompensation(String id) {
        save.run(
                "p:e",
                ChannelActionOrigin.PROACTIVE,
                null,
                List.of(
                        new NewAction(
                                id,
                                ChannelActionType.EDIT,
                                1L,
                                UUID.randomUUID(),
                                null,
                                "{\"messageId\":5}",
                                "{\"text\":\"mesmo texto\"}")));
    }

    @Test
    void nonEditableEditGeneratesCompensationWithSameContent() {
        editWithCompensation("p:e:0");
        confirm.run(List.of(failure("p:e:0", "MESSAGE_NOT_EDITABLE")));
        ChannelAction compensation = repository.searchById("p:e:0-c").orElseThrow();
        assertThat(compensation.origin()).isEqualTo(ChannelActionOrigin.COMPENSATION);
        assertThat(compensation.state()).isEqualTo(ChannelActionState.PENDING);
        assertThat(compensation.payload()).isEqualTo("{\"text\":\"mesmo texto\"}");
        assertThat(compensation.batchId()).isEqualTo("c:p:e:0");
        // repetir a confirmação não cria outra
        confirm.run(List.of(failure("p:e:0", "MESSAGE_NOT_EDITABLE")));
        assertThat(repository.all()).hasSize(2);
    }

    @Test
    void compensationThatFailsDoesNotGenerateAnother() {
        editWithCompensation("p:e:0");
        confirm.run(List.of(failure("p:e:0", "MESSAGE_NOT_EDITABLE")));
        confirm.run(List.of(failure("p:e:0-c", "MESSAGE_NOT_EDITABLE")));
        assertThat(repository.all()).hasSize(2);
        assertThat(repository.stored("p:e:0-c").state()).isEqualTo(ChannelActionState.FAILED);
    }

    private void documentWithCompensation(String id, String compensation) {
        save.run(
                "p:d",
                ChannelActionOrigin.PROACTIVE,
                null,
                List.of(
                        new NewAction(
                                id,
                                ChannelActionType.DOCUMENT,
                                1L,
                                UUID.randomUUID(),
                                null,
                                "{\"documentId\":\"abc\",\"fileName\":\"a.pdf\"}",
                                compensation)));
    }

    @Test
    void unavailableDocumentGeneratesSingleCompensationWithoutDocument() {
        documentWithCompensation("p:d:0", "{\"type\":\"MESSAGE\",\"text\":\"aviso\"}");
        confirm.run(List.of(failure("p:d:0", "DOCUMENT_UNAVAILABLE")));

        ChannelAction compensation = repository.searchById("p:d:0-c").orElseThrow();
        assertThat(compensation.origin()).isEqualTo(ChannelActionOrigin.COMPENSATION);
        assertThat(compensation.state()).isEqualTo(ChannelActionState.PENDING);
        assertThat(compensation.type()).isEqualTo(ChannelActionType.MESSAGE);
        assertThat(compensation.batchId()).isEqualTo("c:p:d:0");
        assertThat(compensation.payload()).isEqualTo("{\"type\":\"MESSAGE\",\"text\":\"aviso\"}");
        assertThat(compensation.payload()).doesNotContain("documentId");
        assertThat(repository.stored("p:d:0").reasonCode()).isEqualTo("DOCUMENT_UNAVAILABLE");

        // repetir a confirmação não cria outra
        confirm.run(List.of(failure("p:d:0", "DOCUMENT_UNAVAILABLE")));
        assertThat(repository.all()).hasSize(2);
    }

    @Test
    void compensationOfDocumentThatFailsDoesNotGenerateAnother() {
        documentWithCompensation("p:d:0", "{\"text\":\"aviso\"}");
        confirm.run(List.of(failure("p:d:0", "DOCUMENT_UNAVAILABLE")));
        confirm.run(List.of(failure("p:d:0-c", "DOCUMENT_UNAVAILABLE")));
        assertThat(repository.all()).hasSize(2);
        assertThat(repository.stored("p:d:0-c").state()).isEqualTo(ChannelActionState.FAILED);
    }

    @Test
    void documentOnlyCompensatesForUnavailableDocumentAndNeedsSavedAlternative() {
        documentWithCompensation("p:d:0", "{\"text\":\"aviso\"}");
        confirm.run(List.of(failure("p:d:0", "MESSAGE_NOT_EDITABLE")));
        assertThat(repository.searchById("p:d:0-c")).isEmpty();

        documentWithCompensation("p:d:1", null);
        confirm.run(List.of(failure("p:d:1", "DOCUMENT_UNAVAILABLE")));
        assertThat(repository.searchById("p:d:1-c")).isEmpty();
        assertThat(repository.all()).hasSize(2);
    }

    @Test
    void otherCodesOnlyRegisterWithoutCompensating() {
        int i = 0;
        for (String code :
                List.of(
                        "CHAT_UNAVAILABLE",
                        "RATE_LIMITED",
                        "ACTION_NOT_SUPPORTED",
                        "DOCUMENT_UNAVAILABLE",
                        "TELEGRAM_ERROR")) {
            String id = "p:e:" + i++;
            editWithCompensation(id);
            confirm.run(List.of(failure(id, code)));
            assertThat(repository.searchById(id + "-c")).isEmpty();
            assertThat(repository.stored(id).reasonCode()).isEqualTo(code);
        }
        assertThat(repository.all()).hasSize(5);
    }

    @Test
    void nonEditableWithoutSavedCompensationOnlyRegisters() {
        response("u1-0");
        confirm.run(List.of(failure("u1-0", "MESSAGE_NOT_EDITABLE")));
        assertThat(repository.all()).hasSize(1);
        assertThat(repository.stored("u1-0").state()).isEqualTo(ChannelActionState.FAILED);
    }

    // ---- mensagem de etapa

    private void proactiveStep(UUID job, String id, String compensation) {
        save.run(
                "p:step:" + job,
                ChannelActionOrigin.PROACTIVE,
                null,
                List.of(
                        new NewAction(
                                id,
                                ChannelActionType.STEP_MESSAGE,
                                1L,
                                UUID.randomUUID(),
                                job,
                                "{\"text\":\"etapa\"}",
                                compensation)));
    }

    @Test
    void executedStepSavesMessageIdInJobBeforeFinalizing() {
        ProcessingJob job = step(UUID.randomUUID());
        proactiveStep(job.id(), "p:step:x:0", null);
        Response response =
                confirm.run(List.of(new Result("p:step:x:0", Status.EXECUTED, null, 4321L)));
        assertThat(response.accepted()).isEqualTo(1);
        assertThat(job.parameter(ProcessingJob.STEP_MESSAGE)).contains("4321");
        assertThat(repository.stored("p:step:x:0").state()).isEqualTo(ChannelActionState.EXECUTED);
    }

    @Test
    void executedStepWithoutMessageIdIsFlaggedAndDoesNotFinalize() {
        ProcessingJob job = step(UUID.randomUUID());
        proactiveStep(job.id(), "p:step:x:0", null);
        save.run(
                "u1",
                ChannelActionOrigin.UPDATE_REPLY,
                1L,
                List.of(
                        new NewAction(
                                "u1-0", ChannelActionType.MESSAGE, 1L, null, null, "{}", null)));
        for (Long invalid : new Long[] {null, 0L}) {
            Response response =
                    confirm.run(
                            List.of(
                                    new Result("p:step:x:0", Status.EXECUTED, null, invalid),
                                    ok("u1-0")));
            assertThat(response.contractViolation()).isTrue();
            assertThat(response.accepted()).isLessThanOrEqualTo(1);
            assertThat(response.ignored()).contains("p:step:x:0");
        }
        assertThat(repository.stored("p:step:x:0").state()).isEqualTo(ChannelActionState.PENDING);
        assertThat(job.parameter(ProcessingJob.STEP_MESSAGE)).isEmpty();
        assertThat(repository.stored("u1-0").state()).isEqualTo(ChannelActionState.EXECUTED);
    }

    @Test
    void stepEditFailureGeneratesNewStepMessageThatRewritesId() {
        ProcessingJob job = step(UUID.randomUUID());
        proactiveStep(job.id(), "p:step:x:0", "{\"text\":\"etapa\"}");
        confirm.run(List.of(failure("p:step:x:0", "MESSAGE_NOT_EDITABLE")));
        ChannelAction compensation = repository.searchById("p:step:x:0-c").orElseThrow();
        assertThat(compensation.type()).isEqualTo(ChannelActionType.STEP_MESSAGE);
        assertThat(compensation.jobId()).isEqualTo(job.id());
        confirm.run(List.of(new Result("p:step:x:0-c", Status.EXECUTED, null, 777L)));
        assertThat(job.parameter(ProcessingJob.STEP_MESSAGE)).contains("777");
    }

    @Test
    void stepWithoutJobDoesNotRequireMessageId() {
        save.run(
                "p:s",
                ChannelActionOrigin.PROACTIVE,
                null,
                List.of(
                        new NewAction(
                                "p:s:0",
                                ChannelActionType.STEP_MESSAGE,
                                1L,
                                UUID.randomUUID(),
                                null,
                                "{}",
                                null)));
        assertThat(confirm.run(List.of(ok("p:s:0"))).accepted()).isEqualTo(1);
    }

    // ---- concorrência

    @Test
    void saveLostToAnotherActorIsRedoneOverNewState() {
        response("u1-0");
        int[] triggers = {0};
        repository.beforeUpdate =
                () -> {
                    if (triggers[0]++ == 0) {
                        // outro ator (despachante) grava no meio: a versão avança
                        ChannelAction other = repository.stored("u1-0");
                        other.defineVersion(other.version() + 1);
                    }
                };
        Response response = confirm.run(List.of(ok("u1-0")));
        assertThat(response.accepted()).isEqualTo(1);
        assertThat(triggers[0]).isEqualTo(2);
        assertThat(repository.stored("u1-0").state()).isEqualTo(ChannelActionState.EXECUTED);
    }

    @Test
    void givesUpAfterThreeLostDisputes() {
        response("u1-0");
        repository.beforeUpdate =
                () -> {
                    ChannelAction other = repository.stored("u1-0");
                    other.defineVersion(other.version() + 1);
                };
        Response response = confirm.run(List.of(ok("u1-0")));
        assertThat(response.accepted()).isZero();
        assertThat(response.ignored()).containsExactly("u1-0");
        assertThat(repository.stored("u1-0").state()).isEqualTo(ChannelActionState.DELIVERED);
    }

    @Test
    void failureAlsoRedoesAfterLostDispute() {
        response("u1-0");
        int[] triggers = {0};
        repository.beforeUpdate =
                () -> {
                    if (triggers[0]++ == 0) {
                        ChannelAction other = repository.stored("u1-0");
                        other.defineVersion(other.version() + 1);
                    }
                };
        assertThat(confirm.run(List.of(failure("u1-0", "TELEGRAM_ERROR"))).accepted()).isEqualTo(1);
    }
}
