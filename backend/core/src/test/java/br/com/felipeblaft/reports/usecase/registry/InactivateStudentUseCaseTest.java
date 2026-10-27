package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
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
import br.com.felipeblaft.reports.usecase.registry.InactivateStudentUseCase.Command;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Inativar aluno pelo administrador: qualquer aluno, sem bloqueio, sem apagar nada (SC-013). */
class InactivateStudentUseCaseTest {

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
    void administratorInactivatesAnyStudentEvenWithManyTeachersOrNone() {
        Student shared = c.student("Matteo Mafra", clara);
        c.assignments.assign(bruno.id(), shared.id());
        Student orphan = c.student("Sem Professor");

        StudentView sharedView = c.inactivate.run(c.administrator(), command(shared));
        StudentView orphanView = c.inactivate.run(c.administrator(), command(orphan));

        assertThat(sharedView.active()).isFalse();
        assertThat(orphanView.active()).isFalse();
        assertThat(c.students.searchById(shared.id())).get().matches(s -> !s.active());
        assertThat(c.students.searchById(orphan.id())).get().matches(s -> !s.active());
    }

    @Test
    void administratorViewListsTeachersWithTheirSituationAndNeverTheCanInactivateFlag() {
        Teacher gone = c.inactiveTeacher("Dora Antiga");
        Student student = c.student("Matteo Mafra", clara);
        c.assignments.assign(gone.id(), student.id());

        StudentView view = c.inactivate.run(c.administrator(), command(student));

        assertThat(view.id()).isEqualTo(student.id());
        assertThat(view.name()).isEqualTo("Matteo Mafra");
        assertThat(view.canInactivate()).isNull();
        assertThat(view.teachers())
                .extracting(StudentView.TeacherRef::name, StudentView.TeacherRef::active)
                .containsExactlyInAnyOrder(tuple("Clara Souza", true), tuple("Dora Antiga", false));
        assertThat(view.guardian()).isNotNull();
        assertThat(view.guardian().name()).isEqualTo("Responsável");
        assertThat(view.age()).isEqualTo(student.ageAt(c.clock.todayInSchoolTimezone()));
    }

    @Test
    void notBlockedByOpenReportsAndNothingIsDeletedOrChanged() {
        Student student = c.student("Matteo Mafra", clara);
        c.openReport(clara, student);
        c.openReport(clara, student);

        StudentView view = c.inactivate.run(c.administrator(), command(student));

        assertThat(view.active()).isFalse();
        // o relatório continua exatamente onde estava e a associação permanece (SC-013)
        assertThat(c.open.listByTeacher(clara.id())).hasSize(2);
        assertThat(c.open.countOpenReports(clara.id(), student.id())).isEqualTo(2);
        assertThat(c.assignments.exists(clara.id(), student.id())).isTrue();
        assertThat(c.students.all()).hasSize(1);
    }

    @Test
    void leavesAuthorshipRecordInTheSameTransaction() {
        Student student = c.student("Matteo Mafra", clara);
        UUID adminId = UUID.randomUUID();
        String correlation = SituationScenario.correlation();

        c.inactivate.run(
                ActorFixtures.administratorWeb(adminId), new Command(student.id(), correlation));

        assertThat(c.recorder.inTransaction()).hasSize(1);
        AuthorshipRecord record = c.recorder.last().orElseThrow();
        assertThat(record.operation()).isEqualTo(Operation.STUDENT_INACTIVATE.name());
        assertThat(record.authorId()).isEqualTo(adminId);
        assertThat(record.authorRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(record.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.STUDENT);
        assertThat(record.targetId()).isEqualTo(student.id());
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.correlationId()).isEqualTo(correlation);
        assertThat(record.toString()).doesNotContain("Matteo");
    }

    @Test
    void alreadyInactiveStudentChangesNothingAndLeavesNoNewRecord() {
        Student student = c.student("Matteo Mafra", clara);
        c.inactivate.run(c.administrator(), command(student));
        c.clock.advance(Duration.ofHours(1));

        StudentView again = c.inactivate.run(c.administrator(), command(student));

        assertThat(again.active()).isFalse();
        assertThat(c.recorder.records()).hasSize(1);
        assertThat(c.students.searchById(student.id()).orElseThrow().updatedAt())
                .isBefore(c.clock.now());
    }

    @Test
    void unknownStudentIsNotFound() {
        Command unknown = new Command(UUID.randomUUID(), SituationScenario.correlation());

        assertThatThrownBy(() -> c.inactivate.run(c.administrator(), unknown))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void administratorWithProvisionalPasswordIsRefusedAndSystemHasNoRoute() {
        Student student = c.student("Matteo Mafra", clara);

        assertThatThrownBy(
                        () ->
                                c.inactivate.run(
                                        ActorFixtures.administratorWithProvisionalPassword(),
                                        command(student)))
                .satisfies(
                        e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PASSWORD_CHANGE_REQUIRED));
        assertThatThrownBy(() -> c.inactivate.run(ActorFixtures.initialLoad(), command(student)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(c.students.searchById(student.id())).get().matches(Student::active);
        assertThat(c.recorder.isEmpty()).isTrue();
    }
}
