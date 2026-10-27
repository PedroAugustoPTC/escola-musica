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
import br.com.felipeblaft.reports.usecase.registry.InactivateStudentUseCase.Command;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Inativar aluno pelo professor (FR-025, FR-026): só os próprios alunos e, havendo mais de um
 * professor ativo associado, a ação é negada com orientação de procurar o administrador.
 */
class InactivateStudentAsTeacherUseCaseTest {

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
    void soleActiveTeacherInactivatesOnWebAndOnTelegram() {
        Student onWeb = c.student("Matteo Mafra", clara);
        Student onTelegram = c.student("Olavo Torrente", clara);

        StudentView viewWeb = c.inactivate.run(web(clara), command(onWeb));
        StudentView viewTelegram = c.inactivate.run(telegram(clara), command(onTelegram));

        assertThat(viewWeb.active()).isFalse();
        assertThat(viewTelegram.active()).isFalse();
        assertThat(viewWeb.canInactivate()).isTrue();
        // o professor nunca vê outros professores
        assertThat(viewWeb.teachers()).isEmpty();
        assertThat(c.recorder.ofOperation(Operation.STUDENT_INACTIVATE)).hasSize(2);
    }

    @Test
    void recordsAuthorshipWithTeacherRoleAndChannel() {
        Student student = c.student("Matteo Mafra", clara);

        c.inactivate.run(telegram(clara), command(student));

        AuthorshipRecord record = c.recorder.last().orElseThrow();
        assertThat(record.authorId()).isEqualTo(clara.id());
        assertThat(record.authorRole()).isEqualTo(Role.TEACHER);
        assertThat(record.origin()).isEqualTo(ActionOrigin.TELEGRAM);
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.STUDENT);
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
    }

    @Test
    void withMoreThanOneActiveTeacherTheActionIsDeniedAndNothingChanges() {
        Student student = c.student("Matteo Mafra", clara);
        c.assignments.assign(bruno.id(), student.id());

        assertThatThrownBy(() -> c.inactivate.run(web(clara), command(student)))
                .isInstanceOf(DomainException.class)
                .satisfies(
                        e ->
                                assertThat(codeOf(e))
                                        .isEqualTo(ErrorCode.SHARED_STUDENT_ACTION_DENIED));

        assertThat(c.students.searchById(student.id())).get().matches(Student::active);
        assertThat(c.recorder.inTransaction()).isEmpty();
        AuthorshipRecord denied = c.recorder.independent().getFirst();
        assertThat(denied.result()).isEqualTo(AuthorshipResult.DENIED);
        assertThat(denied.operation()).isEqualTo(Operation.STUDENT_INACTIVATE.name());
        assertThat(denied.errorCode()).isEqualTo("SHARED_STUDENT_ACTION_DENIED");
        assertThat(denied.targetId()).isEqualTo(student.id());
    }

    @Test
    void inactiveTeachersDoNotCount() {
        Teacher gone = c.inactiveTeacher("Dora Antiga");
        Student student = c.student("Matteo Mafra", clara);
        c.assignments.assign(gone.id(), student.id());

        StudentView view = c.inactivate.run(web(clara), command(student));

        assertThat(view.active()).isFalse();
    }

    @Test
    void neverBlockedByOpenReportsAndTheTeacherKeepsTheAssociation() {
        Student student = c.student("Matteo Mafra", clara);
        c.openReport(clara, student);

        c.inactivate.run(web(clara), command(student));

        assertThat(c.open.countOpenReports(clara.id(), student.id())).isEqualTo(1);
        assertThat(c.assignments.exists(clara.id(), student.id())).isTrue();
        // o professor continua vendo o aluno inativo pelo escopo (filtro de situação ALL)
        assertThat(c.access.findVisible(web(clara), student.id()).active()).isFalse();
    }

    @Test
    void foreignWithoutTeacherAndUnknownStudentsAreIndistinguishable() {
        Student foreign = c.student("Helena Prado", bruno);
        Student orphan = c.student("Sem Professor");
        UUID unknown = UUID.randomUUID();

        for (UUID id : new UUID[] {foreign.id(), orphan.id(), unknown}) {
            assertThatThrownBy(
                            () ->
                                    c.inactivate.run(
                                            web(clara),
                                            new Command(id, SituationScenario.correlation())))
                    .isInstanceOf(DomainException.class)
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND))
                    .satisfies(e -> assertThat(((DomainException) e).details()).isEmpty());
        }

        assertThat(c.students.searchById(foreign.id())).get().matches(Student::active);
        assertThat(c.students.searchById(orphan.id())).get().matches(Student::active);
        // as três negativas deixam o mesmo tipo de registro (FR-040, FR-025)
        assertThat(c.recorder.independent())
                .hasSize(3)
                .allSatisfy(
                        record -> {
                            assertThat(record.operation()).isEqualTo("AUTHORIZATION_DENIED");
                            assertThat(record.result()).isEqualTo(AuthorshipResult.DENIED);
                            assertThat(record.errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
                            assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.STUDENT);
                            assertThat(record.authorId()).isEqualTo(clara.id());
                        });
        assertThat(c.recorder.inTransaction()).isEmpty();
    }

    @Test
    void inactiveTeacherCannotAct() {
        Teacher gone = c.inactiveTeacher("Dora Antiga");
        Student student = c.student("Matteo Mafra", gone);

        assertThatThrownBy(() -> c.inactivate.run(web(gone), command(student)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TEACHER_INACTIVE));
        assertThat(c.students.searchById(student.id())).get().matches(Student::active);
    }

    @Test
    void alreadyInactiveStudentIsReturnedWithoutNewRecord() {
        Student student = c.student("Matteo Mafra", clara);
        c.inactivateDirectly(student);

        StudentView view = c.inactivate.run(web(clara), command(student));

        assertThat(view.active()).isFalse();
        assertThat(c.recorder.isEmpty()).isTrue();
    }
}
