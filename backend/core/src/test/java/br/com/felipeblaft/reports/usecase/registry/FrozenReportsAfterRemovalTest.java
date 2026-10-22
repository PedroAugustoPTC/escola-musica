package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.doubles.FakeAuthorshipRecorder;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import br.com.felipeblaft.reports.usecase.home.GetStudentSummaryUseCase;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Relatórios depois de retirar a associação (R-23, FR-029): preservados sem alteração, fora do
 * alcance do professor, e de volta quando o administrador associa de novo.
 */
class FrozenReportsAfterRemovalTest {

    private final SituationScenario c = new SituationScenario();
    private final Teacher clara = c.teacher("Clara Souza");
    private final Student matteo = c.student("Matteo Mafra", clara);
    private final GetTeacherHomeUseCase home = new GetTeacherHomeUseCase(c.students, c.open);
    private final GetStudentSummaryUseCase summary =
            new GetStudentSummaryUseCase(
                    c.students, c.open, c.assignmentAuthorization, c.eligibility);

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    private void remove(Teacher teacher, Student student) {
        c.remove.run(
                c.administrator(),
                new RemoveTeacherFromStudentUseCase.Command(
                        student.id(), teacher.id(), SituationScenario.correlation()));
    }

    private void assign(Teacher teacher, Student student) {
        c.assign.run(
                c.administrator(),
                new AssignTeacherToStudentUseCase.Command(
                        student.id(), teacher.id(), SituationScenario.correlation()));
    }

    @Test
    void openReportsLeaveTheTeacherViewButStayUntouched() {
        OpenReport report = c.openReport(clara, matteo);
        assertThat(home.run(clara).openReports()).containsExactly(report);

        remove(clara, matteo);

        GetTeacherHomeUseCase.Home afterRemoval = home.run(clara);
        assertThat(afterRemoval.openReports()).isEmpty();
        assertThat(afterRemoval.students()).isEmpty();
        assertThat(afterRemoval.withoutStudents()).isTrue();
        assertThatThrownBy(() -> summary.run(clara.id(), matteo.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        // preservado, sem nenhuma alteração
        assertThat(c.open.listByTeacher(clara.id())).containsExactly(report);
    }

    @Test
    void reassociationReturnsTheReportFromWhereItStopped() {
        OpenReport report = c.openReport(clara, matteo);
        remove(clara, matteo);

        assign(clara, matteo);

        GetTeacherHomeUseCase.Home back = home.run(clara);
        assertThat(back.openReports()).containsExactly(report);
        assertThat(back.students()).extracting(s -> s.name()).containsExactly("Matteo Mafra");
        assertThat(summary.run(clara.id(), matteo.id()).hasOpenReport()).isTrue();
    }

    @Test
    void otherTeachersReportsAreNotAffectedByTheRemoval() {
        Teacher bruno = c.teacher("Bruno Lima");
        c.assignments.assign(bruno.id(), matteo.id());
        OpenReport ofBruno = c.openReport(bruno, matteo);
        c.openReport(clara, matteo);

        remove(clara, matteo);

        assertThat(home.run(bruno).openReports()).containsExactly(ofBruno);
    }

    @Test
    void approvedReportsArePreservedAndCameBackWithTheAssociation() {
        ReportScenario scenario = new ReportScenario();
        LessonReport approved =
                scenario.approvedLesson(scenario.matteo, LocalDate.of(2026, 3, 10), "Escala de Dó");
        FakeAuthorshipRecorder recorder = new FakeAuthorshipRecorder();
        AuthorshipService authorship = new AuthorshipService(recorder, scenario.clock);
        ActorAuthorizationService authorization =
                new ActorAuthorizationService(PermissionMatrix.standard());
        RemoveTeacherFromStudentUseCase remove =
                new RemoveTeacherFromStudentUseCase(
                        authorization, scenario.assignments, authorship);
        AssignTeacherToStudentUseCase assign =
                new AssignTeacherToStudentUseCase(
                        authorization,
                        new StudentAccessService(scenario.students),
                        scenario.teachers,
                        scenario.assignments,
                        authorship,
                        scenario.clock);
        assertThat(scenario.access.lessonReport(scenario.clara.id(), approved.id())).isNotNull();

        remove.run(
                c.administrator(),
                new RemoveTeacherFromStudentUseCase.Command(
                        scenario.matteo.id(),
                        scenario.clara.id(),
                        SituationScenario.correlation()));

        assertThatThrownBy(() -> scenario.access.lessonReport(scenario.clara.id(), approved.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(scenario.reports.searchById(approved.id())).contains(approved);

        assign.run(
                c.administrator(),
                new AssignTeacherToStudentUseCase.Command(
                        scenario.matteo.id(),
                        scenario.clara.id(),
                        SituationScenario.correlation()));

        assertThat(scenario.access.lessonReport(scenario.clara.id(), approved.id()))
                .isSameAs(approved);
    }
}
