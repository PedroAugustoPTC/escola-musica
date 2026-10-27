package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.usecase.registry.ReactivateStudentUseCase.Command;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Reativar aluno pelo professor: a mesma regra do único professor ativo (FR-026). */
class ReactivateStudentAsTeacherUseCaseTest {

    private final SituationScenario c = new SituationScenario();
    private final Teacher clara = c.teacher("Clara Souza");
    private final Teacher bruno = c.teacher("Bruno Lima");

    private Actor web(Teacher teacher) {
        return Actor.teacher(teacher.id(), ActionOrigin.WEB);
    }

    private Actor telegram(Teacher teacher) {
        return Actor.teacher(teacher.id(), ActionOrigin.TELEGRAM);
    }

    private Command command(Student student) {
        return new Command(student.id(), SituationScenario.correlation());
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void soleActiveTeacherReactivatesOnWebAndOnTelegram() {
        Student onWeb = c.student("Matteo Mafra", clara);
        Student onTelegram = c.student("Olavo Torrente", clara);
        c.inactivateDirectly(onWeb);
        c.inactivateDirectly(onTelegram);

        StudentView viewWeb = c.reactivate.run(web(clara), command(onWeb));
        StudentView viewTelegram = c.reactivate.run(telegram(clara), command(onTelegram));

        assertThat(viewWeb.active()).isTrue();
        assertThat(viewTelegram.active()).isTrue();
        assertThat(viewWeb.canInactivate()).isTrue();
        assertThat(viewWeb.teachers()).isEmpty();
        assertThat(c.recorder.ofOperation(Operation.STUDENT_REACTIVATE)).hasSize(2);
    }

    @Test
    void withMoreThanOneActiveTeacherTheActionIsDeniedAndNothingChanges() {
        Student student = c.student("Matteo Mafra", clara);
        c.assignments.assign(bruno.id(), student.id());
        c.inactivateDirectly(student);

        assertThatThrownBy(() -> c.reactivate.run(web(clara), command(student)))
                .satisfies(
                        e ->
                                assertThat(codeOf(e))
                                        .isEqualTo(ErrorCode.SHARED_STUDENT_ACTION_DENIED));

        assertThat(c.students.searchById(student.id())).get().matches(s -> !s.active());
        assertThat(c.recorder.inTransaction()).isEmpty();
        assertThat(c.recorder.independent().getFirst().result()).isEqualTo(AuthorshipResult.DENIED);
        assertThat(c.recorder.independent().getFirst().operation())
                .isEqualTo(Operation.STUDENT_REACTIVATE.name());
    }

    @Test
    void inactiveTeachersDoNotCountAndOpenReportsNeverBlock() {
        Teacher gone = c.inactiveTeacher("Dora Antiga");
        Student student = c.student("Matteo Mafra", clara);
        c.assignments.assign(gone.id(), student.id());
        c.openReport(clara, student);
        c.inactivateDirectly(student);

        StudentView view = c.reactivate.run(web(clara), command(student));

        assertThat(view.active()).isTrue();
        assertThat(c.open.countOpenReports(clara.id(), student.id())).isEqualTo(1);
    }

    @Test
    void foreignOrUnknownStudentIsNotFound() {
        Student foreign = c.student("Helena Prado", bruno);
        c.inactivateDirectly(foreign);

        for (UUID id : new UUID[] {foreign.id(), UUID.randomUUID()}) {
            assertThatThrownBy(
                            () ->
                                    c.reactivate.run(
                                            web(clara),
                                            new Command(id, SituationScenario.correlation())))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
        assertThat(c.students.searchById(foreign.id())).get().matches(s -> !s.active());
    }

    @Test
    void inactiveTeacherCannotAct() {
        Teacher gone = c.inactiveTeacher("Dora Antiga");
        Student student = c.student("Matteo Mafra", gone);
        c.inactivateDirectly(student);

        assertThatThrownBy(() -> c.reactivate.run(web(gone), command(student)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TEACHER_INACTIVE));
    }
}
