package br.com.felipeblaft.reports.usecase.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.service.ReportEligibilityService;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import br.com.felipeblaft.reports.usecase.semester.GenerateSemesterReportUseCase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Aluno inativo (FR-022): não é oferecido para novo relatório e sai das listas padrão; o professor
 * associado ainda vê e conclui ou cancela o relatório já em aberto, pelas regras normais.
 */
class RegisterLessonRejectsInactiveStudentTest {

    private final ReportScenario c = new ReportScenario();
    private final ReportEligibilityService eligibility =
            new ReportEligibilityService(c.teachers, c.students);
    private final RegisterLessonByAudioUseCase register =
            new RegisterLessonByAudioUseCase(
                    c.authorization, c.open, c.audioSending, c.queue, c.clock, eligibility);
    private final GenerateSemesterReportUseCase generate =
            new GenerateSemesterReportUseCase(c.preview, c.queue, c.clock, eligibility);

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    private void inactivateMatteo() {
        c.matteo.inactivate(c.clock.now());
        c.students.save(c.matteo);
    }

    @Test
    void inactiveStudentIsNotOfferedForANewReport() {
        inactivateMatteo();
        var file = ReportScenario.file("a.ogg", 10);

        assertThatThrownBy(() -> register.run(c.clara.id(), c.matteo.id(), file, Channel.WEB))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.STUDENT_INACTIVE));

        assertThat(c.queue.jobs).isEmpty();
        assertThat(c.normalization.deleted).contains(file.path());
    }

    @Test
    void inactiveStudentCannotHaveANewSemesterGenerated() {
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala de Dó");
        inactivateMatteo();

        assertThatThrownBy(
                        () ->
                                generate.run(
                                        c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.STUDENT_INACTIVE));
        assertThat(c.queue.jobs).isEmpty();
    }

    @Test
    void anotherTeachersStudentStillLooksNonexistentEvenWhenInactive() {
        c.helena.inactivate(c.clock.now());
        c.students.save(c.helena);

        assertThatThrownBy(
                        () ->
                                register.run(
                                        c.clara.id(),
                                        c.helena.id(),
                                        ReportScenario.file("b.ogg", 10),
                                        Channel.WEB))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void inactiveStudentLeavesTheDefaultListsAndReactivationBringsItBack() {
        SearchStudentsUseCase search = new SearchStudentsUseCase(c.students, c.open, eligibility);
        inactivateMatteo();

        assertThat(search.run(c.clara.id(), "").students()).isEmpty();

        c.matteo.reactivate(c.clock.now());
        c.students.save(c.matteo);

        assertThat(search.run(c.clara.id(), "").students())
                .extracting(StudentSummary::name)
                .containsExactly("Matteo Mafra");
        assertThat(
                        register.run(
                                c.clara.id(),
                                c.matteo.id(),
                                ReportScenario.file("c.ogg", 10),
                                Channel.WEB))
                .isNotNull();
    }

    @Test
    void associatedTeacherStillSeesAndCancelsTheReportAlreadyOpen() {
        LessonReport report = c.reportInReview();
        inactivateMatteo();

        // o relatório em aberto continua acessível ao professor associado
        assertThat(c.access.lessonReport(c.clara.id(), report.id())).isSameAs(report);

        c.cancel.cancel(c.clara.id(), report.id());

        assertThat(c.reports.searchById(report.id()).orElseThrow().status())
                .isEqualTo(ReportStatus.CANCELLED);
    }
}
