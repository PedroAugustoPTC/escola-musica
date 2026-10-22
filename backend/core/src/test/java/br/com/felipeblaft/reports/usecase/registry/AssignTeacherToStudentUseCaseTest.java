package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.registry.AssignTeacherToStudentUseCase.Command;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Associar professor ativo a aluno (FR-028): só ativo, idempotente, com autoria. */
class AssignTeacherToStudentUseCaseTest {

    private final SituationScenario c = new SituationScenario();
    private final Teacher clara = c.teacher("Clara Souza");

    private Command command(Student student, Teacher teacher) {
        return new Command(student.id(), teacher.id(), SituationScenario.correlation());
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void associatesAnActiveTeacherAndLeavesAuthorship() {
        Student student = c.student("Matteo Mafra");
        UUID adminId = UUID.randomUUID();

        c.assign.run(ActorFixtures.administratorWeb(adminId), command(student, clara));

        assertThat(c.assignments.exists(clara.id(), student.id())).isTrue();
        assertThat(c.recorder.inTransaction()).hasSize(1);
        AuthorshipRecord record = c.recorder.last().orElseThrow();
        assertThat(record.operation()).isEqualTo(Operation.ASSIGNMENT_ASSIGN.name());
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.ASSIGNMENT);
        assertThat(record.targetId()).isNotNull();
        assertThat(record.authorId()).isEqualTo(adminId);
        assertThat(record.authorRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
    }

    @Test
    void studentMayHaveSeveralTeachers() {
        Teacher bruno = c.teacher("Bruno Lima");
        Student student = c.student("Matteo Mafra", clara);

        c.assign.run(c.administrator(), command(student, bruno));

        assertThat(c.assignments.listTeachersOfStudent(student.id()))
                .containsExactlyInAnyOrder(clara.id(), bruno.id());
        assertThat(c.assignments.countActiveTeachers(student.id())).isEqualTo(2);
    }

    @Test
    void isIdempotentAndWritesNothingTheSecondTime() {
        Student student = c.student("Matteo Mafra");

        c.assign.run(c.administrator(), command(student, clara));
        c.assign.run(c.administrator(), command(student, clara));

        assertThat(c.assignments.all()).hasSize(1);
        assertThat(c.recorder.records()).hasSize(1);
    }

    @Test
    void inactiveTeacherIsRefused() {
        Teacher gone = c.inactiveTeacher("Dora Antiga");
        Student student = c.student("Matteo Mafra");

        assertThatThrownBy(() -> c.assign.run(c.administrator(), command(student, gone)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TEACHER_INACTIVE));
        assertThat(c.assignments.all()).isEmpty();
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void unknownTeacherOrStudentIsNotFound() {
        Student student = c.student("Matteo Mafra");

        assertThatThrownBy(
                        () ->
                                c.assign.run(
                                        c.administrator(),
                                        new Command(
                                                student.id(),
                                                UUID.randomUUID(),
                                                SituationScenario.correlation())))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(
                        () ->
                                c.assign.run(
                                        c.administrator(),
                                        new Command(
                                                UUID.randomUUID(),
                                                clara.id(),
                                                SituationScenario.correlation())))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(c.assignments.all()).isEmpty();
    }

    @Test
    void teachersCannotAssignAndAProvisionalPasswordCannot() {
        Student student = c.student("Matteo Mafra");

        for (Actor actor :
                new Actor[] {
                    Actor.teacher(clara.id(), ActionOrigin.WEB),
                    Actor.teacher(clara.id(), ActionOrigin.TELEGRAM),
                    ActorFixtures.bootstrap()
                }) {
            assertThatThrownBy(() -> c.assign.run(actor, command(student, clara)))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
        assertThatThrownBy(
                        () ->
                                c.assign.run(
                                        ActorFixtures.administratorWithProvisionalPassword(),
                                        command(student, clara)))
                .satisfies(
                        e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PASSWORD_CHANGE_REQUIRED));
        assertThat(c.assignments.all()).isEmpty();
    }

    @Test
    void initialLoadAssociatesByItsOwnAuthority() {
        Student student = c.student("Matteo Mafra");

        c.assign.run(
                ActorFixtures.initialLoad(),
                new Command(student.id(), clara.id(), "load-a1"));

        assertThat(c.assignments.exists(clara.id(), student.id())).isTrue();
        AuthorshipRecord record = c.recorder.last().orElseThrow();
        assertThat(record.authorId()).isNull();
        assertThat(record.origin()).isEqualTo(ActionOrigin.INITIAL_LOAD);
    }

    @Test
    void initialLoadStillRefusesInactiveTeacher() {
        Teacher gone = c.inactiveTeacher("Dora Antiga");
        Student student = c.student("Matteo Mafra");

        assertThatThrownBy(
                        () ->
                                c.assign.run(
                                        ActorFixtures.initialLoad(),
                                        new Command(student.id(), gone.id(), "load-a2")))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TEACHER_INACTIVE));
    }
}
