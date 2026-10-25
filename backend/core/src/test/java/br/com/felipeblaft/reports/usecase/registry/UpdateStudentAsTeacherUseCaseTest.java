package br.com.felipeblaft.reports.usecase.registry;

import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CORRELATION;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_A;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_B;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_C;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_D;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.registerCommand;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.updateCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Edição de aluno pelo professor, Web e Telegram (US4, FR-024, FR-025). */
class UpdateStudentAsTeacherUseCaseTest {

    private final RegistryScenario scenario = new RegistryScenario();
    private final Teacher clara = scenario.teacher("Clara Dias");
    private final Teacher bruno = scenario.teacher("Bruno Lima");
    private final Actor claraWeb = ActorFixtures.teacherWeb(clara.id());
    private final Actor claraTelegram = ActorFixtures.teacherTelegram(clara.id());
    private final Student mine =
            scenario.student("Minha Aluna", "1990-03-04", CPF_A, "Piano", clara);

    private DomainException refusal(Actor actor, UpdateStudentUseCase.Command command) {
        return catchThrowableOfType(
                DomainException.class, () -> scenario.update.run(actor, command));
    }

    private static StudentInput cpfOnly(String cpf) {
        return new StudentInput(null, null, new Secret(cpf), null, null);
    }

    @Test
    void teacherEditsTheirOwnStudentIncludingASharedOne() {
        scenario.assignments.assign(bruno.id(), mine.id());

        StudentView view =
                scenario.update.run(
                        claraWeb,
                        updateCommand(
                                mine.id(),
                                new StudentInput("Minha Aluna Souza", null, null, null, "")));

        assertThat(view.name()).isEqualTo("Minha Aluna Souza");
        assertThat(view.instrument()).isNull();
        assertThat(view.teachers()).isEmpty();
        assertThat(view.canInactivate()).as("compartilhado: só o administrador inativa").isFalse();
        AuthorshipRecord record = scenario.recorder.inTransaction().getFirst();
        assertThat(record.operation()).isEqualTo("STUDENT_UPDATE");
        assertThat(record.authorId()).isEqualTo(clara.id());
        assertThat(record.origin()).isEqualTo(ActionOrigin.WEB);
    }

    @Test
    void sameRulesOnTelegram() {
        StudentView view =
                scenario.update.run(
                        claraTelegram,
                        new UpdateStudentUseCase.Command(
                                mine.id(),
                                new StudentInput("Nova Aluna", "05/06/1991", null, null, null),
                                "4711"));

        assertThat(view.name()).isEqualTo("Nova Aluna");
        assertThat(scenario.recorder.records().getFirst().origin())
                .isEqualTo(ActionOrigin.TELEGRAM);
    }

    @Test
    void anotherTeachersStudentIsIndistinguishableFromAMissingOne() {
        Student alien = scenario.student("De Bruno", "1990-03-04", CPF_B, null, bruno);
        Student orphan = scenario.student("Sem Dono", "1990-03-04", CPF_C, null);

        DomainException alienError =
                refusal(
                        claraWeb,
                        updateCommand(alien.id(), new StudentInput("X Y", null, null, null, null)));
        DomainException orphanError =
                refusal(
                        claraWeb,
                        updateCommand(
                                orphan.id(), new StudentInput("X Y", null, null, null, null)));
        DomainException missingError =
                refusal(
                        claraWeb,
                        updateCommand(
                                UUID.randomUUID(),
                                new StudentInput("X Y", null, null, null, null)));

        for (DomainException error :
                new DomainException[] {alienError, orphanError, missingError}) {
            assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            assertThat(error.details()).isEmpty();
        }
        assertThat(scenario.recorder.ofOperation(AuthorshipRecord.AUTHORIZATION_DENIED))
                .hasSize(3)
                .allSatisfy(
                        record -> {
                            assertThat(record.result()).isEqualTo(AuthorshipResult.DENIED);
                            assertThat(record.errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
                        });
        assertThat(alien.name()).isEqualTo("De Bruno");
    }

    @Test
    void newCpfBelongingToAnotherTeachersStudentIsRefusedNeutrallyAndCounted() {
        Student alien = scenario.student("De Bruno", "1990-03-04", CPF_B, null, bruno);

        DomainException error = refusal(claraWeb, updateCommand(mine.id(), cpfOnly(CPF_B)));

        assertThat(error.code()).isEqualTo(ErrorCode.CPF_UNAVAILABLE);
        assertThat(error.details()).isEmpty();
        assertThat(mine.cpfHash()).isEqualTo(scenario.protection.hashCpf(Cpf.of(CPF_A)));
        AuthorshipRecord record =
                scenario.recorder
                        .ofOperation(AuthorshipRecord.CPF_REFUSED_OTHER_STUDENT)
                        .getFirst();
        assertThat(record.targetId()).isEqualTo(alien.id());
        assertThat(record.authorId()).isEqualTo(clara.id());
        assertThat(scenario.recorder.inTransaction()).isEmpty();
    }

    @Test
    void newCpfBelongingToAnotherOfTheirOwnStudentsSaysSo() {
        scenario.student("Outra Minha", "1990-03-04", CPF_B, null, clara);

        DomainException error = refusal(claraTelegram, updateCommand(mine.id(), cpfOnly(CPF_B)));

        assertThat(error.code()).isEqualTo(ErrorCode.STUDENT_ALREADY_YOURS);
        assertThat(scenario.recorder.isEmpty()).isTrue();
    }

    @Test
    void fiveRefusalsBlockCpfOperationsOfEditAndRegisterTogether() {
        scenario.student("De Bruno", "1990-03-04", CPF_B, null, bruno);
        for (int i = 0; i < 3; i++) {
            assertThat(refusal(claraWeb, updateCommand(mine.id(), cpfOnly(CPF_B))).code())
                    .isEqualTo(ErrorCode.CPF_UNAVAILABLE);
        }
        for (int i = 0; i < 2; i++) {
            assertThat(
                            catchThrowableOfType(
                                            DomainException.class,
                                            () ->
                                                    scenario.register.run(
                                                            claraWeb,
                                                            registerCommand(
                                                                    RegistryScenario.adult(
                                                                            "Pessoa", CPF_B))))
                                    .code())
                    .isEqualTo(ErrorCode.CPF_UNAVAILABLE);
        }

        DomainException blocked = refusal(claraWeb, updateCommand(mine.id(), cpfOnly(CPF_D)));

        assertThat(blocked.code()).isEqualTo(ErrorCode.OPERATION_TEMPORARILY_BLOCKED);
        assertThat(blocked.details()).containsKey("remainingSeconds");
        assertThat(mine.cpfHash()).isEqualTo(scenario.protection.hashCpf(Cpf.of(CPF_A)));
    }

    @Test
    void editWithoutCpfStillWorksWhileBlocked() {
        scenario.student("De Bruno", "1990-03-04", CPF_B, null, bruno);
        for (int i = 0; i < 5; i++) {
            refusal(claraWeb, updateCommand(mine.id(), cpfOnly(CPF_B)));
        }

        StudentView view =
                scenario.update.run(
                        claraWeb,
                        updateCommand(
                                mine.id(),
                                new StudentInput("Nome Novo", null, new Secret(""), null, null)));

        assertThat(view.name()).isEqualTo("Nome Novo");
        scenario.clock.advance(Duration.ofMinutes(15));
        assertThat(scenario.update.run(claraWeb, updateCommand(mine.id(), cpfOnly(CPF_D))).id())
                .isEqualTo(mine.id());
        assertThat(mine.cpfHash()).isEqualTo(scenario.protection.hashCpf(Cpf.of(CPF_D)));
    }

    @Test
    void thirtyLookupsBlockEditsWithCpfEvenWhenTheCpfIsFree() {
        for (int i = 0; i < 30; i++) {
            scenario.update.run(claraWeb, updateCommand(mine.id(), cpfOnly(CPF_A)));
        }

        DomainException blocked = refusal(claraWeb, updateCommand(mine.id(), cpfOnly(CPF_D)));

        assertThat(blocked.code()).isEqualTo(ErrorCode.OPERATION_TEMPORARILY_BLOCKED);
        assertThat(blocked.details()).containsEntry("remainingSeconds", 3600L);
    }

    @Test
    void theRecheckDoesNotCountALookup() {
        for (int i = 0; i < 40; i++) {
            scenario.update.run(
                    claraTelegram,
                    new UpdateStudentUseCase.Command(mine.id(), cpfOnly(CPF_A), "4711", true));
        }

        assertThat(scenario.attemptsRepository.all()).isEmpty();
    }

    @Test
    void invalidCpfDoesNotCount() {
        for (int i = 0; i < 40; i++) {
            DomainException error = refusal(claraWeb, updateCommand(mine.id(), cpfOnly("123")));
            assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        }

        assertThat(scenario.attemptsRepository.all()).isEmpty();
    }

    @Test
    void anotherTeachersDeniedCpfEditDoesNotLeakThroughTheScope() {
        Student alien = scenario.student("De Bruno", "1990-03-04", CPF_B, null, bruno);

        DomainException error = refusal(claraWeb, updateCommand(alien.id(), cpfOnly(CPF_D)));

        assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(scenario.attemptsRepository.all())
                .as("nenhuma consulta de CPF foi feita")
                .isEmpty();
    }

    @Test
    void commandTextShowsOnlyTheStudentId() {
        UpdateStudentUseCase.Command command =
                new UpdateStudentUseCase.Command(mine.id(), cpfOnly(CPF_A), CORRELATION);

        assertThat(command.toString())
                .isEqualTo("UpdateStudentUseCase.Command[studentId=" + mine.id() + "]");
        assertThat(command.lookupAlreadyCounted()).isFalse();
    }
}
