package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.registry.ReactivateStudentUseCase.Command;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Reativar aluno pelo administrador: devolve o aluno, sem apagar nem alterar mais nada. */
class ReactivateStudentUseCaseTest {

    private final SituationScenario c = new SituationScenario();
    private final Teacher clara = c.teacher("Clara Souza");
    private final Teacher bruno = c.teacher("Bruno Lima");

    private Command command(Student student) {
        return new Command(student.id(), SituationScenario.correlation());
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void administratorReactivatesAnyStudent() {
        Student shared = c.student("Matteo Mafra", clara);
        c.assignments.assign(bruno.id(), shared.id());
        Student orphan = c.student("Sem Professor");
        c.inactivateDirectly(shared);
        c.inactivateDirectly(orphan);

        StudentView sharedView = c.reactivate.run(c.administrator(), command(shared));
        StudentView orphanView = c.reactivate.run(c.administrator(), command(orphan));

        assertThat(sharedView.active()).isTrue();
        assertThat(orphanView.active()).isTrue();
        assertThat(sharedView.canInactivate()).isNull();
        assertThat(sharedView.teachers())
                .extracting(StudentView.TeacherRef::name)
                .containsExactlyInAnyOrder("Clara Souza", "Bruno Lima");
        assertThat(c.students.searchById(shared.id())).get().matches(Student::active);
    }

    @Test
    void reactivationKeepsAssociationAndOpenReports() {
        Student student = c.student("Matteo Mafra", clara);
        c.openReport(clara, student);
        c.inactivateDirectly(student);

        c.reactivate.run(c.administrator(), command(student));

        assertThat(c.open.countOpenReports(clara.id(), student.id())).isEqualTo(1);
        assertThat(c.assignments.exists(clara.id(), student.id())).isTrue();
        assertThat(c.students.listByTeacher(clara.id()))
                .extracting(Student::active)
                .containsOnly(true);
    }

    @Test
    void leavesAuthorshipRecordInTheSameTransaction() {
        Student student = c.student("Matteo Mafra", clara);
        c.inactivateDirectly(student);
        UUID adminId = UUID.randomUUID();

        c.reactivate.run(ActorFixtures.administratorWeb(adminId), command(student));

        AuthorshipRecord record = c.recorder.last().orElseThrow();
        assertThat(c.recorder.inTransaction()).hasSize(1);
        assertThat(record.operation()).isEqualTo(Operation.STUDENT_REACTIVATE.name());
        assertThat(record.authorId()).isEqualTo(adminId);
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.STUDENT);
        assertThat(record.targetId()).isEqualTo(student.id());
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
    }

    @Test
    void alreadyActiveStudentChangesNothingAndLeavesNoRecord() {
        Student student = c.student("Matteo Mafra", clara);

        StudentView view = c.reactivate.run(c.administrator(), command(student));

        assertThat(view.active()).isTrue();
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void unknownStudentAndForbiddenActorsAreRefused() {
        Student student = c.student("Matteo Mafra", clara);
        c.inactivateDirectly(student);
        Command unknown = new Command(UUID.randomUUID(), SituationScenario.correlation());

        assertThatThrownBy(() -> c.reactivate.run(c.administrator(), unknown))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(
                        () ->
                                c.reactivate.run(
                                        ActorFixtures.administratorWithProvisionalPassword(),
                                        command(student)))
                .satisfies(
                        e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PASSWORD_CHANGE_REQUIRED));
        assertThatThrownBy(() -> c.reactivate.run(ActorFixtures.bootstrap(), command(student)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(c.students.searchById(student.id())).get().matches(s -> !s.active());
        assertThat(c.recorder.isEmpty()).isTrue();
    }
}
