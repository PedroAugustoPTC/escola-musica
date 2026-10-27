package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.registry.RemoveTeacherFromStudentUseCase.Command;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Retirar associação (FR-029): nunca bloqueia, nada é apagado nem alterado, acesso cai na hora. */
class RemoveTeacherFromStudentUseCaseTest {

    private final SituationScenario c = new SituationScenario();
    private final Teacher clara = c.teacher("Clara Souza");
    private final Teacher bruno = c.teacher("Bruno Lima");

    private Command command(Student student, Teacher teacher) {
        return new Command(student.id(), teacher.id(), SituationScenario.correlation());
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void removesTheAssociationAndAccessEndsAtOnce() {
        Student student = c.student("Matteo Mafra", clara);
        assertThat(c.access.findVisible(Actor.teacher(clara.id(), ActionOrigin.WEB), student.id()))
                .isNotNull();

        c.remove.run(c.administrator(), command(student, clara));

        assertThat(c.assignments.exists(clara.id(), student.id())).isFalse();
        assertThatThrownBy(
                        () ->
                                c.access.findVisible(
                                        Actor.teacher(clara.id(), ActionOrigin.WEB), student.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(
                        () -> c.assignmentAuthorization.requireAssignment(clara.id(), student.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void notBlockedByOpenReportsAndNoReportIsChanged() {
        Student student = c.student("Matteo Mafra", clara);
        c.openReport(clara, student);
        c.openReport(clara, student);

        c.remove.run(c.administrator(), command(student, clara));

        // nada de relatório é apagado nem alterado: a consulta de leitura ainda os guarda
        assertThat(c.open.countOpenReports(clara.id(), student.id())).isEqualTo(2);
        assertThat(c.students.searchById(student.id())).get().matches(Student::active);
    }

    @Test
    void studentMayEndWithZeroTeachersAndOnlyTheAdministratorSeesIt() {
        Student student = c.student("Matteo Mafra", clara);

        c.remove.run(c.administrator(), command(student, clara));

        assertThat(c.assignments.listTeachersOfStudent(student.id())).isEmpty();
        assertThat(c.access.findVisible(c.administrator(), student.id()).id())
                .isEqualTo(student.id());
        assertThatThrownBy(
                        () ->
                                c.access.findVisible(
                                        Actor.teacher(bruno.id(), ActionOrigin.WEB), student.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void otherTeachersKeepTheirAccess() {
        Student student = c.student("Matteo Mafra", clara);
        c.assignments.assign(bruno.id(), student.id());

        c.remove.run(c.administrator(), command(student, clara));

        assertThat(c.assignments.listTeachersOfStudent(student.id())).containsExactly(bruno.id());
    }

    @Test
    void removalOfAnInactiveTeacherAndOfAnInactiveStudentWorksToo() {
        Teacher gone = c.inactiveTeacher("Dora Antiga");
        Student student = c.student("Matteo Mafra", gone);
        c.inactivateDirectly(student);

        c.remove.run(c.administrator(), command(student, gone));

        assertThat(c.assignments.exists(gone.id(), student.id())).isFalse();
    }

    @Test
    void leavesAuthorshipRecordInTheSameTransaction() {
        Student student = c.student("Matteo Mafra", clara);
        UUID adminId = UUID.randomUUID();

        c.remove.run(ActorFixtures.administratorWeb(adminId), command(student, clara));

        assertThat(c.recorder.inTransaction()).hasSize(1);
        AuthorshipRecord record = c.recorder.last().orElseThrow();
        assertThat(record.operation()).isEqualTo(Operation.ASSIGNMENT_REMOVE.name());
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.ASSIGNMENT);
        assertThat(record.authorId()).isEqualTo(adminId);
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
    }

    @Test
    void assignAndRemoveOfTheSamePairShareTheTarget() {
        Student student = c.student("Matteo Mafra");

        c.assign.run(
                c.administrator(),
                new AssignTeacherToStudentUseCase.Command(
                        student.id(), clara.id(), SituationScenario.correlation()));
        c.remove.run(c.administrator(), command(student, clara));

        assertThat(c.recorder.records()).hasSize(2);
        assertThat(c.recorder.records().get(0).targetId())
                .isEqualTo(c.recorder.records().get(1).targetId());
    }

    @Test
    void absentAssociationIsNotFoundAndLeavesNoRecord() {
        Student student = c.student("Matteo Mafra", clara);

        assertThatThrownBy(() -> c.remove.run(c.administrator(), command(student, bruno)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(c.assignments.exists(clara.id(), student.id())).isTrue();
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void onlyTheAdministratorMayRemove() {
        Student student = c.student("Matteo Mafra", clara);

        for (Actor actor :
                new Actor[] {
                    Actor.teacher(clara.id(), ActionOrigin.WEB),
                    Actor.teacher(clara.id(), ActionOrigin.TELEGRAM),
                    ActorFixtures.initialLoad()
                }) {
            assertThatThrownBy(() -> c.remove.run(actor, command(student, clara)))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
        assertThatThrownBy(
                        () ->
                                c.remove.run(
                                        ActorFixtures.administratorWithProvisionalPassword(),
                                        command(student, clara)))
                .satisfies(
                        e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PASSWORD_CHANGE_REQUIRED));
        assertThat(c.assignments.exists(clara.id(), student.id())).isTrue();
    }
}
