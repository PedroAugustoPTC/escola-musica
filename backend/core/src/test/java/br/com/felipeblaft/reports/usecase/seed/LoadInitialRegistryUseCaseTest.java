package br.com.felipeblaft.reports.usecase.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.FakeDataProtection;
import br.com.felipeblaft.reports.doubles.FixedAccessCodeGenerator;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAssignmentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryStudentRepository;
import br.com.felipeblaft.reports.doubles.InMemoryTeacherRepository;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.usecase.seed.SeedFile.SeedStudent;
import br.com.felipeblaft.reports.usecase.seed.SeedFile.SeedTeacher;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.Count;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.SeedError;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoadInitialRegistryUseCaseTest {

    private static final String CPF_MATTEO = "529.982.247-25";
    private static final String CPF_OLAVO = "123.456.789-09";

    private final FixedClock clock = new FixedClock(Instant.parse("2026-09-21T13:00:00Z"));
    private final InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
    private final InMemoryAssignmentRepository assignments = new InMemoryAssignmentRepository();
    private final InMemoryStudentRepository students = new InMemoryStudentRepository(assignments);
    private final FakeDataProtection protection = new FakeDataProtection();
    private final FixedAccessCodeGenerator generator =
            new FixedAccessCodeGenerator("K7QM2XPA", "BRUN2XPA");
    private final LoadInitialRegistryUseCase useCase =
            new LoadInitialRegistryUseCase(
                    teachers,
                    students,
                    assignments,
                    protection,
                    new AccessCodeIssuanceService(
                            generator, protection, teachers, BusinessParameters.defaults()),
                    clock);

    private static SeedFile example() {
        return new SeedFile(
                List.of(
                        new SeedTeacher("Clara@Example.test", "Clara Souza", true),
                        new SeedTeacher("bruno@example.test", "Bruno Lima", null)),
                List.of(
                        new SeedStudent(
                                CPF_MATTEO,
                                "Matteo Mafra",
                                "2014-05-10",
                                "Responsável Fictício",
                                "Piano",
                                List.of("clara@example.test")),
                        new SeedStudent(
                                CPF_OLAVO,
                                "Olavo Torrente",
                                "1990-02-01",
                                null,
                                " ",
                                List.of(
                                        "clara@example.test",
                                        "CLARA@example.test",
                                        "bruno@example.test"))));
    }

    @Test
    void seedCreatesTeachersStudentsAssignmentsAndEmitsCodes() {
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
                                "K7QM2XPA",
                                clock.now().plus(Duration.ofDays(7))),
                        tuple(
                                "bruno@example.test",
                                "BRUN2XPA",
                                clock.now().plus(Duration.ofDays(7))));
        assertThat(result.issuedCodes().getFirst().toString()).doesNotContain("K7QM2XPA");

        Teacher clara = teachers.searchByEmail("clara@example.test").orElseThrow();
        assertThat(clara.accessCodeHash()).isEqualTo("code:K7QM2XPA");
        assertThat(teachers.searchByEmail("bruno@example.test").orElseThrow().active()).isTrue();

        Student matteo = students.searchByCpfHash("cpf:52998224725").orElseThrow();
        assertThat(new String(matteo.encryptedCpf())).isEqualTo("encrypted:52742289925");
        assertThat(matteo.guardianName()).isEqualTo("Responsável Fictício");
        Student olavo = students.searchByCpfHash("cpf:12345678909").orElseThrow();
        assertThat(olavo.instrument()).isNull();
        assertThat(students.listByTeacher(clara.id()))
                .extracting(Student::name)
                .containsExactly("Matteo Mafra", "Olavo Torrente");
    }

    @Test
    void secondRunIsIdempotent() {
        useCase.run(example(), false);
        int recordings = teachers.recordings();

        SeedResult result = useCase.run(example(), false);

        assertThat(result.teachers()).isEqualTo(new Count(0, 0, 2));
        assertThat(result.students()).isEqualTo(new Count(0, 0, 2));
        assertThat(result.createdAssignments()).isZero();
        assertThat(result.issuedCodes()).isEmpty();
        assertThat(teachers.recordings()).isEqualTo(recordings);
        assertThat(assignments.all()).hasSize(3);
    }

    @Test
    void updatesExistingRegistriesAndAssignsDatabaseTeacher() {
        useCase.run(example(), false);
        SeedFile change =
                new SeedFile(
                        List.of(new SeedTeacher("clara@example.test", "Clara S. Souza", false)),
                        List.of(
                                new SeedStudent(
                                        CPF_MATTEO,
                                        "Matteo Mafra",
                                        "2014-05-10",
                                        "Responsável Fictício",
                                        "Violão",
                                        List.of("bruno@example.test"))));

        SeedResult result = useCase.run(change, false);

        assertThat(result.teachers()).isEqualTo(new Count(0, 1, 0));
        assertThat(result.students()).isEqualTo(new Count(0, 1, 0));
        assertThat(result.createdAssignments()).isEqualTo(1);
        assertThat(teachers.searchByEmail("clara@example.test").orElseThrow().active()).isFalse();
    }

    @Test
    void simulationValidatesAndCountsWithoutSaving() {
        SeedResult result = useCase.run(example(), true);

        assertThat(result.success()).isTrue();
        assertThat(result.simulation()).isTrue();
        assertThat(result.teachers()).isEqualTo(new Count(2, 0, 0));
        assertThat(result.students()).isEqualTo(new Count(2, 0, 0));
        assertThat(result.createdAssignments()).isEqualTo(3);
        assertThat(result.issuedCodes()).isEmpty();
        assertThat(teachers.all()).isEmpty();
        assertThat(students.all()).isEmpty();
        assertThat(assignments.all()).isEmpty();
    }

    @Test
    void simulationOverExistingDataDoesNotSave() {
        useCase.run(example(), false);
        SeedFile change =
                new SeedFile(
                        List.of(new SeedTeacher("clara@example.test", "Outro Nome", true)),
                        List.of(
                                new SeedStudent(
                                        CPF_MATTEO,
                                        "Outro",
                                        "2014-05-10",
                                        "R",
                                        null,
                                        List.of("bruno@example.test"))));
        int recordings = teachers.recordings();

        SeedResult result = useCase.run(change, true);

        assertThat(result.teachers()).isEqualTo(new Count(0, 1, 0));
        assertThat(result.students()).isEqualTo(new Count(0, 1, 0));
        assertThat(result.createdAssignments()).isEqualTo(1);
        assertThat(teachers.recordings()).isEqualTo(recordings);
        assertThat(assignments.all()).hasSize(3);
    }

    @Test
    void anyErrorPreventsAllSavingAndIdentifiesLineWithoutCpfValue() {
        SeedFile file =
                new SeedFile(
                        List.of(
                                new SeedTeacher("clara@example.test", "Clara", true),
                                new SeedTeacher("CLARA@example.test", " ", true),
                                new SeedTeacher("without-at-sign", "X", true),
                                new SeedTeacher(null, "Y", true)),
                        List.of(
                                new SeedStudent(
                                        CPF_MATTEO,
                                        "Matteo",
                                        "2014-05-10",
                                        "Resp",
                                        null,
                                        List.of("clara@example.test")),
                                new SeedStudent(
                                        "529.982.247-25",
                                        "Duplicado",
                                        "2000-01-01",
                                        null,
                                        null,
                                        List.of()),
                                new SeedStudent(
                                        "111.111.111-11",
                                        "",
                                        "2030-01-01",
                                        null,
                                        null,
                                        List.of("nobody@example.test")),
                                new SeedStudent(
                                        CPF_OLAVO,
                                        "Menor",
                                        "2015-01-01",
                                        "  ",
                                        null,
                                        java.util.Arrays.asList((String) null)),
                                new SeedStudent(
                                        "390.533.447-05",
                                        "Data ruim",
                                        "10/05/2014",
                                        null,
                                        null,
                                        List.of()),
                                new SeedStudent(
                                        "245.781.963-64",
                                        "Sem data",
                                        null,
                                        null,
                                        null,
                                        List.of())));

        SeedResult result = useCase.run(file, false);

        assertThat(result.success()).isFalse();
        assertThat(result.errors())
                .extracting(SeedError::section, SeedError::index, SeedError::field, SeedError::code)
                .containsExactlyInAnyOrder(
                        tuple("teachers", 1, "email", ErrorCode.DUPLICATE_EMAIL_IN_FILE),
                        tuple("teachers", 1, "name", ErrorCode.NAME_REQUIRED),
                        tuple("teachers", 2, "email", ErrorCode.INVALID_EMAIL),
                        tuple("teachers", 3, "email", ErrorCode.INVALID_EMAIL),
                        tuple("students", 1, "cpf", ErrorCode.DUPLICATE_CPF_IN_FILE),
                        tuple("students", 2, "name", ErrorCode.NAME_REQUIRED),
                        tuple("students", 2, "cpf", ErrorCode.INVALID_CPF),
                        tuple("students", 2, "birthDate", ErrorCode.INVALID_BIRTH_DATE),
                        tuple("students", 2, "teachers[0]", ErrorCode.TEACHER_NOT_FOUND),
                        tuple("students", 3, "guardianName", ErrorCode.GUARDIAN_REQUIRED),
                        tuple("students", 3, "teachers[0]", ErrorCode.TEACHER_NOT_FOUND),
                        tuple("students", 4, "birthDate", ErrorCode.INVALID_BIRTH_DATE),
                        tuple("students", 5, "birthDate", ErrorCode.INVALID_BIRTH_DATE));
        assertThat(result.errors().toString()).doesNotContain("52998224725", "529.982");
        assertThat(result.teachers()).isEqualTo(new Count(0, 0, 0));
        assertThat(result.issuedCodes()).isEmpty();
        assertThat(teachers.all()).isEmpty();
        assertThat(students.all()).isEmpty();
    }

    @Test
    void assignmentTeacherCanComeFromDatabase() {
        useCase.run(example(), false);
        SeedFile onlyStudent =
                new SeedFile(
                        null,
                        List.of(
                                new SeedStudent(
                                        "704.512.836-35",
                                        "Miguel de Castro Torrente",
                                        "2012-03-03",
                                        "Resp",
                                        "Violino",
                                        List.of("clara@example.test"))));

        SeedResult result = useCase.run(onlyStudent, false);

        assertThat(result.success()).isTrue();
        assertThat(result.students()).isEqualTo(new Count(1, 0, 0));
        assertThat(result.createdAssignments()).isEqualTo(1);
        assertThat(new SeedFile(null, null).students()).isEmpty();
        assertThat(new SeedStudent("c", "n", "d", null, null, null).teachers()).isEmpty();
        assertThat(LocalDate.parse("2012-03-03")).isNotNull();
    }
}
