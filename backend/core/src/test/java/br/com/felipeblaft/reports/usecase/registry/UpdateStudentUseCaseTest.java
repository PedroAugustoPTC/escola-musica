package br.com.felipeblaft.reports.usecase.registry;

import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CORRELATION;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_A;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_B;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.updateCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Edição de aluno pelo administrador (US3). */
class UpdateStudentUseCaseTest {

    private final RegistryScenario scenario = new RegistryScenario();
    private final Actor admin = ActorFixtures.administratorWeb();

    private DomainException refusal(Actor actor, UpdateStudentUseCase.Command command) {
        return catchThrowableOfType(
                DomainException.class, () -> scenario.update.run(actor, command));
    }

    private Student registered(Teacher... owners) {
        StudentView view =
                scenario.register.run(
                        admin,
                        RegistryScenario.registerCommand(
                                RegistryScenario.minor("Matteo Mafra", CPF_A),
                                java.util.Arrays.stream(owners)
                                        .map(Teacher::id)
                                        .toArray(UUID[]::new)));
        scenario.recorder.rollBackTransaction();
        return scenario.students.searchById(view.id()).orElseThrow();
    }

    @Test
    void blankCpfKeepsTheCurrentOne() {
        Student student = registered();
        String hash = student.cpfHash();

        for (Secret blank : new Secret[] {null, new Secret(""), new Secret("   ")}) {
            scenario.update.run(
                    admin,
                    updateCommand(
                            student.id(), new StudentInput("Matteo M.", null, blank, null, null)));
            assertThat(student.cpfHash()).isEqualTo(hash);
        }
        assertThat(student.name()).isEqualTo("Matteo M.");
    }

    @Test
    void absentFieldsDoNotChangeAndGuardianStaysWhole() {
        Student student = registered();

        StudentView view =
                scenario.update.run(
                        admin,
                        updateCommand(
                                student.id(), new StudentInput(null, null, null, null, null)));

        assertThat(view.name()).isEqualTo("Matteo Mafra");
        assertThat(view.birthDate()).isEqualTo(LocalDate.of(2014, 5, 10));
        assertThat(view.instrument()).isEqualTo("Piano");
        assertThat(view.guardian().phone()).isEqualTo("11987654321");
        assertThat(scenario.recorder.isEmpty()).as("nada mudou, nada a registrar").isTrue();
    }

    @Test
    void updatesFieldsAndRecordsTheAuthorship() {
        Student student = registered();

        StudentView view =
                scenario.update.run(
                        admin,
                        updateCommand(
                                student.id(),
                                new StudentInput(
                                        "Matteo Mafra Jr.",
                                        "11/05/2014",
                                        null,
                                        new GuardianInput(
                                                "Joana Lima", "1134567890", "joana@exemplo.com"),
                                        "")));

        assertThat(view.name()).isEqualTo("Matteo Mafra Jr.");
        assertThat(view.birthDate()).isEqualTo(LocalDate.of(2014, 5, 11));
        assertThat(view.instrument()).isNull();
        assertThat(view.guardian().name()).isEqualTo("Joana Lima");
        AuthorshipRecord record = scenario.recorder.inTransaction().getFirst();
        assertThat(record.operation()).isEqualTo("STUDENT_UPDATE");
        assertThat(record.targetId()).isEqualTo(student.id());
        assertThat(record.authorId()).isEqualTo(admin.id());
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
    }

    @Test
    void replacingTheCpfStoresTheNewProtectedValue() {
        Student student = registered();

        StudentView view =
                scenario.update.run(
                        admin,
                        updateCommand(
                                student.id(),
                                new StudentInput(null, null, new Secret(CPF_B), null, null)));

        Cpf cpf = Cpf.of(CPF_B);
        assertThat(student.cpfHash()).isEqualTo(scenario.protection.hashCpf(cpf));
        assertThat(student.encryptedCpf()).isEqualTo(scenario.protection.encryptCpf(cpf));
        assertThat(view.toString()).doesNotContain("12345678909");
        assertThat(scenario.recorder.inTransaction()).hasSize(1);
        assertThat(scenario.attemptsRepository.all()).isEmpty();
    }

    @Test
    void sameCpfAgainIsNotADuplicate() {
        Student student = registered();
        String hash = student.cpfHash();

        scenario.update.run(
                admin,
                updateCommand(
                        student.id(),
                        new StudentInput("Matteo M.", null, new Secret(CPF_A), null, null)));

        assertThat(student.cpfHash()).isEqualTo(hash);
        assertThat(student.name()).isEqualTo("Matteo M.");
    }

    @Test
    void cpfOfAnotherStudentIsADuplicateThatNamesTheOtherNeverTheCpf() {
        Student student = registered();
        scenario.student("Ana Souza", "1990-03-04", CPF_B, null);

        DomainException error =
                refusal(
                        admin,
                        updateCommand(
                                student.id(),
                                new StudentInput(null, null, new Secret(CPF_B), null, null)));

        assertThat(error.code()).isEqualTo(ErrorCode.DUPLICATE_CPF);
        assertThat(error.details()).isEqualTo(Map.of("name", "Ana Souza"));
        assertThat(error.getMessage() + error.details()).doesNotContain("12345678909");
        assertThat(student.cpfHash()).isEqualTo(scenario.protection.hashCpf(Cpf.of(CPF_A)));
    }

    @Test
    void invalidCpfIsAFieldViolation() {
        Student student = registered();

        DomainException error =
                refusal(
                        admin,
                        updateCommand(
                                student.id(),
                                new StudentInput(null, null, new Secret("123"), null, null)));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.details().get("fields"))
                .isEqualTo(List.of(Map.of("field", "cpf", "code", "INVALID_CPF")));
    }

    @Test
    void editThatMakesTheStudentAMinorRequiresTheGuardian() {
        Student adult = scenario.student("Ana Souza", "1990-03-04", CPF_B, null);

        DomainException error =
                refusal(
                        admin,
                        updateCommand(
                                adult.id(),
                                new StudentInput(null, "2014-05-10", null, null, null)));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.details().get("fields"))
                .isEqualTo(List.of(Map.of("field", "guardian", "code", "GUARDIAN_REQUIRED")));
        assertThat(adult.birthDate()).isEqualTo(LocalDate.of(1990, 3, 4));
    }

    @Test
    void editThatMakesTheStudentAMinorAcceptsTheGuardianSentTogether() {
        Student adult = scenario.student("Ana Souza", "1990-03-04", CPF_B, null);

        scenario.update.run(
                admin,
                updateCommand(
                        adult.id(),
                        new StudentInput(
                                null,
                                "2014-05-10",
                                null,
                                new GuardianInput(
                                        "Maria Silva", "11987654321", "maria@exemplo.com"),
                                null)));

        assertThat(adult.guardianName()).isEqualTo("Maria Silva");
    }

    @Test
    void legacyMinorWithoutContactMustGetTheThreeGuardianFieldsOnAnyEdit() {
        Student legacy = scenario.student("Lia Torres", "2014-05-10", CPF_B, null);

        DomainException error =
                refusal(
                        admin,
                        updateCommand(
                                legacy.id(), new StudentInput("Lia T.", null, null, null, null)));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void guardianIsKeptWhenTheStudentBecomesAnAdult() {
        Student student = registered();
        scenario.clock.define(java.time.Instant.parse("2032-06-01T12:00:00Z"));

        scenario.update.run(
                admin,
                updateCommand(student.id(), new StudentInput("Matteo M.", null, null, null, null)));

        assertThat(student.ageAt(LocalDate.of(2032, 6, 1))).isEqualTo(18);
        assertThat(student.guardianName()).isEqualTo("Maria Silva");
        assertThat(student.guardianPhone()).isEqualTo("11987654321");
    }

    @Test
    void adultCanClearTheGuardianByAllBlankFields() {
        Student student = scenario.student("Ana Souza", "1990-03-04", CPF_B, null);
        scenario.update.run(
                admin,
                updateCommand(
                        student.id(),
                        new StudentInput(
                                null,
                                null,
                                null,
                                new GuardianInput(
                                        "Maria Silva", "11987654321", "maria@exemplo.com"),
                                null)));

        StudentView view =
                scenario.update.run(
                        admin,
                        updateCommand(
                                student.id(),
                                new StudentInput(
                                        null, null, null, new GuardianInput("", "", ""), null)));

        assertThat(view.guardian()).isNull();
        assertThat(student.guardianPhone()).isNull();
    }

    @Test
    void anyStudentIsEditableByTheAdministratorEvenWithoutTeachers() {
        Student student = scenario.student("Ana Souza", "1990-03-04", CPF_B, null);

        StudentView view =
                scenario.update.run(
                        admin,
                        updateCommand(
                                student.id(), new StudentInput("Ana S.", null, null, null, null)));

        assertThat(view.name()).isEqualTo("Ana S.");
        assertThat(view.teachers()).isEmpty();
    }

    @Test
    void unknownStudentIsNotFoundWithoutAuthorshipForTheAdministrator() {
        DomainException error =
                refusal(
                        admin,
                        updateCommand(
                                UUID.randomUUID(),
                                new StudentInput("Ana S.", null, null, null, null)));

        assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(scenario.recorder.isEmpty()).isTrue();
    }

    @Test
    void administratorWithProvisionalPasswordCannotEdit() {
        Student student = registered();

        DomainException error =
                refusal(
                        ActorFixtures.administratorWithProvisionalPassword(),
                        updateCommand(
                                student.id(), new StudentInput("X Y", null, null, null, null)));

        assertThat(error.code()).isEqualTo(ErrorCode.PASSWORD_CHANGE_REQUIRED);
    }

    @Test
    void commandTextShowsOnlyTheStudentId() {
        UUID id = UUID.randomUUID();
        UpdateStudentUseCase.Command command =
                updateCommand(id, new StudentInput("Matteo", null, new Secret(CPF_A), null, null));

        assertThat(command.toString()).contains(id.toString()).doesNotContain("Matteo");
        assertThat(command.toString()).doesNotContain("529");
    }
}
