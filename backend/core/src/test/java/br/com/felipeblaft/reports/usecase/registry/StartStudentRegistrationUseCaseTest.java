package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Início do cadastro em andamento no Telegram, por tipo, com o escopo do professor no núcleo. */
class StartStudentRegistrationUseCaseTest {

    private final DraftScenario scenario = new DraftScenario();

    private StartStudentRegistrationUseCase.Command command(Kind kind, UUID studentId) {
        return new StartStudentRegistrationUseCase.Command(scenario.link.id(), kind, studentId);
    }

    private DomainException failure(Kind kind, UUID studentId) {
        return catchDomain(() -> scenario.start().run(scenario.actor, command(kind, studentId)));
    }

    private static DomainException catchDomain(Runnable action) {
        try {
            action.run();
        } catch (DomainException error) {
            return error;
        }
        throw new AssertionError("esperava DomainException");
    }

    @Test
    void createStartsAnEmptyDraftWithTheInactivityDeadline() {
        StartStudentRegistrationUseCase.Result result =
                scenario.start().run(scenario.actor, command(Kind.CREATE, null));

        StudentRegistrationDraft draft = scenario.drafts.searchByLinkId(scenario.link.id()).get();
        assertThat(result.kind()).isEqualTo(Kind.CREATE);
        assertThat(result.expiresAt()).isEqualTo(scenario.clock.now().plus(DraftScenario.VALIDITY));
        assertThat(result.confirmationToken()).isNull();
        assertThat(draft.kind()).isEqualTo(Kind.CREATE);
        assertThat(draft.studentId()).isNull();
        assertThat(draft.providedFields()).isEmpty();
        assertThat(draft.expiresAt()).isEqualTo(result.expiresAt());
        assertThat(scenario.students.all()).isEmpty();
    }

    @Test
    void createIgnoresAStudentIdentifierSentByMistake() {
        Student alien = scenario.alienStudent();

        scenario.start().run(scenario.actor, command(Kind.CREATE, alien.id()));

        assertThat(scenario.drafts.searchByLinkId(scenario.link.id()).get().studentId()).isNull();
    }

    @Test
    void updateKeepsTheStudentAndStartsWithoutFields() {
        Student student = scenario.ownStudent();

        StartStudentRegistrationUseCase.Result result =
                scenario.start().run(scenario.actor, command(Kind.UPDATE, student.id()));

        StudentRegistrationDraft draft = scenario.drafts.searchByLinkId(scenario.link.id()).get();
        assertThat(result.kind()).isEqualTo(Kind.UPDATE);
        assertThat(result.confirmationToken()).isNull();
        assertThat(draft.studentId()).isEqualTo(student.id());
        assertThat(draft.providedFields()).isEmpty();
        assertThat(draft.confirmationToken()).isNull();
    }

    @Test
    void inactivateAndReactivateStoreTheStudentAndTheSingleUseToken() {
        Student student = scenario.ownStudent();
        for (Kind kind : new Kind[] {Kind.INACTIVATE, Kind.REACTIVATE}) {
            StartStudentRegistrationUseCase.Result result =
                    scenario.start().run(scenario.actor, command(kind, student.id()));

            StudentRegistrationDraft draft =
                    scenario.drafts.searchByLinkId(scenario.link.id()).get();
            assertThat(result.kind()).isEqualTo(kind);
            assertThat(result.confirmationToken()).matches("[A-Z2-9]{8}");
            assertThat(draft.kind()).isEqualTo(kind);
            assertThat(draft.studentId()).isEqualTo(student.id());
            assertThat(draft.providedFields()).isEmpty();
            assertThat(draft.confirmationToken()).isEqualTo(result.confirmationToken());
            assertThat(draft.matchesToken(result.confirmationToken())).isTrue();
        }
    }

    @Test
    void nothingIsWrittenAsAStudentOrAuthorship() {
        Student student = scenario.ownStudent();
        int before = scenario.students.all().size();

        scenario.start().run(scenario.actor, command(Kind.INACTIVATE, student.id()));

        assertThat(scenario.students.all()).hasSize(before);
        assertThat(scenario.writes()).isZero();
    }

    @Test
    void anotherTeachersStudentIsTreatedAsNonexistentAndNoDraftIsCreated() {
        Student alien = scenario.alienStudent();
        for (Kind kind : new Kind[] {Kind.UPDATE, Kind.INACTIVATE, Kind.REACTIVATE}) {
            DomainException foreign = failure(kind, alien.id());
            DomainException missing = failure(kind, UUID.randomUUID());

            assertThat(foreign.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            assertThat(missing.code()).isEqualTo(foreign.code());
            assertThat(missing.details()).isEqualTo(foreign.details());
        }
        assertThat(scenario.drafts.saves()).isZero();
        assertThat(scenario.drafts.all()).isEmpty();
    }

    @Test
    void aStudentWithoutTeachersIsAlsoNonexistentForTheTeacher() {
        Student orphan = scenario.student(scenario.teacher, "Sem professor");
        scenario.assignments.delete(scenario.teacher.id(), orphan.id());

        assertThat(failure(Kind.UPDATE, orphan.id()).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(scenario.drafts.all()).isEmpty();
    }

    @Test
    void aFailedStartKeepsThePreviousDraftUntouched() {
        Student own = scenario.ownStudent();
        Student alien = scenario.alienStudent();
        scenario.start().run(scenario.actor, command(Kind.UPDATE, own.id()));
        StudentRegistrationDraft previous =
                scenario.drafts.searchByLinkId(scenario.link.id()).get();

        failure(Kind.UPDATE, alien.id());

        assertThat(scenario.drafts.searchByLinkId(scenario.link.id())).contains(previous);
    }

    @Test
    void existingKindsRequireAStudentIdentifier() {
        for (Kind kind : new Kind[] {Kind.UPDATE, Kind.INACTIVATE, Kind.REACTIVATE}) {
            assertThat(failure(kind, null).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
        assertThat(scenario.drafts.all()).isEmpty();
    }

    @Test
    void thereIsOneDraftPerLinkAndTheNewOneReplacesThePrevious() {
        Student student = scenario.ownStudent();
        scenario.start().run(scenario.actor, command(Kind.CREATE, null));
        scenario.start().run(scenario.actor, command(Kind.INACTIVATE, student.id()));

        assertThat(scenario.drafts.all()).hasSize(1);
        assertThat(scenario.drafts.all().get(0).kind()).isEqualTo(Kind.INACTIVATE);
    }

    @Test
    void theNewDraftStartsEmptyEvenWhenThePreviousHadFields() {
        scenario.start().run(scenario.actor, command(Kind.CREATE, null));
        StudentRegistrationDraft started = scenario.drafts.searchByLinkId(scenario.link.id()).get();
        scenario.drafts.save(started.withName("Ana", scenario.clock.now(), DraftScenario.VALIDITY));

        scenario.start().run(scenario.actor, command(Kind.CREATE, null));

        assertThat(scenario.drafts.searchByLinkId(scenario.link.id()).get().providedFields())
                .isEmpty();
    }

    @Test
    void theDraftBelongsToTheCallersOwnLinkOnly() {
        UUID otherLink = UUID.randomUUID();
        DomainException error =
                catchDomain(
                        () ->
                                scenario.start()
                                        .run(
                                                scenario.actor,
                                                new StartStudentRegistrationUseCase.Command(
                                                        otherLink, Kind.CREATE, null)));

        assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(scenario.drafts.all()).isEmpty();
    }

    @Test
    void aTeacherWithoutALinkCannotStart() {
        Actor unlinked = Actor.teacher(UUID.randomUUID(), ActionOrigin.TELEGRAM);

        assertThat(
                        catchDomain(
                                        () ->
                                                scenario.start()
                                                        .run(unlinked, command(Kind.CREATE, null)))
                                .code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void onlyTheTeacherOnTelegramMayStart() {
        for (Actor denied :
                new Actor[] {
                    ActorFixtures.administratorWeb(),
                    Actor.teacher(scenario.teacher.id(), ActionOrigin.WEB),
                    ActorFixtures.initialLoad()
                }) {
            assertThat(
                            catchDomain(
                                            () ->
                                                    scenario.start()
                                                            .run(
                                                                    denied,
                                                                    command(Kind.CREATE, null)))
                                    .code())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
        assertThat(scenario.drafts.all()).isEmpty();
    }

    @Test
    void eachKindChecksTheMatchingOperationOfTheMatrix() {
        Operation[] operations = {
            Operation.STUDENT_REGISTER,
            Operation.STUDENT_UPDATE,
            Operation.STUDENT_INACTIVATE,
            Operation.STUDENT_REACTIVATE
        };
        Kind[] kinds = {Kind.CREATE, Kind.UPDATE, Kind.INACTIVATE, Kind.REACTIVATE};
        for (int denied = 0; denied < kinds.length; denied++) {
            DraftScenario restricted =
                    new DraftScenario(DraftScenario.matrixDenying(operations[denied]));
            Student own = restricted.ownStudent();
            for (int kind = 0; kind < kinds.length; kind++) {
                StartStudentRegistrationUseCase.Command command =
                        new StartStudentRegistrationUseCase.Command(
                                restricted.link.id(),
                                kinds[kind],
                                kinds[kind] == Kind.CREATE ? null : own.id());
                if (kind == denied) {
                    assertThatThrownBy(() -> restricted.start().run(restricted.actor, command))
                            .isInstanceOfSatisfying(
                                    DomainException.class,
                                    e ->
                                            assertThat(e.code())
                                                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
                } else {
                    assertThat(restricted.start().run(restricted.actor, command).kind())
                            .isEqualTo(kinds[kind]);
                }
            }
        }
    }

    @Test
    void theResultWithoutATokenKeepsTheKindAndTheDeadline() {
        var deadline = scenario.clock.now();
        var result = new StartStudentRegistrationUseCase.Result(Kind.CREATE, deadline);

        assertThat(result.kind()).isEqualTo(Kind.CREATE);
        assertThat(result.expiresAt()).isEqualTo(deadline);
        assertThat(result.confirmationToken()).isNull();
        assertThat(result.toString()).contains("CREATE");
    }

    @Test
    void resultAndCommandNeverRevealTheTokenOrTheStudentInToString() {
        Student student = scenario.ownStudent();
        StartStudentRegistrationUseCase.Result result =
                scenario.start().run(scenario.actor, command(Kind.INACTIVATE, student.id()));

        assertThat(result.toString()).doesNotContain(result.confirmationToken());
    }
}
