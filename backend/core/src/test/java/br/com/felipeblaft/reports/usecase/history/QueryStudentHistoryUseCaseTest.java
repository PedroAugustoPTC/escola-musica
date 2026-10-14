package br.com.felipeblaft.reports.usecase.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.TaskLlm;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase.History;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase.HistoryItem;
import br.com.felipeblaft.reports.usecase.home.GetStudentSummaryUseCase;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class QueryStudentHistoryUseCaseTest {

    private final ReportScenario c = new ReportScenario();
    private final QueryStudentHistoryUseCase history =
            new QueryStudentHistoryUseCase(
                    new GetStudentSummaryUseCase(c.students, c.open, c.authorization),
                    c.reports,
                    c.semester);

    @Test
    void onlyTeachersApprovedFromNewestToOldest() {
        LessonReport milestone = c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        LessonReport maio = c.approvedLesson(c.matteo, LocalDate.of(2026, 5, 20), "Estudo");
        c.clock.advance(Duration.ofMinutes(5));
        LessonReport mayAfter =
                c.approvedLesson(c.matteo, LocalDate.of(2026, 5, 20), "Outro estudo");
        c.reportWithQuestions(); // em aberto: não entra
        c.approvedLesson(c.helena, LocalDate.of(2026, 4, 1), "De outro professor");
        c.llm.schedule(TaskLlm.SEMESTER_CONSOLIDATION, ReportScenario.semesterOutput("Bom."));
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);
        c.processQueue();
        SemesterReport semester = c.semester.reports.values().iterator().next();
        c.approveSemester.run(c.clara.id(), semester.id(), 1, Channel.WEB);

        History result = history.run(c.clara.id(), c.matteo.id());

        assertThat(result.student().name()).isEqualTo("Matteo Mafra");
        assertThat(result.items())
                .extracting(HistoryItem::reportId)
                .containsExactly(mayAfter.id(), maio.id(), milestone.id(), semester.id());
        assertThat(result.items().getLast().type()).isEqualTo(ReportType.SEMESTER);
        assertThat(result.items().getLast().period().key()).isEqualTo("2026-1");
    }

    @Test
    void emptyAndOtherTeachersStudent() {
        assertThat(history.run(c.clara.id(), c.matteo.id()).items()).isEmpty();
        assertThatThrownBy(() -> history.run(c.clara.id(), c.helena.id()))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.STUDENT_NOT_ASSIGNED));
    }

    @Test
    void officialPdfOnlyOfTeachersApprovedReport() {
        LessonReport review = c.reportInReview();
        assertThatThrownBy(
                        () -> c.getDocument.official(c.clara.id(), ReportType.LESSON, review.id()))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        c.approve.run(c.clara.id(), review.id(), review.currentVersion(), Channel.WEB);
        assertThat(c.getDocument.official(c.clara.id(), ReportType.LESSON, review.id()).content())
                .isNotEmpty();
        assertThatThrownBy(
                        () -> c.getDocument.official(c.bruno.id(), ReportType.LESSON, review.id()))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
