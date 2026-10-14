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
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.doubles.FakeChannelOrchestrator;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryChannelActionRepository;
import br.com.felipeblaft.reports.doubles.InMemoryTelegramLinkRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryJobQueue;
import br.com.felipeblaft.reports.port.DeliveryBatch;
import br.com.felipeblaft.reports.usecase.channel.DeliverPendingActionsUseCase.Cycle;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase.NewAction;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliverPendingActionsUseCaseTest {

    private static final long CHAT_A = 100L;
    private static final long CHAT_B = 200L;

    private final FixedClock clock = new FixedClock();
    private final InMemoryChannelActionRepository repository =
            new InMemoryChannelActionRepository();
    private final FakeChannelOrchestrator orchestrator = new FakeChannelOrchestrator();
    private final InMemoryJobQueue queue = new InMemoryJobQueue();
    private final InMemoryTelegramLinkRepository links = new InMemoryTelegramLinkRepository();
    private final RetryPolicy policy =
            new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));
    private final SaveChannelActionsUseCase save =
            new SaveChannelActionsUseCase(repository, clock, policy);
    private final ConfirmChannelActionsExecutionUseCase confirm =
            new ConfirmChannelActionsExecutionUseCase(repository, queue, clock, policy);
    private final DeliverPendingActionsUseCase deliver =
            new DeliverPendingActionsUseCase(
                    repository,
                    orchestrator,
                    queue,
                    links,
                    clock,
                    policy,
                    Duration.ofSeconds(60),
                    Duration.ofSeconds(60));

    private UUID linkedTeacher(long chatId) {
        UUID teacher = UUID.randomUUID();
        links.save(TelegramLink.create(teacher, chatId + 1_000, chatId, clock.now()));
        return teacher;
    }

    private void batch(String batchId, long chatId, UUID teacher, String... ids) {
        save.run(
                batchId,
                ChannelActionOrigin.PROACTIVE,
                null,
                java.util.Arrays.stream(ids)
                        .map(
                                id ->
                                        new NewAction(
                                                id,
                                                ChannelActionType.MESSAGE,
                                                chatId,
                                                teacher,
                                                null,
                                                "{\"text\":\"" + id + "\"}",
                                                null))
                        .toList());
    }

    private ChannelActionState state(String id) {
        return repository.stored(id).state();
    }

    // ---- aceite e recusa

    @Test
    void acceptMarksWholeBatchDeliveredWithStableIds() {
        UUID teacher = linkedTeacher(CHAT_A);
        batch("p:r:1", CHAT_A, teacher, "p:r:1:0", "p:r:1:1");

        Cycle cycle = deliver.run(10);

        assertThat(cycle.acceptedBatches()).isEqualTo(1);
        assertThat(cycle.refusedBatches()).isZero();
        assertThat(state("p:r:1:0")).isEqualTo(ChannelActionState.DELIVERED);
        assertThat(state("p:r:1:1")).isEqualTo(ChannelActionState.DELIVERED);
        assertThat(orchestrator.received).hasSize(1);
        DeliveryBatch received = orchestrator.received.get(0);
        assertThat(received.batchId()).isEqualTo("p:r:1");
        assertThat(received.origin()).isEqualTo(ChannelActionOrigin.PROACTIVE);
        assertThat(received.attempt()).isEqualTo(1);
        assertThat(received.actions())
                .extracting(DeliveryBatch.Action::actionId)
                .containsExactly("p:r:1:0", "p:r:1:1");
        assertThat(received.actions().get(0).payload()).contains("p:r:1:0");
    }

    @Test
    void withNothingDueDoesNotCallOrchestrator() {
        assertThat(deliver.run(10)).isEqualTo(new Cycle(0, 0, 0, List.of()));
        assertThat(orchestrator.received).isEmpty();
    }

    @Test
    void refusalKeepsPendingAndResendsWithSameIdsAtGrowingIntervals() {
        UUID teacher = linkedTeacher(CHAT_A);
        batch("p:r:1", CHAT_A, teacher, "p:r:1:0");
        orchestrator.refuseNext("HTTP_503", 2);

        assertThat(deliver.run(10).refusedBatches()).isEqualTo(1);
        assertThat(state("p:r:1:0")).isEqualTo(ChannelActionState.PENDING);
        assertThat(repository.stored("p:r:1:0").nextAttemptAt())
                .isEqualTo(clock.now().plusSeconds(30));
        // ainda cedo: nada é enviado
        clock.advance(Duration.ofSeconds(29));
        deliver.run(10);
        assertThat(orchestrator.received).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        assertThat(deliver.run(10).refusedBatches()).isEqualTo(1);
        assertThat(repository.stored("p:r:1:0").nextAttemptAt())
                .isEqualTo(clock.now().plusSeconds(60));

        clock.advance(Duration.ofSeconds(60));
        assertThat(deliver.run(10).acceptedBatches()).isEqualTo(1);
        assertThat(state("p:r:1:0")).isEqualTo(ChannelActionState.DELIVERED);
        assertThat(orchestrator.received).extracting(DeliveryBatch::batchId).containsOnly("p:r:1");
        assertThat(orchestrator.received)
                .extracting(DeliveryBatch::attempt)
                .containsExactly(1, 2, 3);
    }

    // ---- prazo

    @Test
    void deadlineExpiredBeforeDeliveryBecomesPermanentFailureWithoutCallingOrchestrator() {
        UUID teacher = linkedTeacher(CHAT_A);
        batch("p:r:1", CHAT_A, teacher, "p:r:1:0");
        clock.advance(Duration.ofHours(24));

        Cycle cycle = deliver.run(10);

        assertThat(cycle.permanentFailures()).containsExactly("p:r:1:0");
        assertThat(state("p:r:1:0")).isEqualTo(ChannelActionState.PERMANENTLY_FAILED);
        assertThat(orchestrator.received).isEmpty();
    }

    @Test
    void refusalCrossingDeadlineBecomesPermanentFailure() {
        UUID teacher = linkedTeacher(CHAT_A);
        batch("p:r:1", CHAT_A, teacher, "p:r:1:0");
        clock.advance(Duration.ofHours(24).minusSeconds(1));
        orchestrator.duringDelivery = () -> clock.advance(Duration.ofSeconds(2));
        orchestrator.refuseNext("TIMEOUT", 1);

        Cycle cycle = deliver.run(10);

        assertThat(cycle.permanentFailures()).containsExactly("p:r:1:0");
        assertThat(state("p:r:1:0")).isEqualTo(ChannelActionState.PERMANENTLY_FAILED);
    }

    // ---- vínculo

    @Test
    void teacherWithoutLinkHasActionDiscardedWithoutDelivery() {
        UUID withoutLink = UUID.randomUUID();
        batch("p:r:1", CHAT_A, withoutLink, "p:r:1:0");

        Cycle cycle = deliver.run(10);

        assertThat(cycle.discarded()).isEqualTo(1);
        assertThat(state("p:r:1:0")).isEqualTo(ChannelActionState.DISCARDED);
        assertThat(orchestrator.received).isEmpty();
    }

    @Test
    void actionWithoutTeacherSkipsLinkCheck() {
        batch("p:r:1", CHAT_A, null, "p:r:1:0");
        assertThat(deliver.run(10).acceptedBatches()).isEqualTo(1);
    }

    // ---- ordem por chat

    @Test
    void sameChatBatchWaitsForPreviousConfirmationButOtherChatFollows() {
        UUID a = linkedTeacher(CHAT_A);
        UUID b = linkedTeacher(CHAT_B);
        batch("p:1", CHAT_A, a, "p:1:0");
        batch("p:2", CHAT_A, a, "p:2:0");
        batch("p:3", CHAT_B, b, "p:3:0");

        Cycle first = deliver.run(10);

        assertThat(first.acceptedBatches()).isEqualTo(2);
        assertThat(orchestrator.received)
                .extracting(DeliveryBatch::batchId)
                .containsExactly("p:1", "p:3");
        assertThat(state("p:2:0")).isEqualTo(ChannelActionState.PENDING);

        confirm.run(
                List.of(
                        new ConfirmChannelActionsExecutionUseCase.Result(
                                "p:1:0",
                                ConfirmChannelActionsExecutionUseCase.Status.EXECUTED,
                                null,
                                null)));
        assertThat(deliver.run(10).acceptedBatches()).isEqualTo(1);
        assertThat(orchestrator.received)
                .extracting(DeliveryBatch::batchId)
                .containsExactly("p:1", "p:3", "p:2");
    }

    @Test
    void withoutConfirmationGateReleasesAfterTimeout() {
        UUID a = linkedTeacher(CHAT_A);
        batch("p:1", CHAT_A, a, "p:1:0");
        batch("p:2", CHAT_A, a, "p:2:0");
        deliver.run(10);
        assertThat(orchestrator.received).hasSize(1);

        clock.advance(Duration.ofSeconds(59));
        deliver.run(10);
        assertThat(orchestrator.received).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        deliver.run(10);
        assertThat(orchestrator.received)
                .extracting(DeliveryBatch::batchId)
                .containsExactly("p:1", "p:2");
    }

    @Test
    void unconfirmedReplyToUpdateAlsoHoldsProactiveBatchOfSameChat() {
        UUID a = linkedTeacher(CHAT_A);
        save.run(
                "u5",
                ChannelActionOrigin.UPDATE_REPLY,
                5L,
                List.of(
                        new NewAction(
                                "u5-0",
                                ChannelActionType.MESSAGE,
                                CHAT_A,
                                null,
                                null,
                                "{}",
                                null)));
        batch("p:1", CHAT_A, a, "p:1:0");

        deliver.run(10);
        assertThat(orchestrator.received).isEmpty();

        clock.advance(Duration.ofSeconds(61));
        deliver.run(10);
        assertThat(orchestrator.received).hasSize(1);
    }

    @Test
    void previousBatchInBackoffKeepsHoldingNextOfSameChat() {
        UUID a = linkedTeacher(CHAT_A);
        batch("p:1", CHAT_A, a, "p:1:0");
        orchestrator.refuseNext("HTTP_500", 1);
        batch("p:2", CHAT_A, a, "p:2:0");

        deliver.run(10);

        assertThat(orchestrator.received).extracting(DeliveryBatch::batchId).containsExactly("p:1");
        assertThat(state("p:2:0")).isEqualTo(ChannelActionState.PENDING);
    }

    @Test
    void respectsMaxBatchesPerCycle() {
        UUID a = linkedTeacher(CHAT_A);
        UUID b = linkedTeacher(CHAT_B);
        batch("p:1", CHAT_A, a, "p:1:0");
        batch("p:2", CHAT_B, b, "p:2:0");
        assertThat(deliver.run(1).acceptedBatches()).isEqualTo(1);
        assertThat(orchestrator.received).hasSize(1);
    }

    // ---- corrida e lease

    @Test
    void confirmationArrivingDuringHttpPrevailsOverAccept() {
        UUID a = linkedTeacher(CHAT_A);
        batch("p:1", CHAT_A, a, "p:1:0");
        orchestrator.duringDelivery =
                () ->
                        confirm.run(
                                List.of(
                                        new ConfirmChannelActionsExecutionUseCase.Result(
                                                "p:1:0",
                                                ConfirmChannelActionsExecutionUseCase.Status
                                                        .EXECUTED,
                                                null,
                                                null)));

        Cycle cycle = deliver.run(10);

        assertThat(cycle.acceptedBatches()).isEqualTo(1);
        assertThat(state("p:1:0")).isEqualTo(ChannelActionState.EXECUTED);
    }

    @Test
    void confirmationDuringRefusedHttpDoesNotBecomePermanentFailureNorReturnToPending() {
        UUID a = linkedTeacher(CHAT_A);
        batch("p:1", CHAT_A, a, "p:1:0");
        orchestrator.refuseNext("TIMEOUT", 1);
        orchestrator.duringDelivery =
                () ->
                        confirm.run(
                                List.of(
                                        new ConfirmChannelActionsExecutionUseCase.Result(
                                                "p:1:0",
                                                ConfirmChannelActionsExecutionUseCase.Status.FAILED,
                                                "TELEGRAM_ERROR",
                                                null)));
        Cycle cycle = deliver.run(10);
        assertThat(cycle.permanentFailures()).isEmpty();
        assertThat(state("p:1:0")).isEqualTo(ChannelActionState.FAILED);
    }

    @Test
    void dispatcherThatDiesLeavesBatchRedeliverableWhenLeaseExpires() {
        UUID a = linkedTeacher(CHAT_A);
        batch("p:1", CHAT_A, a, "p:1:0");
        // outro despachante reservou o lote e morreu antes de entregar
        assertThat(
                        repository.reserveNextDueBatch(
                                clock.now(), Duration.ofSeconds(60), clock.now().plusSeconds(60)))
                .hasSize(1);

        deliver.run(10);
        assertThat(orchestrator.received).isEmpty();

        clock.advance(Duration.ofSeconds(60));
        deliver.run(10);
        assertThat(orchestrator.received).extracting(DeliveryBatch::batchId).containsExactly("p:1");
    }

    // ---- etapa

    @Test
    void stepCarriesMessageIdAlreadyKnownToJob() {
        UUID a = linkedTeacher(CHAT_A);
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        a,
                        UUID.randomUUID(),
                        null,
                        null,
                        Channel.TELEGRAM,
                        Medium.AUDIO,
                        JobStep.UPLOADING,
                        clock.now());
        job.defineParameter(ProcessingJob.STEP_MESSAGE, "555");
        queue.enqueue(job);
        ProcessingJob withoutMessage =
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        a,
                        UUID.randomUUID(),
                        null,
                        null,
                        Channel.TELEGRAM,
                        Medium.AUDIO,
                        JobStep.UPLOADING,
                        clock.now());
        queue.enqueue(withoutMessage);
        ProcessingJob invalid =
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        a,
                        UUID.randomUUID(),
                        null,
                        null,
                        Channel.TELEGRAM,
                        Medium.AUDIO,
                        JobStep.UPLOADING,
                        clock.now());
        invalid.defineParameter(ProcessingJob.STEP_MESSAGE, "not-a-number");
        queue.enqueue(invalid);
        save.run(
                "p:e",
                ChannelActionOrigin.PROACTIVE,
                null,
                List.of(
                        new NewAction(
                                "p:e:0",
                                ChannelActionType.STEP_MESSAGE,
                                CHAT_A,
                                a,
                                job.id(),
                                "{}",
                                null),
                        new NewAction(
                                "p:e:1",
                                ChannelActionType.STEP_MESSAGE,
                                CHAT_A,
                                a,
                                withoutMessage.id(),
                                "{}",
                                null),
                        new NewAction(
                                "p:e:2",
                                ChannelActionType.STEP_MESSAGE,
                                CHAT_A,
                                a,
                                invalid.id(),
                                "{}",
                                null),
                        new NewAction(
                                "p:e:3",
                                ChannelActionType.STEP_MESSAGE,
                                CHAT_A,
                                a,
                                UUID.randomUUID(),
                                "{}",
                                null),
                        new NewAction(
                                "p:e:4",
                                ChannelActionType.MESSAGE,
                                CHAT_A,
                                a,
                                job.id(),
                                "{}",
                                null)));

        deliver.run(10);

        List<DeliveryBatch.Action> actions = orchestrator.received.get(0).actions();
        assertThat(actions)
                .extracting(DeliveryBatch.Action::editMessageId)
                .containsExactly(555L, null, null, null, null);
        assertThat(actions.get(0).jobId()).isEqualTo(job.id());
    }

    @Test
    void stepCompensationDoesNotCarryIdOfMessageThatCannotBeEdited() {
        UUID a = linkedTeacher(CHAT_A);
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        a,
                        UUID.randomUUID(),
                        null,
                        null,
                        Channel.TELEGRAM,
                        Medium.AUDIO,
                        JobStep.UPLOADING,
                        clock.now());
        job.defineParameter(ProcessingJob.STEP_MESSAGE, "555");
        queue.enqueue(job);
        ChannelAction step =
                ChannelAction.create(
                        "p:e:0",
                        "p:e",
                        ChannelActionOrigin.PROACTIVE,
                        null,
                        CHAT_A,
                        a,
                        job.id(),
                        ChannelActionType.STEP_MESSAGE,
                        "{}",
                        "{}",
                        clock.now(),
                        policy);
        repository.saveBatch(List.of(step.createCompensation(clock.now(), policy)));

        deliver.run(10);

        assertThat(orchestrator.received.get(0).actions().get(0).actionId()).isEqualTo("p:e:0-c");
        assertThat(orchestrator.received.get(0).actions().get(0).editMessageId()).isNull();
    }

    @Test
    void permanentFailureReportedOnlyOncePerAction() {
        UUID a = linkedTeacher(CHAT_A);
        batch("p:1", CHAT_A, a, "p:1:0");
        clock.advance(Duration.ofHours(24));
        assertThat(deliver.run(10).permanentFailures()).hasSize(1);
        assertThat(deliver.run(10).permanentFailures()).isEmpty();
        ChannelAction action = repository.stored("p:1:0");
        assertThat(action.reasonCode()).isEqualTo(ChannelAction.DEADLINE_EXPIRED_CODE);
    }
}
