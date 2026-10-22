package br.com.felipeblaft.reports.usecase.registry;

import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CORRELATION;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_A;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_B;
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
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.InMemoryAssignmentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryStudentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Cadastro de aluno pelo administrador e pela carga inicial (US3). */
class RegisterStudentUseCaseTest {

    private final RegistryScenario scenario = new RegistryScenario();
    private final Actor admin = ActorFixtures.administratorWeb();

    private DomainException refusal(Actor actor, RegisterStudentUseCase.Command command) {
        return catchThrowableOfType(
                DomainException.class, () -> scenario.register.run(actor, command));
    }

    @Test
    void registersAnAdultAndAssociatesTheChosenTeachers() {
        Teacher clara = scenario.teacher("Clara Dias");
        Teacher bruno = scenario.teacher("Bruno Lima");

        StudentView view =
                scenario.register.run(
                        admin, registerCommand(adult("Ana Souza", CPF_A), clara.id(), bruno.id()));

        assertThat(view.name()).isEqualTo("Ana Souza");
        assertThat(view.active()).isTrue();
        assertThat(view.age()).isEqualTo(36);
        assertThat(view.guardian()).isNull();
        assertThat(view.canInactivate()).isNull();
        assertThat(view.teachers())
                .extracting(StudentView.TeacherRef::name)
                .containsExactly("Bruno Lima", "Clara Dias");
        assertThat(scenario.assignments.listTeachersOfStudent(view.id()))
                .containsExactlyInAnyOrder(clara.id(), bruno.id());
        Student saved = scenario.students.all().getFirst();
        assertThat(saved.active()).isTrue();
        assertThat(saved.id()).isEqualTo(view.id());
    }

    @Test
    void registersWithoutTeachers() {
        StudentView view = scenario.register.run(admin, registerCommand(adult("Ana Souza", CPF_A)));

        assertThat(view.teachers()).isEmpty();
        assertThat(scenario.assignments.all()).isEmpty();
        assertThat(scenario.students.all()).hasSize(1);
    }

    @Test
    void cpfIsStoredProtectedAndNeverReturned() {
        StudentView view = scenario.register.run(admin, registerCommand(adult("Ana Souza", CPF_A)));

        Student saved = scenario.students.all().getFirst();
        Cpf cpf = Cpf.of(CPF_A);
        assertThat(saved.cpfHash()).isEqualTo(scenario.protection.hashCpf(cpf));
        assertThat(saved.encryptedCpf()).isEqualTo(scenario.protection.encryptCpf(cpf));
        assertThat(view.toString()).doesNotContain("52998224725");
        assertThat(view.guardian()).isNull();
    }

    @Test
    void normalizesTextAndKeepsTheGuardianContact() {
        Teacher clara = scenario.teacher("Clara Dias");

        StudentView view =
                scenario.register.run(
                        admin,
                        registerCommand(
                                new StudentInput(
                                        "  Matteo   Mafra ",
                                        "10/05/2014",
                                        new br.com.felipeblaft.reports.domain.common.Secret(CPF_A),
                                        new GuardianInput(
                                                " Maria Silva ",
                                                "(11) 98765-4321",
                                                " Maria@Exemplo.com "),
                                        "  Piano "),
                                clara.id()));

        assertThat(view.birthDate()).isEqualTo(LocalDate.of(2014, 5, 10));
        assertThat(view.age()).isEqualTo(12);
        assertThat(view.instrument()).isEqualTo("Piano");
        assertThat(view.guardian())
                .extracting("name", "phone", "email")
                .containsExactly("Maria Silva", "11987654321", "maria@exemplo.com");
        Student saved = scenario.students.all().getFirst();
        assertThat(saved.guardianPhone()).isEqualTo("11987654321");
        assertThat(saved.guardianEmail()).isEqualTo("maria@exemplo.com");
    }

    @Test
    void recordsTheAuthorshipInTheSameTransaction() {
        StudentView view = scenario.register.run(admin, registerCommand(adult("Ana Souza", CPF_A)));

        assertThat(scenario.recorder.inTransaction()).hasSize(1);
        AuthorshipRecord record = scenario.recorder.records().getFirst();
        assertThat(record.operation()).isEqualTo("STUDENT_REGISTER");
        assertThat(record.authorId()).isEqualTo(admin.id());
        assertThat(record.authorRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(record.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.STUDENT);
        assertThat(record.targetId()).isEqualTo(view.id());
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
        assertThat(record.toString()).doesNotContain("52998224725").doesNotContain("Ana");
    }

    @Test
    void doesNotTouchTheCpfProbeCountersForTheAdministrator() {
        scenario.register.run(admin, registerCommand(adult("Ana Souza", CPF_A)));

        assertThat(scenario.attemptsRepository.all()).isEmpty();
    }

    @Test
    void minorNeedsTheGuardianAndNothingIsSaved() {
        Teacher clara = scenario.teacher("Clara Dias");
        StudentInput noGuardian =
                new StudentInput(
                        "Matteo",
                        "2014-05-10",
                        new br.com.felipeblaft.reports.domain.common.Secret(CPF_A),
                        null,
                        null);

        DomainException error = refusal(admin, registerCommand(noGuardian, clara.id()));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.details().get("fields"))
                .isEqualTo(List.of(Map.of("field", "guardian", "code", "GUARDIAN_REQUIRED")));
        assertThat(scenario.students.all()).isEmpty();
        assertThat(scenario.assignments.all()).isEmpty();
        assertThat(scenario.recorder.isEmpty()).isTrue();
    }

    @Test
    void invalidFieldsAreAllReportedTogetherWithoutRepeatingTheValues() {
        StudentInput bad =
                new StudentInput(
                        "A",
                        "2099-01-01",
                        new br.com.felipeblaft.reports.domain.common.Secret("111.111.111-11"),
                        null,
                        "x".repeat(61));

        DomainException error = refusal(admin, registerCommand(bad));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat((List<?>) error.details().get("fields")).hasSize(4);
        assertThat(error.details().toString()).doesNotContain("111");
        assertThat(scenario.students.all()).isEmpty();
    }

    @Test
    void missingCpfOrInputIsAValidationFailure() {
        StudentInput noCpf = new StudentInput("Ana Souza", "1990-03-04", null, null, null);

        DomainException error = refusal(admin, registerCommand(noCpf));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.details().get("fields"))
                .isEqualTo(List.of(Map.of("field", "cpf", "code", "INVALID_CPF")));
    }

    @Test
    void schoolTimezoneDecidesWhoIsAMinor() {
        // 02:00Z de 22/09 ainda é 21/09 em São Paulo: quem faz 18 anos no dia 22 ainda é menor.
        scenario.clock.define(Instant.parse("2026-09-22T02:00:00Z"));
        StudentInput bornOnThe22nd =
                new StudentInput(
                        "Lia",
                        "2008-09-22",
                        new br.com.felipeblaft.reports.domain.common.Secret(CPF_A),
                        null,
                        null);

        DomainException error = refusal(admin, registerCommand(bornOnThe22nd));
        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);

        scenario.clock.define(Instant.parse("2026-09-22T04:00:00Z"));
        StudentView view = scenario.register.run(admin, registerCommand(bornOnThe22nd));
        assertThat(view.age()).isEqualTo(18);
    }

    @Test
    void duplicateCpfNamesTheExistingStudentAndNeverTheCpf() {
        Teacher clara = scenario.teacher("Clara Dias");
        scenario.student("Ana Souza", "1990-03-04", CPF_A, null, clara);

        DomainException error =
                refusal(admin, registerCommand(adult("Outra Pessoa", CPF_A), clara.id()));

        assertThat(error.code()).isEqualTo(ErrorCode.DUPLICATE_CPF);
        assertThat(error.details()).isEqualTo(Map.of("name", "Ana Souza"));
        assertThat(error.getMessage() + error.details()).doesNotContain("52998224725");
        assertThat(scenario.students.all()).hasSize(1);
        assertThat(scenario.assignments.all()).hasSize(1);
        assertThat(scenario.recorder.isEmpty()).isTrue();
    }

    @Test
    void duplicateAgainstAnInactiveStudentCountsToo() {
        Student existing = scenario.student("Ana Souza", "1990-03-04", CPF_A, null);
        existing.inactivate(scenario.clock.now());

        DomainException error = refusal(admin, registerCommand(adult("Outra", CPF_A)));

        assertThat(error.code()).isEqualTo(ErrorCode.DUPLICATE_CPF);
    }

    @Test
    void duplicateDetectedByTheInsertUnderConcurrencyStillNamesTheStudent() {
        RegistryScenario racing = new RegistryScenario(RacingStudents::new);
        racing.student("Ana Souza", "1990-03-04", CPF_A, null);

        DomainException error =
                catchThrowableOfType(
                        DomainException.class,
                        () -> racing.register.run(admin, registerCommand(adult("Outra", CPF_A))));

        assertThat(error.code()).isEqualTo(ErrorCode.DUPLICATE_CPF);
        assertThat(error.details()).isEqualTo(Map.of("name", "Ana Souza"));
    }

    @Test
    void insertRefusedButOwnerVanishedIsReportedAsNotSaved() {
        RegistryScenario racing = new RegistryScenario(VanishingStudents::new);

        DomainException error =
                catchThrowableOfType(
                        DomainException.class,
                        () -> racing.register.run(admin, registerCommand(adult("Outra", CPF_A))));

        assertThat(error.code()).isEqualTo(ErrorCode.OPERATION_NOT_SAVED);
    }

    @Test
    void inactiveTeacherIsRefusedAndNothingIsSaved() {
        Teacher clara = scenario.teacher("Clara Dias");
        Teacher gone = scenario.teacher("Bruno Lima", false);

        DomainException error =
                refusal(admin, registerCommand(adult("Ana Souza", CPF_A), clara.id(), gone.id()));

        assertThat(error.code()).isEqualTo(ErrorCode.TEACHER_INACTIVE);
        assertThat(scenario.students.all()).isEmpty();
        assertThat(scenario.assignments.all()).isEmpty();
        assertThat(scenario.recorder.isEmpty()).isTrue();
    }

    @Test
    void unknownTeacherIsAFieldViolation() {
        DomainException error =
                refusal(
                        admin,
                        registerCommand(adult("Ana Souza", CPF_A), java.util.UUID.randomUUID()));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.details().get("fields"))
                .isEqualTo(List.of(Map.of("field", "teacherIds", "code", "TEACHER_NOT_FOUND")));
        assertThat(scenario.students.all()).isEmpty();
    }

    @Test
    void repeatedTeacherIsAssociatedOnce() {
        Teacher clara = scenario.teacher("Clara Dias");

        StudentView view =
                scenario.register.run(
                        admin, registerCommand(adult("Ana Souza", CPF_A), clara.id(), clara.id()));

        assertThat(view.teachers()).hasSize(1);
        assertThat(scenario.assignments.all()).hasSize(1);
    }

    @Test
    void nullTeacherListMeansNoTeachers() {
        StudentView view =
                scenario.register.run(
                        admin,
                        new RegisterStudentUseCase.Command(
                                adult("Ana Souza", CPF_A), null, CORRELATION));

        assertThat(view.teachers()).isEmpty();
    }

    @Test
    void administratorWithProvisionalPasswordCannotRegister() {
        DomainException error =
                refusal(
                        ActorFixtures.administratorWithProvisionalPassword(),
                        registerCommand(adult("Ana Souza", CPF_A)));

        assertThat(error.code()).isEqualTo(ErrorCode.PASSWORD_CHANGE_REQUIRED);
        assertThat(scenario.students.all()).isEmpty();
    }

    @Test
    void bootstrapSystemActorCannotRegister() {
        DomainException error =
                refusal(ActorFixtures.bootstrap(), registerCommand(adult("Ana Souza", CPF_A)));

        assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void initialLoadRegistersAsSystemWithoutAuthor() {
        Teacher clara = scenario.teacher("Clara Dias");

        StudentView view =
                scenario.register.run(
                        ActorFixtures.initialLoad(),
                        new RegisterStudentUseCase.Command(
                                minor("Matteo Mafra", CPF_B), List.of(clara.id()), "load-1"));

        assertThat(view.teachers()).hasSize(1);
        AuthorshipRecord record = scenario.recorder.records().getFirst();
        assertThat(record.authorId()).isNull();
        assertThat(record.authorRole()).isNull();
        assertThat(record.origin()).isEqualTo(ActionOrigin.INITIAL_LOAD);
        assertThat(record.correlationId()).isEqualTo("load-1");
    }

    @Test
    void failureToRecordTheAuthorshipPropagatesSoTheTransactionCanRollBack() {
        scenario.recorder.failNextRecordWith(new IllegalStateException("registro indisponível"));

        Throwable error =
                org.assertj.core.api.Assertions.catchThrowable(
                        () -> scenario.register.run(admin, registerCommand(adult("Ana", CPF_A))));

        assertThat(error).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void commandTextRevealsNoPersonalData() {
        RegisterStudentUseCase.Command command = registerCommand(minor("Matteo Mafra", CPF_A));

        assertThat(command.toString()).isEqualTo("RegisterStudentUseCase.Command");
        assertThat(command.input().toString()).doesNotContain("Matteo").doesNotContain("529");
    }

    /**
     * Repositório em que o aluno já existente só é visto pelo insert (corrida entre dois
     * cadastros).
     */
    private static final class RacingStudents extends InMemoryStudentRepository {
        private boolean first = true;

        RacingStudents(InMemoryAssignmentRepository assignments) {
            super(assignments);
        }

        @Override
        public Optional<Student> searchByCpfHash(String cpfHash) {
            if (first) {
                first = false;
                return Optional.empty();
            }
            return super.searchByCpfHash(cpfHash);
        }
    }

    /** Insert recusado e dono do CPF que sumiu em seguida. */
    private static final class VanishingStudents extends InMemoryStudentRepository {
        VanishingStudents(InMemoryAssignmentRepository assignments) {
            super(assignments);
        }

        @Override
        public boolean insertIfAbsent(Student student) {
            return false;
        }
    }
}
