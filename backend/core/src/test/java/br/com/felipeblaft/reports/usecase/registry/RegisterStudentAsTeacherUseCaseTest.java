package br.com.felipeblaft.reports.usecase.registry;

import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CORRELATION;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_A;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_B;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_C;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_D;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.adult;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.minor;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.registerCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cadastro de aluno pelo professor, Web e Telegram (US4, FR-024). */
class RegisterStudentAsTeacherUseCaseTest {

    private final RegistryScenario scenario = new RegistryScenario();
    private final Teacher clara = scenario.teacher("Clara Dias");
    private final Teacher bruno = scenario.teacher("Bruno Lima");
    private final Actor claraWeb = ActorFixtures.teacherWeb(clara.id());
    private final Actor claraTelegram = ActorFixtures.teacherTelegram(clara.id());

    private DomainException refusal(Actor actor, RegisterStudentUseCase.Command command) {
        return catchThrowableOfType(
                DomainException.class, () -> scenario.register.run(actor, command));
    }

    private DomainException refusalOnCpf(Actor actor, String cpf) {
        return refusal(actor, registerCommand(adult("Pessoa Teste", cpf)));
    }

    private List<AuthorshipRecord> refusals() {
        return scenario.recorder.ofOperation(AuthorshipRecord.CPF_REFUSED_OTHER_STUDENT);
    }

    @Test
    void firstStudentOfATeacherWithNoStudentsIsBornAssociatedOnlyToThem() {
        StudentView view =
                scenario.register.run(
                        claraWeb,
                        registerCommand(
                                minor("Matteo Mafra", CPF_A), bruno.id(), UUID.randomUUID()));

        assertThat(scenario.assignments.listTeachersOfStudent(view.id()))
                .containsExactly(clara.id());
        assertThat(view.teachers()).as("o professor nunca vê os outros").isEmpty();
        assertThat(view.canInactivate()).isTrue();
        assertThat(view.active()).isTrue();
        assertThat(
                        scenario.students.list(
                                br.com.felipeblaft.reports.domain.registry.StudentScope.ofTeacher(
                                        bruno.id()),
                                br.com.felipeblaft.reports.domain.registry.StudentStatusFilter.ALL))
                .isEmpty();
    }

    @Test
    void recordsTheAuthorshipOfTheTeacherByChannel() {
        StudentView web =
                scenario.register.run(claraWeb, registerCommand(adult("Ana Souza", CPF_A)));
        StudentView telegram =
                scenario.register.run(
                        claraTelegram,
                        new RegisterStudentUseCase.Command(
                                adult("Beto Lima", CPF_B), null, "4711"));

        List<AuthorshipRecord> records = scenario.recorder.ofOperation("STUDENT_REGISTER");
        assertThat(records).hasSize(2);
        assertThat(records.get(0).authorRole()).isEqualTo(Role.TEACHER);
        assertThat(records.get(0).authorId()).isEqualTo(clara.id());
        assertThat(records.get(0).origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(records.get(0).targetId()).isEqualTo(web.id());
        assertThat(records.get(1).origin()).isEqualTo(ActionOrigin.TELEGRAM);
        assertThat(records.get(1).targetId()).isEqualTo(telegram.id());
        assertThat(records.get(1).correlationId()).isEqualTo("4711");
    }

    @Test
    void cpfOfAnotherTeachersStudentIsRefusedNeutrally() {
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null, bruno);

        DomainException error = refusalOnCpf(claraWeb, CPF_A);

        assertThat(error.code()).isEqualTo(ErrorCode.CPF_UNAVAILABLE);
        assertThat(error.details()).isEmpty();
        assertThat(error.getMessage()).isEqualTo("CPF_UNAVAILABLE");
        assertThat(scenario.students.all()).hasSize(1);
        assertThat(scenario.assignments.all()).hasSize(1);
    }

    @Test
    void cpfOfAStudentWithoutTeacherIsRefusedTheSameWay() {
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null);

        DomainException error = refusalOnCpf(claraWeb, CPF_A);

        assertThat(error.code()).isEqualTo(ErrorCode.CPF_UNAVAILABLE);
        assertThat(error.details()).isEmpty();
    }

    @Test
    void cpfOfAStudentOfAnInactiveTeacherIsStillUnavailable() {
        Teacher gone = scenario.teacher("Inativo Silva", false);
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null, gone);

        assertThat(refusalOnCpf(claraWeb, CPF_A).code()).isEqualTo(ErrorCode.CPF_UNAVAILABLE);
    }

    @Test
    void theRefusalLeavesAnIndependentRecordWithoutTheCpfOrTheStudentData() {
        Student other = scenario.student("Ana Souza", "1990-03-04", CPF_A, null, bruno);

        refusalOnCpf(claraTelegram, CPF_A);

        assertThat(scenario.recorder.independent()).hasSize(1);
        AuthorshipRecord record = refusals().getFirst();
        assertThat(record.result()).isEqualTo(AuthorshipResult.DENIED);
        assertThat(record.errorCode()).isEqualTo("CPF_UNAVAILABLE");
        assertThat(record.authorId()).isEqualTo(clara.id());
        assertThat(record.authorRole()).isEqualTo(Role.TEACHER);
        assertThat(record.origin()).isEqualTo(ActionOrigin.TELEGRAM);
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.STUDENT);
        assertThat(record.targetId()).isEqualTo(other.id());
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
        assertThat(record.toString())
                .doesNotContain("52998224725")
                .doesNotContain("Ana")
                .doesNotContain("Pessoa");
        assertThat(scenario.recorder.inTransaction()).isEmpty();
    }

    @Test
    void cpfOfOneOfTheirOwnStudentsSaysSoAndLeavesNoRefusalRecord() {
        Student own = scenario.student("Ana Souza", "1990-03-04", CPF_A, null, clara);
        own.inactivate(scenario.clock.now());

        DomainException error = refusalOnCpf(claraWeb, CPF_A);

        assertThat(error.code()).isEqualTo(ErrorCode.STUDENT_ALREADY_YOURS);
        assertThat(error.details()).isEmpty();
        assertThat(scenario.recorder.isEmpty()).isTrue();
        assertThat(scenario.students.all()).hasSize(1);
    }

    @Test
    void aStudentSharedWithAnotherTeacherIsStillYoursToTheOneWhoHasIt() {
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null, clara, bruno);

        assertThat(refusalOnCpf(claraWeb, CPF_A).code()).isEqualTo(ErrorCode.STUDENT_ALREADY_YOURS);
        assertThat(refusalOnCpf(ActorFixtures.teacherWeb(bruno.id()), CPF_A).code())
                .isEqualTo(ErrorCode.STUDENT_ALREADY_YOURS);
    }

    @Test
    void teacherOnlyEverReceivesTheNeutralCodesForDuplicateCpf() {
        scenario.student("De Bruno", "1990-03-04", CPF_A, null, bruno);
        scenario.student("De Clara", "1990-03-04", CPF_B, null, clara);
        scenario.student("De Ninguem", "1990-03-04", CPF_C, null);

        for (Actor actor : List.of(claraWeb, claraTelegram)) {
            for (String cpf : List.of(CPF_A, CPF_B, CPF_C)) {
                DomainException error = refusalOnCpf(actor, cpf);
                assertThat(error.code())
                        .isIn(ErrorCode.CPF_UNAVAILABLE, ErrorCode.STUDENT_ALREADY_YOURS)
                        .isNotEqualTo(ErrorCode.DUPLICATE_CPF);
                assertThat(error.details()).isEmpty();
            }
            scenario.clock.advance(Duration.ofHours(2));
        }
    }

    @Test
    void duplicateCaughtOnlyByTheInsertIsStillNeutral() {
        RegistryScenario racing =
                new RegistryScenario(RegisterStudentUseCaseTest.RacingStudents::new);
        Teacher other = racing.teacher("Bruno Lima");
        Teacher self = racing.teacher("Clara Dias");
        racing.student("Ana Souza", "1990-03-04", CPF_A, null, other);

        DomainException error =
                catchThrowableOfType(
                        DomainException.class,
                        () ->
                                racing.register.run(
                                        ActorFixtures.teacherWeb(self.id()),
                                        registerCommand(adult("Pessoa", CPF_A))));

        assertThat(error.code()).isEqualTo(ErrorCode.CPF_UNAVAILABLE);
        assertThat(racing.recorder.ofOperation(AuthorshipRecord.CPF_REFUSED_OTHER_STUDENT))
                .hasSize(1);
    }

    @Test
    void fiveRefusalsInFifteenMinutesBlockCpfOperationsForFifteenMinutes() {
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null, bruno);
        for (int i = 0; i < 5; i++) {
            assertThat(refusalOnCpf(claraWeb, CPF_A).code()).isEqualTo(ErrorCode.CPF_UNAVAILABLE);
        }

        DomainException blocked = refusalOnCpf(claraWeb, CPF_D);

        assertThat(blocked.code()).isEqualTo(ErrorCode.OPERATION_TEMPORARILY_BLOCKED);
        assertThat(blocked.details()).containsEntry("remainingSeconds", 900L);
        assertThat(scenario.students.all()).as("nada foi gravado").hasSize(1);
        AuthorshipRecord record =
                scenario.recorder.ofOperation(AuthorshipRecord.ATTEMPT_BLOCKED).getFirst();
        assertThat(record.result()).isEqualTo(AuthorshipResult.BLOCKED);
        assertThat(record.errorCode()).isEqualTo("OPERATION_TEMPORARILY_BLOCKED");
        assertThat(refusals()).as("a recusa durante o bloqueio não é nova").hasSize(5);

        scenario.clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(refusalOnCpf(claraWeb, CPF_D).code())
                .isEqualTo(ErrorCode.OPERATION_TEMPORARILY_BLOCKED);
        scenario.clock.advance(Duration.ofSeconds(1));
        StudentView view =
                scenario.register.run(claraWeb, registerCommand(adult("Livre Pessoa", CPF_D)));
        assertThat(view.name()).isEqualTo("Livre Pessoa");
    }

    @Test
    void refusalsOutsideTheFifteenMinuteWindowDoNotAddUp() {
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null, bruno);
        for (int i = 0; i < 4; i++) {
            refusalOnCpf(claraWeb, CPF_A);
        }
        scenario.clock.advance(Duration.ofMinutes(16));

        assertThat(refusalOnCpf(claraWeb, CPF_A).code()).isEqualTo(ErrorCode.CPF_UNAVAILABLE);
        assertThat(refusalOnCpf(claraWeb, CPF_A).code()).isEqualTo(ErrorCode.CPF_UNAVAILABLE);
    }

    @Test
    void blockOnlyAffectsOperationsWithCpf() {
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null, bruno);
        Student mine = scenario.student("Minha Aluna", "1990-03-04", CPF_B, null, clara);
        for (int i = 0; i < 5; i++) {
            refusalOnCpf(claraWeb, CPF_A);
        }
        assertThat(refusalOnCpf(claraWeb, CPF_D).code())
                .isEqualTo(ErrorCode.OPERATION_TEMPORARILY_BLOCKED);

        assertThat(
                        scenario.list
                                .run(claraWeb, new ListStudentsUseCase.Command(null, null, null))
                                .items())
                .hasSize(1);
        assertThat(
                        scenario.get
                                .run(
                                        claraWeb,
                                        new GetStudentUseCase.Command(mine.id(), CORRELATION))
                                .id())
                .isEqualTo(mine.id());
    }

    @Test
    void theBlockIsPerTeacherAndSharedAcrossChannels() {
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null, bruno);
        for (int i = 0; i < 3; i++) {
            refusalOnCpf(claraWeb, CPF_A);
        }
        for (int i = 0; i < 2; i++) {
            refusalOnCpf(claraTelegram, CPF_A);
        }

        assertThat(refusalOnCpf(claraTelegram, CPF_D).code())
                .isEqualTo(ErrorCode.OPERATION_TEMPORARILY_BLOCKED);
        assertThat(refusalOnCpf(claraWeb, CPF_D).code())
                .isEqualTo(ErrorCode.OPERATION_TEMPORARILY_BLOCKED);
        assertThat(
                        scenario.register
                                .run(
                                        ActorFixtures.teacherWeb(bruno.id()),
                                        registerCommand(adult("Livre Pessoa", CPF_D)))
                                .name())
                .isEqualTo("Livre Pessoa");
    }

    @Test
    void ownStudentAnswersDoNotCountAsRefusals() {
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null, clara);
        for (int i = 0; i < 8; i++) {
            assertThat(refusalOnCpf(claraWeb, CPF_A).code())
                    .isEqualTo(ErrorCode.STUDENT_ALREADY_YOURS);
        }

        StudentView view =
                scenario.register.run(claraWeb, registerCommand(adult("Livre Pessoa", CPF_D)));

        assertThat(view.name()).isEqualTo("Livre Pessoa");
    }

    @Test
    void invalidCpfNeitherCountsNorConsultsTheDuplicates() {
        for (int i = 0; i < 40; i++) {
            DomainException error = refusalOnCpf(claraWeb, "111.111.111-11");
            assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        assertThat(scenario.attemptsRepository.all()).isEmpty();
    }

    @Test
    void thirtyLookupsInSixtyMinutesBlockCpfOperationsForSixtyMinutes() {
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null, clara);
        for (int i = 0; i < 29; i++) {
            assertThat(refusalOnCpf(claraWeb, CPF_A).code())
                    .isEqualTo(ErrorCode.STUDENT_ALREADY_YOURS);
        }
        // a consulta de um CPF livre também conta: é a 30ª
        scenario.register.run(claraWeb, registerCommand(adult("Livre Pessoa", CPF_D)));

        DomainException blocked = refusalOnCpf(claraWeb, CPF_A);

        assertThat(blocked.code()).isEqualTo(ErrorCode.OPERATION_TEMPORARILY_BLOCKED);
        assertThat(blocked.details()).containsEntry("remainingSeconds", 3600L);
        scenario.clock.advance(Duration.ofMinutes(59));
        assertThat(refusalOnCpf(claraWeb, CPF_A).code())
                .isEqualTo(ErrorCode.OPERATION_TEMPORARILY_BLOCKED);
        scenario.clock.advance(Duration.ofMinutes(1));
        assertThat(refusalOnCpf(claraWeb, CPF_A).code()).isEqualTo(ErrorCode.STUDENT_ALREADY_YOURS);
    }

    @Test
    void theRecheckOfTheTelegramConfirmationDoesNotCountALookup() {
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null, clara);
        for (int i = 0; i < 40; i++) {
            DomainException error =
                    refusal(
                            claraTelegram,
                            new RegisterStudentUseCase.Command(
                                    adult("Pessoa Teste", CPF_A), null, "4711", true));
            assertThat(error.code()).isEqualTo(ErrorCode.STUDENT_ALREADY_YOURS);
        }

        assertThat(scenario.attemptsRepository.all()).isEmpty();
    }

    @Test
    void theRecheckStillHonorsAnActiveBlockAndStillCountsRefusals() {
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null, bruno);
        RegisterStudentUseCase.Command recheck =
                new RegisterStudentUseCase.Command(
                        adult("Pessoa Teste", CPF_A), null, "4711", true);
        for (int i = 0; i < 5; i++) {
            assertThat(refusal(claraTelegram, recheck).code()).isEqualTo(ErrorCode.CPF_UNAVAILABLE);
        }

        assertThat(refusal(claraTelegram, recheck).code())
                .isEqualTo(ErrorCode.OPERATION_TEMPORARILY_BLOCKED);
    }

    @Test
    void validationFailuresComeBeforeAnyLookup() {
        StudentInput noGuardian =
                new StudentInput(
                        "Matteo",
                        "2014-05-10",
                        new br.com.felipeblaft.reports.domain.common.Secret(CPF_A),
                        null,
                        null);

        DomainException error = refusal(claraWeb, registerCommand(noGuardian));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(scenario.attemptsRepository.all()).isEmpty();
        assertThat(scenario.students.all()).isEmpty();
    }

    @Test
    void commandTextRevealsNoPersonalData() {
        RegisterStudentUseCase.Command command =
                new RegisterStudentUseCase.Command(
                        minor("Matteo Mafra", CPF_A), null, "4711", true);

        assertThat(command.toString()).isEqualTo("RegisterStudentUseCase.Command");
        assertThat(
                        new RegisterStudentUseCase.Command(minor("Matteo", CPF_A), null, "4711")
                                .lookupAlreadyCounted())
                .isFalse();
    }

    @Test
    void inactiveTeacherCannotRegisterAndNothingIsSaved() {
        Teacher inactive = scenario.teacher("Diego Inativo", false);
        Actor actor = ActorFixtures.teacherWeb(inactive.id());

        DomainException error = refusal(actor, registerCommand(adult("Pessoa Teste", CPF_A)));

        assertThat(error.code()).isEqualTo(ErrorCode.TEACHER_INACTIVE);
        assertThat(scenario.students.all()).isEmpty();
        assertThat(scenario.attemptsRepository.all()).isEmpty();
    }

    @Test
    void unknownTeacherActorIsNotFound() {
        Actor ghost = ActorFixtures.teacherWeb(UUID.randomUUID());

        DomainException error = refusal(ghost, registerCommand(adult("Pessoa Teste", CPF_A)));

        assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
