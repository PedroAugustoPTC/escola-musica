package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Confirmação: dono, token de uso único, prazo e atomicidade com o caso de uso de gravação. */
class ConfirmStudentRegistrationUseCaseTest {

    private static final String TOKEN = "ABCD2345";

    private final DraftScenario scenario = new DraftScenario();

    private StudentView confirm(String token) {
        return confirm(scenario.link.id(), token);
    }

    private StudentView confirm(UUID linkId, String token) {
        return scenario.confirm()
                .run(
                        scenario.actor,
                        new ConfirmStudentRegistrationUseCase.Command(
                                linkId, token, DraftScenario.CORRELATION));
    }

    private DomainException refused(String token) {
        try {
            confirm(token);
        } catch (DomainException error) {
            return error;
        }
        throw new AssertionError("esperava DomainException");
    }

    private StudentRegistrationDraft statusDraft(Kind kind, UUID studentId) {
        StudentRegistrationDraft draft =
                scenario.startDraft(kind, studentId)
                        .withConfirmationToken(TOKEN, scenario.clock.now(), DraftScenario.VALIDITY);
        scenario.drafts.save(draft);
        return draft;
    }

    private StudentRegistrationDraft updateDraft(UUID studentId) {
        StudentRegistrationDraft draft =
                scenario.startDraft(Kind.UPDATE, studentId)
                        .withName("Nome Novo", scenario.clock.now(), DraftScenario.VALIDITY)
                        .withConfirmationToken(TOKEN, scenario.clock.now(), DraftScenario.VALIDITY);
        scenario.drafts.save(draft);
        return draft;
    }

    // ---- CREATE

    @Test
    void createRegistersWithTheDecryptedCpfAndConsumesTheDraft() {
        scenario.confirmableCreate(TOKEN);

        StudentView view = confirm(TOKEN);

        assertThat(view).isSameAs(scenario.register.result);
        assertThat(scenario.register.calls).hasSize(1);
        var call = scenario.register.calls.get(0);
        assertThat(call.actor()).isEqualTo(scenario.actor);
        assertThat(call.command().correlationId()).isEqualTo(DraftScenario.CORRELATION);
        assertThat(call.command().teacherIds()).isEmpty();
        assertThat(call.command().lookupAlreadyCounted()).isTrue();
        StudentInput input = call.command().input();
        assertThat(input.name()).isEqualTo("Ana Souza");
        assertThat(input.birthDate()).isEqualTo("1990-03-15");
        assertThat(input.cpf().value()).isEqualTo(Cpf.of(DraftScenario.CPF).digits());
        assertThat(input.instrument()).isEqualTo("Violão");
        assertThat(input.guardian()).isNull();
        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isEmpty();
        assertThat(scenario.decryption.decryptions()).isEqualTo(1);
        assertThat(scenario.writes()).isEqualTo(1);
    }

    @Test
    void createOfAMinorPassesTheThreeGuardianValues() {
        StudentRegistrationDraft base = scenario.confirmableCreate(TOKEN);
        StudentRegistrationDraft minor =
                base.withBirthDate(
                                LocalDate.of(2014, 5, 10),
                                scenario.clock.now(),
                                DraftScenario.VALIDITY)
                        .withGuardianName(
                                "Maria Souza", scenario.clock.now(), DraftScenario.VALIDITY)
                        .withGuardianPhone(
                                "11912345678", scenario.clock.now(), DraftScenario.VALIDITY)
                        .withGuardianEmail(
                                "maria@example.com", scenario.clock.now(), DraftScenario.VALIDITY)
                        .withConfirmationToken(TOKEN, scenario.clock.now(), DraftScenario.VALIDITY);
        scenario.drafts.save(minor);

        confirm(TOKEN);

        GuardianInput guardian = scenario.register.calls.get(0).command().input().guardian();
        assertThat(guardian.name()).isEqualTo("Maria Souza");
        assertThat(guardian.phone()).isEqualTo("11912345678");
        assertThat(guardian.email()).isEqualTo("maria@example.com");
        assertThat(scenario.register.calls.get(0).command().input().birthDate())
                .isEqualTo("2014-05-10");
    }

    @Test
    void createWithTheInstrumentSkippedSendsNoInstrument() {
        StudentRegistrationDraft skipped =
                scenario.confirmableCreate(TOKEN)
                        .withInstrument(null, scenario.clock.now(), DraftScenario.VALIDITY)
                        .withConfirmationToken(TOKEN, scenario.clock.now(), DraftScenario.VALIDITY);
        scenario.drafts.save(skipped);

        confirm(TOKEN);

        assertThat(scenario.register.calls.get(0).command().input().instrument()).isNull();
    }

    @Test
    void aDuplicateCpfLeavesTheDraftSoTheTeacherCanFixTheCpf() {
        scenario.confirmableCreate(TOKEN);
        scenario.register.failure = new DomainException(ErrorCode.CPF_UNAVAILABLE);

        assertThat(refused(TOKEN).code()).isEqualTo(ErrorCode.CPF_UNAVAILABLE);

        StudentRegistrationDraft kept = scenario.drafts.searchByLinkId(scenario.link.id()).get();
        assertThat(kept.matchesToken(TOKEN)).isTrue();
        assertThat(scenario.writes()).isZero();
    }

    // ---- UPDATE

    @Test
    void updateSendsOnlyTheProvidedFields() {
        UUID student = scenario.ownStudent().id();
        updateDraft(student);

        StudentView view = confirm(TOKEN);

        assertThat(view).isSameAs(scenario.update.result);
        var command = scenario.update.calls.get(0).command();
        assertThat(command.studentId()).isEqualTo(student);
        assertThat(command.correlationId()).isEqualTo(DraftScenario.CORRELATION);
        assertThat(command.lookupAlreadyCounted()).isTrue();
        StudentInput input = command.input();
        assertThat(input.name()).isEqualTo("Nome Novo");
        assertThat(input.birthDate()).isNull();
        assertThat(input.cpf()).isNull();
        assertThat(input.guardian()).isNull();
        assertThat(input.instrument()).isNull();
        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isEmpty();
        assertThat(scenario.decryption.decryptions()).isZero();
        assertThat(scenario.writes()).isEqualTo(1);
    }

    @Test
    void updateSendsTheNewCpfBirthDateGuardianAndInstrumentWhenProvided() {
        UUID student = scenario.ownStudent().id();
        StudentRegistrationDraft draft =
                scenario.startDraft(Kind.UPDATE, student)
                        .withBirthDate(
                                LocalDate.of(2014, 5, 10),
                                scenario.clock.now(),
                                DraftScenario.VALIDITY)
                        .withCpf(
                                scenario.protection.encryptCpf(Cpf.of(DraftScenario.CPF)),
                                scenario.protection.hashCpf(Cpf.of(DraftScenario.CPF)),
                                scenario.clock.now(),
                                DraftScenario.VALIDITY)
                        .withGuardianName(
                                "Maria Souza", scenario.clock.now(), DraftScenario.VALIDITY)
                        .withGuardianPhone(
                                "11912345678", scenario.clock.now(), DraftScenario.VALIDITY)
                        .withGuardianEmail(
                                "maria@example.com", scenario.clock.now(), DraftScenario.VALIDITY)
                        .withInstrument("Piano", scenario.clock.now(), DraftScenario.VALIDITY)
                        .withConfirmationToken(TOKEN, scenario.clock.now(), DraftScenario.VALIDITY);
        scenario.drafts.save(draft);

        confirm(TOKEN);

        StudentInput input = scenario.update.calls.get(0).command().input();
        assertThat(input.name()).isNull();
        assertThat(input.birthDate()).isEqualTo("2014-05-10");
        assertThat(input.cpf().value()).isEqualTo(Cpf.of(DraftScenario.CPF).digits());
        assertThat(input.guardian().name()).isEqualTo("Maria Souza");
        assertThat(input.guardian().phone()).isEqualTo("11912345678");
        assertThat(input.guardian().email()).isEqualTo("maria@example.com");
        assertThat(input.instrument()).isEqualTo("Piano");
    }

    @Test
    void anAssociationRemovedBetweenStartAndConfirmWritesNothingAndKeepsTheDraft() {
        updateDraft(scenario.ownStudent().id());
        scenario.update.failure = new DomainException(ErrorCode.RESOURCE_NOT_FOUND);

        assertThat(refused(TOKEN).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        assertThat(scenario.writes()).isZero();
        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isPresent();
    }

    // ---- INACTIVATE e REACTIVATE

    @Test
    void inactivateRunsOnlyTheInactivationAndConsumesTheDraft() {
        UUID student = scenario.ownStudent().id();
        statusDraft(Kind.INACTIVATE, student);

        StudentView view = confirm(TOKEN);

        assertThat(view).isSameAs(scenario.inactivate.result);
        assertThat(scenario.inactivate.calls).hasSize(1);
        var call = scenario.inactivate.calls.get(0);
        assertThat(call.actor()).isEqualTo(scenario.actor);
        assertThat(call.command().studentId()).isEqualTo(student);
        assertThat(call.command().correlationId()).isEqualTo(DraftScenario.CORRELATION);
        assertThat(scenario.writes()).isEqualTo(1);
        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isEmpty();
        assertThat(scenario.decryption.decryptions()).isZero();
    }

    @Test
    void reactivateRunsOnlyTheReactivation() {
        UUID student = scenario.ownStudent().id();
        statusDraft(Kind.REACTIVATE, student);

        StudentView view = confirm(TOKEN);

        assertThat(view).isSameAs(scenario.reactivate.result);
        assertThat(scenario.reactivate.calls).hasSize(1);
        assertThat(scenario.reactivate.calls.get(0).command().studentId()).isEqualTo(student);
        assertThat(scenario.writes()).isEqualTo(1);
    }

    @Test
    void aSharedStudentDeniedByTheInactivationWritesNothingAndKeepsTheDraft() {
        statusDraft(Kind.INACTIVATE, scenario.ownStudent().id());
        scenario.inactivate.failure = new DomainException(ErrorCode.SHARED_STUDENT_ACTION_DENIED);

        assertThat(refused(TOKEN).code()).isEqualTo(ErrorCode.SHARED_STUDENT_ACTION_DENIED);

        assertThat(scenario.writes()).isZero();
        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isPresent();
    }

    @Test
    void aReactivationDeniedBecauseTheStudentIsGoneWritesNothing() {
        statusDraft(Kind.REACTIVATE, scenario.ownStudent().id());
        scenario.reactivate.failure = new DomainException(ErrorCode.RESOURCE_NOT_FOUND);

        assertThat(refused(TOKEN).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        assertThat(scenario.writes()).isZero();
    }

    // ---- Token de uso único

    @Test
    void theSecondConfirmationIsAlreadyDoneAndWritesNothingMore() {
        scenario.confirmableCreate(TOKEN);
        confirm(TOKEN);

        assertThat(refused(TOKEN).code()).isEqualTo(ErrorCode.CONFIRMATION_ALREADY_DONE);

        assertThat(scenario.register.calls).hasSize(1);
        assertThat(scenario.decryption.decryptions()).isEqualTo(1);
    }

    @Test
    void theSecondConfirmationOfAStatusChangeIsAlreadyDoneToo() {
        statusDraft(Kind.INACTIVATE, scenario.ownStudent().id());
        confirm(TOKEN);

        assertThat(refused(TOKEN).code()).isEqualTo(ErrorCode.CONFIRMATION_ALREADY_DONE);

        assertThat(scenario.inactivate.calls).hasSize(1);
    }

    @Test
    void aTokenFromAnEarlierSummaryIsAlreadyDoneAndTheCurrentOneStillWorks() {
        UUID student = scenario.ownStudent().id();
        updateDraft(student);
        StudentRegistrationDraft renewed =
                scenario.drafts
                        .searchByLinkId(scenario.link.id())
                        .get()
                        .withConfirmationToken(
                                "NEWTOKN2", scenario.clock.now(), DraftScenario.VALIDITY);
        scenario.drafts.save(renewed);

        assertThat(refused(TOKEN).code()).isEqualTo(ErrorCode.CONFIRMATION_ALREADY_DONE);
        assertThat(scenario.writes()).isZero();
        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isPresent();

        confirm("NEWTOKN2");
        assertThat(scenario.update.calls).hasSize(1);
    }

    @Test
    void aWrongOrMissingTokenIsAlreadyDoneAndKeepsTheDraft() {
        scenario.confirmableCreate(TOKEN);

        assertThat(refused("WRONGTK2").code()).isEqualTo(ErrorCode.CONFIRMATION_ALREADY_DONE);
        assertThat(refused(null).code()).isEqualTo(ErrorCode.CONFIRMATION_ALREADY_DONE);
        assertThat(refused("").code()).isEqualTo(ErrorCode.CONFIRMATION_ALREADY_DONE);

        assertThat(scenario.writes()).isZero();
        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isPresent();
    }

    @Test
    void aDraftWithoutATokenCannotBeConfirmed() {
        scenario.startDraft(Kind.CREATE, null);

        assertThat(refused(TOKEN).code()).isEqualTo(ErrorCode.CONFIRMATION_ALREADY_DONE);
        assertThat(scenario.writes()).isZero();
    }

    @Test
    void withoutADraftTheConfirmationIsAlreadyDone() {
        assertThat(refused(TOKEN).code()).isEqualTo(ErrorCode.CONFIRMATION_ALREADY_DONE);
        assertThat(scenario.writes()).isZero();
    }

    @Test
    void aButtonFromAnotherLinkNeverConfirmsAndNeverTouchesTheOwnersDraft() {
        scenario.confirmableCreate(TOKEN);
        UUID otherLink = UUID.randomUUID();

        try {
            confirm(otherLink, TOKEN);
            throw new AssertionError("esperava recusa");
        } catch (DomainException error) {
            assertThat(error.code()).isEqualTo(ErrorCode.CONFIRMATION_ALREADY_DONE);
        }

        assertThat(scenario.writes()).isZero();
        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isPresent();
    }

    // ---- Prazo

    @Test
    void anExpiredDraftIsDiscardedAndNothingIsWritten() {
        scenario.confirmableCreate(TOKEN);
        scenario.clock.advance(DraftScenario.VALIDITY);

        assertThat(refused(TOKEN).code()).isEqualTo(ErrorCode.CONFIRMATION_EXPIRED);

        assertThat(scenario.writes()).isZero();
        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isEmpty();
        assertThat(scenario.decryption.decryptions()).isZero();
    }

    @Test
    void justBeforeTheDeadlineTheConfirmationStillWorks() {
        scenario.confirmableCreate(TOKEN);
        scenario.clock.advance(DraftScenario.VALIDITY.minusSeconds(1));

        confirm(TOKEN);

        assertThat(scenario.register.calls).hasSize(1);
    }

    @Test
    void theDeadlineIsCheckedEvenWithAWrongToken() {
        scenario.confirmableCreate(TOKEN);
        scenario.clock.advance(Duration.ofHours(1));

        assertThat(refused("WRONGTK2").code()).isEqualTo(ErrorCode.CONFIRMATION_EXPIRED);
    }

    // ---- Atomicidade e autorização

    @Test
    void theWriteAndTheConsumptionRunInASingleTransaction() {
        scenario.confirmableCreate(TOKEN);

        confirm(TOKEN);

        assertThat(scenario.transaction.transactions).isEqualTo(1);
    }

    @Test
    void whenTheWriteFailsTheDraftIsNotConsumed() {
        scenario.confirmableCreate(TOKEN);
        scenario.register.failure = new IllegalStateException("falha técnica");

        try {
            confirm(TOKEN);
            throw new AssertionError("esperava falha");
        } catch (IllegalStateException error) {
            assertThat(error).hasMessage("falha técnica");
        }

        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isPresent();
        assertThat(scenario.writes()).isZero();
    }

    @Test
    void onlyTheTeacherOnTelegramMayConfirm() {
        scenario.confirmableCreate(TOKEN);
        for (Actor denied :
                new Actor[] {
                    ActorFixtures.administratorWeb(),
                    Actor.teacher(scenario.teacher.id(), ActionOrigin.WEB),
                    ActorFixtures.initialLoad()
                }) {
            try {
                scenario.confirm()
                        .run(
                                denied,
                                new ConfirmStudentRegistrationUseCase.Command(
                                        scenario.link.id(), TOKEN, DraftScenario.CORRELATION));
                throw new AssertionError("esperava recusa");
            } catch (DomainException error) {
                assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            }
        }
        assertThat(scenario.writes()).isZero();
        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).isPresent();
    }

    @Test
    void theOperationOfTheDraftKindIsCheckedAgainstTheMatrix() {
        List<Kind> kinds = List.of(Kind.CREATE, Kind.UPDATE, Kind.INACTIVATE, Kind.REACTIVATE);
        List<Operation> operations =
                List.of(
                        Operation.STUDENT_REGISTER,
                        Operation.STUDENT_UPDATE,
                        Operation.STUDENT_INACTIVATE,
                        Operation.STUDENT_REACTIVATE);
        for (int i = 0; i < kinds.size(); i++) {
            DraftScenario restricted =
                    new DraftScenario(DraftScenario.matrixDenying(operations.get(i)));
            UUID student = restricted.ownStudent().id();
            StudentRegistrationDraft draft =
                    kinds.get(i) == Kind.CREATE
                            ? restricted.confirmableCreate(TOKEN)
                            : restricted
                                    .startDraft(kinds.get(i), student)
                                    .withConfirmationToken(
                                            TOKEN, restricted.clock.now(), DraftScenario.VALIDITY);
            restricted.drafts.save(
                    draft.withName("N", restricted.clock.now(), DraftScenario.VALIDITY)
                            .withConfirmationToken(
                                    TOKEN, restricted.clock.now(), DraftScenario.VALIDITY));

            try {
                restricted
                        .confirm()
                        .run(
                                restricted.actor,
                                new ConfirmStudentRegistrationUseCase.Command(
                                        restricted.link.id(), TOKEN, DraftScenario.CORRELATION));
                throw new AssertionError("esperava recusa em " + kinds.get(i));
            } catch (DomainException error) {
                assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            }
            assertThat(restricted.writes()).isZero();
        }
    }

    // ---- tipo do rascunho confirmado (o canal escolhe a resposta por ele, não pela conversa)

    private ConfirmStudentRegistrationUseCase.Outcome confirmWithKind() {
        return scenario.confirm()
                .runWithKind(
                        scenario.actor,
                        new ConfirmStudentRegistrationUseCase.Command(
                                scenario.link.id(), TOKEN, DraftScenario.CORRELATION));
    }

    @Test
    void theOutcomeCarriesTheKindOfTheConfirmedDraftAndTheStudent() {
        scenario.confirmableCreate(TOKEN);
        var created = confirmWithKind();
        assertThat(created.kind()).isEqualTo(Kind.CREATE);
        assertThat(created.student()).isSameAs(scenario.register.result);

        updateDraft(scenario.ownStudent().id());
        var updated = confirmWithKind();
        assertThat(updated.kind()).isEqualTo(Kind.UPDATE);
        assertThat(updated.student()).isSameAs(scenario.update.result);

        statusDraft(Kind.INACTIVATE, scenario.ownStudent().id());
        var inactivated = confirmWithKind();
        assertThat(inactivated.kind()).isEqualTo(Kind.INACTIVATE);
        assertThat(inactivated.student()).isSameAs(scenario.inactivate.result);

        statusDraft(Kind.REACTIVATE, scenario.ownStudent().id());
        var reactivated = confirmWithKind();
        assertThat(reactivated.kind()).isEqualTo(Kind.REACTIVATE);
        assertThat(reactivated.student()).isSameAs(scenario.reactivate.result);
    }

    @Test
    void theOutcomeIsNotProducedWhenTheConfirmationIsRefused() {
        assertThatThrownBy(this::confirmWithKind)
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error ->
                                assertThat(error.code())
                                        .isEqualTo(ErrorCode.CONFIRMATION_ALREADY_DONE));
    }

    @Test
    void commandNeverRevealsTheTokenInToString() {
        var command =
                new ConfirmStudentRegistrationUseCase.Command(UUID.randomUUID(), TOKEN, "req-1");

        assertThat(command.toString()).doesNotContain(TOKEN);
    }
}
