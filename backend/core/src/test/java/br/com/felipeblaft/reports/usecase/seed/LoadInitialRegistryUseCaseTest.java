package br.com.felipeblaft.reports.usecase.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.usecase.seed.SeedFile.SeedStudent;
import br.com.felipeblaft.reports.usecase.seed.SeedFile.SeedTeacher;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.Count;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.SeedError;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A carga inicial só orquestra os casos de uso da administração (SC-011): estes testes usam dublês
 * deles (regra 5 do tasks.md) e conferem ator, tudo ou nada, idempotência e erros.
 */
class LoadInitialRegistryUseCaseTest {

    private static final String CPF_MATTEO = "529.982.247-25";
    private static final String CPF_OLAVO = "123.456.789-09";
    private static final String PHONE = "(11) 91234-5678";

    private final RegistryUseCaseDoubles world = new RegistryUseCaseDoubles();
    private final LoadInitialRegistryUseCase useCase = world.loadUseCase();

    private static SeedStudent student(
            String cpf,
            String name,
            String birth,
            String guardianName,
            String instrument,
            String... teacherEmails) {
        boolean guardian = guardianName != null;
        return new SeedStudent(
                cpf,
                name,
                birth,
                guardianName,
                guardian ? PHONE : null,
                guardian ? "responsavel@example.test" : null,
                instrument,
                List.of(teacherEmails));
    }

    private static SeedFile example() {
        return new SeedFile(
                List.of(
                        new SeedTeacher("Clara@Example.test", "Clara Souza", true),
                        new SeedTeacher("bruno@example.test", "Bruno Lima", null)),
                List.of(
                        student(
                                CPF_MATTEO,
                                "Matteo Mafra",
                                "2014-05-10",
                                "Responsável Fictício",
                                "Piano",
                                "clara@example.test"),
                        student(
                                CPF_OLAVO,
                                "Olavo Torrente",
                                "1990-02-01",
                                null,
                                " ",
                                "clara@example.test",
                                "CLARA@example.test",
                                "bruno@example.test")));
    }

    @Test
    void seedCreatesTeachersStudentsAssignmentsAndReportsCodes() {
        SeedResult result = useCase.run(example(), false);

        assertThat(result.success()).isTrue();
        assertThat(result.simulation()).isFalse();
        assertThat(result.teachers()).isEqualTo(new Count(2, 0, 0));
        assertThat(result.students()).isEqualTo(new Count(2, 0, 0));
        assertThat(result.createdAssignments()).isEqualTo(3);
        assertThat(result.issuedCodes())
                .extracting(c -> c.email(), c -> c.code(), c -> c.validUntil())
                .containsExactly(
                        tuple(
                                "clara@example.test",
                                "CODE0001",
                                world.clock.now().plus(Duration.ofDays(7))),
                        tuple(
                                "bruno@example.test",
                                "CODE0002",
                                world.clock.now().plus(Duration.ofDays(7))));
        assertThat(result.issuedCodes().getFirst().toString()).doesNotContain("CODE0001");

        Teacher clara = world.teachers.searchByEmail("clara@example.test").orElseThrow();
        Student matteo = world.students.searchByCpfHash("cpf:52998224725").orElseThrow();
        assertThat(matteo.guardianPhone()).isEqualTo("11912345678");
        assertThat(matteo.guardianEmail()).isEqualTo("responsavel@example.test");
        Student olavo = world.students.searchByCpfHash("cpf:12345678909").orElseThrow();
        assertThat(olavo.instrument()).isNull();
        assertThat(world.students.listByTeacher(clara.id()))
                .extracting(Student::name)
                .containsExactly("Matteo Mafra", "Olavo Torrente");
        assertThat(world.transaction.committed).isEqualTo(1);
        assertThat(world.transaction.rolledBack).isZero();
    }

    @Test
    void everyCallUsesTheSystemActorOfTheInitialLoad() {
        useCase.run(example(), false);

        List<AuthorshipRecord> records = world.authorshipRecorder.records();
        assertThat(records).hasSize(2 + 2 + 3);
        assertThat(records)
                .allSatisfy(
                        record -> {
                            assertThat(record.origin()).isEqualTo(ActionOrigin.INITIAL_LOAD);
                            assertThat(record.authorId()).isNull();
                            assertThat(record.authorRole()).isNull();
                            assertThat(record.correlationId()).startsWith("load-");
                        });
        assertThat(records)
                .extracting(AuthorshipRecord::operation)
                .containsOnly(
                        Operation.TEACHER_REGISTER.name(),
                        Operation.STUDENT_REGISTER.name(),
                        Operation.ASSIGNMENT_ASSIGN.name());
        assertThat(world.calls)
                .containsExactly(
                        "registerTeacher",
                        "registerTeacher",
                        "registerStudent",
                        "assign",
                        "registerStudent",
                        "assign",
                        "assign");
    }

    @Test
    void secondRunIsIdempotent() {
        useCase.run(example(), false);
        world.calls.clear();

        SeedResult result = useCase.run(example(), false);

        assertThat(result.teachers()).isEqualTo(new Count(0, 0, 2));
        assertThat(result.students()).isEqualTo(new Count(0, 0, 2));
        assertThat(result.createdAssignments()).isZero();
        assertThat(result.issuedCodes()).isEmpty();
        assertThat(world.teachers.all()).hasSize(2);
        assertThat(world.students.all()).hasSize(2);
        assertThat(world.assignments.all()).hasSize(3);
        assertThat(world.calls).doesNotContain("registerTeacher", "registerStudent");
    }

    @Test
    void updatesWhatChangedAndAssignsTeacherThatComesFromTheDatabase() {
        useCase.run(example(), false);
        SeedFile change =
                new SeedFile(
                        List.of(new SeedTeacher("clara@example.test", "Clara S. Souza", true)),
                        List.of(
                                student(
                                        CPF_MATTEO,
                                        "Matteo Mafra",
                                        "2014-05-10",
                                        "Responsável Fictício",
                                        "Violão",
                                        "bruno@example.test")));
        world.calls.clear();

        SeedResult result = useCase.run(change, false);

        assertThat(result.teachers()).isEqualTo(new Count(0, 1, 0));
        assertThat(result.students()).isEqualTo(new Count(0, 1, 0));
        assertThat(result.createdAssignments()).isEqualTo(1);
        assertThat(world.calls).containsExactly("updateTeacher", "updateStudent", "assign");
        assertThat(world.teachers.searchByEmail("clara@example.test").orElseThrow().name())
                .isEqualTo("Clara S. Souza");
        assertThat(world.students.searchByCpfHash("cpf:52998224725").orElseThrow().instrument())
                .isEqualTo("Violão");
    }

    @Test
    void newInactiveTeacherIsRegisteredThenInactivatedAndGetsNoCode() {
        SeedFile file =
                new SeedFile(
                        List.of(new SeedTeacher("lia@example.test", "Lia Prado", false)),
                        List.of());

        SeedResult result = useCase.run(file, false);

        assertThat(result.teachers()).isEqualTo(new Count(1, 0, 0));
        assertThat(result.issuedCodes()).isEmpty();
        assertThat(world.calls).containsExactly("registerTeacher", "inactivateTeacher");
        assertThat(world.teachers.searchByEmail("lia@example.test").orElseThrow().active())
                .isFalse();
    }

    @Test
    void existingTeacherChangesSituationAndReactivationReportsTheNewCode() {
        useCase.run(example(), false);
        world.calls.clear();
        SeedFile inactivate =
                new SeedFile(
                        List.of(
                                new SeedTeacher("clara@example.test", "Clara Souza", false),
                                new SeedTeacher("bruno@example.test", "Bruno Lima", true)),
                        List.of());

        SeedResult first = useCase.run(inactivate, false);

        assertThat(first.teachers()).isEqualTo(new Count(0, 1, 1));
        assertThat(world.calls).containsExactly("inactivateTeacher");

        world.calls.clear();
        SeedFile reactivate =
                new SeedFile(
                        List.of(new SeedTeacher("clara@example.test", "Clara Souza", true)),
                        List.of());

        SeedResult second = useCase.run(reactivate, false);

        assertThat(second.teachers()).isEqualTo(new Count(0, 1, 0));
        assertThat(world.calls).containsExactly("reactivateTeacher");
        assertThat(second.issuedCodes())
                .extracting(c -> c.email())
                .containsExactly("clara@example.test");
    }

    @Test
    void simulationRunsTheSameUseCasesInATransactionThatIsRolledBack() {
        SeedResult result = useCase.run(example(), true);

        assertThat(result.success()).isTrue();
        assertThat(result.simulation()).isTrue();
        assertThat(result.teachers()).isEqualTo(new Count(2, 0, 0));
        assertThat(result.students()).isEqualTo(new Count(2, 0, 0));
        assertThat(result.createdAssignments()).isEqualTo(3);
        assertThat(result.issuedCodes()).isEmpty();
        assertThat(world.transaction.rolledBack).isEqualTo(1);
        assertThat(world.transaction.committed).isZero();
        assertThat(world.calls).contains("registerTeacher", "registerStudent", "assign");
    }

    @Test
    void simulationReportsTheSameErrorsAsTheRealRun() {
        SeedFile file =
                new SeedFile(
                        List.of(),
                        List.of(student("111.111.111-11", "Ana", "2000-01-01", null, null)));

        SeedResult simulated = useCase.run(file, true);
        SeedResult real = useCase.run(file, false);

        assertThat(simulated.errors()).isEqualTo(real.errors()).hasSize(1);
        assertThat(simulated.simulation()).isTrue();
        assertThat(real.simulation()).isFalse();
    }

    @Test
    void anyErrorRollsEverythingBackAndIdentifiesTheLineWithoutTheCpfValue() {
        SeedFile file =
                new SeedFile(
                        List.of(
                                new SeedTeacher("clara@example.test", "Clara", true),
                                new SeedTeacher("CLARA@example.test", "Outra", true),
                                new SeedTeacher("without-at-sign", "X", true),
                                new SeedTeacher(null, "Y", true),
                                new SeedTeacher("bruno@example.test", " ", true)),
                        List.of(
                                student(
                                        CPF_MATTEO,
                                        "Matteo",
                                        "2014-05-10",
                                        "Resp",
                                        null,
                                        "clara@example.test"),
                                student("529.982.247-25", "Duplicado", "2000-01-01", null, null),
                                student(
                                        "111.111.111-11",
                                        "",
                                        "2030-01-01",
                                        null,
                                        null,
                                        "nobody@example.test"),
                                new SeedStudent(
                                        CPF_OLAVO,
                                        "Menor",
                                        "2015-01-01",
                                        "  ",
                                        null,
                                        null,
                                        null,
                                        Arrays.asList((String) null)),
                                student("390.533.447-05", "Data ruim", "10/05/2014", null, null),
                                student("245.781.963-64", "Sem data", null, null, null),
                                student(
                                        "704.512.836-35",
                                        "Com professor inválido",
                                        "2000-01-01",
                                        null,
                                        null,
                                        "bruno@example.test")));

        SeedResult result = useCase.run(file, false);

        assertThat(result.success()).isFalse();
        assertThat(result.errors())
                .extracting(SeedError::section, SeedError::index, SeedError::field, SeedError::code)
                .containsExactlyInAnyOrder(
                        tuple("teachers", 1, "email", ErrorCode.DUPLICATE_EMAIL_IN_FILE),
                        tuple("teachers", 2, "name", ErrorCode.INVALID_NAME),
                        tuple("teachers", 2, "email", ErrorCode.INVALID_EMAIL),
                        tuple("teachers", 3, "name", ErrorCode.INVALID_NAME),
                        tuple("teachers", 3, "email", ErrorCode.INVALID_EMAIL),
                        tuple("teachers", 4, "name", ErrorCode.INVALID_NAME),
                        tuple("students", 1, "cpf", ErrorCode.DUPLICATE_CPF_IN_FILE),
                        tuple("students", 2, "name", ErrorCode.INVALID_NAME),
                        tuple("students", 2, "cpf", ErrorCode.INVALID_CPF),
                        tuple("students", 2, "birthDate", ErrorCode.INVALID_BIRTH_DATE),
                        tuple("students", 2, "guardian", ErrorCode.GUARDIAN_REQUIRED),
                        tuple("students", 2, "teachers[0]", ErrorCode.TEACHER_NOT_FOUND),
                        tuple("students", 3, "guardian", ErrorCode.GUARDIAN_REQUIRED),
                        tuple("students", 3, "teachers[0]", ErrorCode.TEACHER_NOT_FOUND),
                        tuple("students", 4, "birthDate", ErrorCode.INVALID_BIRTH_DATE),
                        tuple("students", 5, "birthDate", ErrorCode.INVALID_BIRTH_DATE));
        assertThat(result.errors().toString()).doesNotContain("52998224725", "529.982");
        assertThat(result.teachers()).isEqualTo(new Count(0, 0, 0));
        assertThat(result.students()).isEqualTo(new Count(0, 0, 0));
        assertThat(result.createdAssignments()).isZero();
        assertThat(result.issuedCodes()).isEmpty();
        assertThat(world.transaction.rolledBack).isEqualTo(1);
        assertThat(world.transaction.committed).isZero();
    }

    @Test
    void minorWithoutGuardianPhoneOrEmailIsRefusedByTheSharedValidation() {
        SeedFile file =
                new SeedFile(
                        List.of(),
                        List.of(
                                new SeedStudent(
                                        CPF_MATTEO,
                                        "Matteo",
                                        "2014-05-10",
                                        "Responsável Fictício",
                                        null,
                                        null,
                                        null,
                                        List.of())));

        SeedResult result = useCase.run(file, false);

        assertThat(result.errors())
                .extracting(SeedError::field, SeedError::code)
                .containsExactly(tuple("guardian", ErrorCode.GUARDIAN_REQUIRED));
    }

    @Test
    void invalidGuardianContactComesFromTheSharedValidationWithItsFields() {
        SeedFile file =
                new SeedFile(
                        List.of(),
                        List.of(
                                new SeedStudent(
                                        CPF_MATTEO,
                                        "Matteo",
                                        "2014-05-10",
                                        "Responsável Fictício",
                                        "123",
                                        "sem-arroba",
                                        null,
                                        List.of())));

        SeedResult result = useCase.run(file, false);

        assertThat(result.errors())
                .extracting(SeedError::field, SeedError::code)
                .containsExactlyInAnyOrder(
                        tuple("guardian.phone", ErrorCode.INVALID_PHONE),
                        tuple("guardian.email", ErrorCode.INVALID_EMAIL));
    }

    @Test
    void errorOfAnAssignmentIsReportedOnTheStudentLineAndRollsBack() {
        world.assignmentFailure = new DomainException(ErrorCode.RESOURCE_NOT_FOUND);
        SeedFile file =
                new SeedFile(
                        List.of(new SeedTeacher("clara@example.test", "Clara", true)),
                        List.of(
                                student(
                                        CPF_OLAVO,
                                        "Olavo Torrente",
                                        "1990-02-01",
                                        null,
                                        null,
                                        "clara@example.test")));

        SeedResult result = useCase.run(file, false);

        assertThat(result.errors())
                .extracting(SeedError::section, SeedError::index, SeedError::field, SeedError::code)
                .containsExactly(tuple("students", 0, "teachers[0]", ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(world.transaction.rolledBack).isEqualTo(1);
    }

    @Test
    void errorCodeWithoutFieldsIsMappedToTheFieldItBelongsTo() {
        world.studentRegistrationFailure = new DomainException(ErrorCode.DUPLICATE_CPF);
        SeedFile file =
                new SeedFile(
                        List.of(), List.of(student(CPF_OLAVO, "Olavo", "1990-02-01", null, null)));

        SeedResult result = useCase.run(file, false);

        assertThat(result.errors())
                .extracting(SeedError::field, SeedError::code)
                .containsExactly(tuple("cpf", ErrorCode.DUPLICATE_CPF));
    }

    @Test
    void unexpectedFailureRollsBackAndPropagates() {
        world.studentRegistrationFailure = new IllegalStateException("falha inesperada");
        SeedFile file =
                new SeedFile(
                        List.of(), List.of(student(CPF_OLAVO, "Olavo", "1990-02-01", null, null)));

        assertThatThrownBy(() -> useCase.run(file, false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(world.transaction.rolledBack).isEqualTo(1);
    }

    @Test
    void emptyFileIsASuccessWithZeroCounts() {
        SeedResult result = useCase.run(new SeedFile(null, null), false);

        assertThat(result.success()).isTrue();
        assertThat(result.teachers()).isEqualTo(new Count(0, 0, 0));
        assertThat(result.students()).isEqualTo(new Count(0, 0, 0));
    }

    @Test
    void studentWithTeacherAlreadyInTheDatabaseDoesNotNeedTheTeacherInTheFile() {
        useCase.run(example(), false);
        SeedFile onlyStudent =
                new SeedFile(
                        null,
                        List.of(
                                student(
                                        "704.512.836-35",
                                        "Miguel de Castro Torrente",
                                        "2012-03-03",
                                        "Resp",
                                        "Violino",
                                        "Clara@Example.test")));

        SeedResult result = useCase.run(onlyStudent, false);

        assertThat(result.success()).isTrue();
        assertThat(result.students()).isEqualTo(new Count(1, 0, 0));
        assertThat(result.createdAssignments()).isEqualTo(1);
    }

    @Test
    void teacherThatFailedItsOwnValidationDoesNotAddNoiseToTheStudentLine() {
        SeedFile file =
                new SeedFile(
                        List.of(new SeedTeacher("ok@example.test", " ", true)),
                        List.of(
                                student(
                                        CPF_OLAVO,
                                        "Olavo Torrente",
                                        "1990-02-01",
                                        null,
                                        null,
                                        "ok@example.test")));

        SeedResult result = useCase.run(file, false);

        assertThat(result.errors())
                .extracting(SeedError::section, SeedError::field)
                .containsExactly(tuple("teachers", "name"));
    }
}
