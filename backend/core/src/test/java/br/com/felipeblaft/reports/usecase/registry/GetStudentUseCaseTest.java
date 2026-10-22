package br.com.felipeblaft.reports.usecase.registry;

import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_A;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Abrir um aluno (US3). */
class GetStudentUseCaseTest {

    private final RegistryScenario scenario = new RegistryScenario();
    private final Actor admin = ActorFixtures.administratorWeb();

    private DomainException refusal(Actor actor, UUID id) {
        return catchThrowableOfType(
                DomainException.class,
                () -> scenario.get.run(actor, new GetStudentUseCase.Command(id, "req-7")));
    }

    @Test
    void administratorOpensAnyStudentWithTeachersAndAge() {
        Teacher clara = scenario.teacher("Clara Dias");
        Teacher gone = scenario.teacher("Bruno Lima", false);
        Student student = scenario.student("Ana Souza", "1990-03-04", CPF_A, "Piano", clara, gone);

        StudentView view =
                scenario.get.run(admin, new GetStudentUseCase.Command(student.id(), "req-7"));

        assertThat(view.id()).isEqualTo(student.id());
        assertThat(view.name()).isEqualTo("Ana Souza");
        assertThat(view.birthDate()).isEqualTo(LocalDate.of(1990, 3, 4));
        assertThat(view.age()).isEqualTo(36);
        assertThat(view.instrument()).isEqualTo("Piano");
        assertThat(view.active()).isTrue();
        assertThat(view.canInactivate()).isNull();
        assertThat(view.teachers())
                .extracting(StudentView.TeacherRef::name, StudentView.TeacherRef::active)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Bruno Lima", false),
                        org.assertj.core.groups.Tuple.tuple("Clara Dias", true));
        assertThat(view.toString()).doesNotContain("Ana").doesNotContain("52998224725");
    }

    @Test
    void opensStudentWithoutTeachersAndInactiveOnes() {
        Student student = scenario.student("Ana Souza", "1990-03-04", CPF_A, null);
        student.inactivate(scenario.clock.now());

        StudentView view =
                scenario.get.run(admin, new GetStudentUseCase.Command(student.id(), "req-7"));

        assertThat(view.active()).isFalse();
        assertThat(view.teachers()).isEmpty();
        assertThat(view.guardian()).isNull();
    }

    @Test
    void showsTheGuardianContactToTheAdministrator() {
        StudentView created =
                scenario.register.run(
                        admin,
                        RegistryScenario.registerCommand(
                                RegistryScenario.minor("Matteo Mafra", CPF_B)));

        StudentView view =
                scenario.get.run(admin, new GetStudentUseCase.Command(created.id(), "req-7"));

        assertThat(view.guardian())
                .extracting("name", "phone", "email")
                .containsExactly("Maria Silva", "11987654321", "maria@exemplo.com");
    }

    @Test
    void unknownStudentIsNotFoundAndLeavesNoRecordForTheAdministrator() {
        DomainException error = refusal(admin, UUID.randomUUID());

        assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(scenario.recorder.isEmpty()).isTrue();
    }

    @Test
    void readsLeaveNoAuthorshipWhenAuthorized() {
        Student student = scenario.student("Ana Souza", "1990-03-04", CPF_A, null);

        scenario.get.run(admin, new GetStudentUseCase.Command(student.id(), "req-7"));

        assertThat(scenario.recorder.isEmpty()).isTrue();
    }

    @Test
    void administratorWithProvisionalPasswordCannotOpen() {
        Student student = scenario.student("Ana Souza", "1990-03-04", CPF_A, null);

        DomainException error =
                refusal(ActorFixtures.administratorWithProvisionalPassword(), student.id());

        assertThat(error.code()).isEqualTo(ErrorCode.PASSWORD_CHANGE_REQUIRED);
    }

    @Test
    void teacherDeniedOnAnotherTeachersStudentRecordsTheDenialWithTheCorrelationId() {
        Teacher clara = scenario.teacher("Clara Dias");
        Teacher bruno = scenario.teacher("Bruno Lima");
        Student student = scenario.student("Ana Souza", "1990-03-04", CPF_A, null, bruno);

        DomainException error = refusal(ActorFixtures.teacherWeb(clara.id()), student.id());

        assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        AuthorshipRecord record = scenario.recorder.independent().getFirst();
        assertThat(record.operation()).isEqualTo("AUTHORIZATION_DENIED");
        assertThat(record.result()).isEqualTo(AuthorshipResult.DENIED);
        assertThat(record.errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(record.correlationId()).isEqualTo("req-7");
        assertThat(record.authorId()).isEqualTo(clara.id());
        assertThat(record.targetId()).isEqualTo(student.id());
    }
}
