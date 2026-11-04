package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.TaskLlm;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Leitura do PDF oficial da versão aprovada e nome sugerido, iguais para professor e escola. */
class OfficialDocumentServiceTest {

    private final ReportScenario c = new ReportScenario();
    private final OfficialDocumentService service =
            new OfficialDocumentService(c.versions, c.pdfDocuments, c.documents);

    private LessonReport approvedLessonWithPdf() {
        LessonReport review = c.reportInReview();
        c.approve.run(c.clara.id(), review.id(), review.currentVersion(), Channel.WEB);
        return c.reports.searchById(review.id()).orElseThrow();
    }

    private SemesterReport approvedSemesterWithPdf() {
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        c.llm.schedule(TaskLlm.SEMESTER_CONSOLIDATION, ReportScenario.semesterOutput("Bom."));
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);
        c.processQueue();
        SemesterReport semester = c.semester.reports.values().iterator().next();
        c.approveSemester.run(c.clara.id(), semester.id(), 1, Channel.WEB);
        return c.semester.searchById(semester.id()).orElseThrow();
    }

    @Test
    void readsTheOfficialPdfOfTheApprovedLessonVersion() {
        LessonReport report = approvedLessonWithPdf();

        assertThat(service.readOfficial(ReportType.LESSON, report.id(), report.approvedVersion()))
                .hasValueSatisfying(bytes -> assertThat(bytes).isNotEmpty());
    }

    @Test
    void readsTheOfficialPdfOfTheApprovedSemesterVersion() {
        SemesterReport report = approvedSemesterWithPdf();

        assertThat(service.readOfficial(ReportType.SEMESTER, report.id(), report.approvedVersion()))
                .hasValueSatisfying(bytes -> assertThat(bytes).isNotEmpty());
    }

    @Test
    void sameBytesAsTheTeacherPath() {
        LessonReport report = approvedLessonWithPdf();

        byte[] teacher =
                c.getDocument.official(c.clara.id(), ReportType.LESSON, report.id()).content();

        assertThat(service.readOfficial(ReportType.LESSON, report.id(), report.approvedVersion()))
                .hasValueSatisfying(bytes -> assertThat(bytes).isEqualTo(teacher));
    }

    @Test
    void suggestedNamesMatchTheTeacherPath() {
        LessonReport lesson = approvedLessonWithPdf();
        LocalDate lessonDate = c.reports.searchLesson(lesson.lessonId()).orElseThrow().lessonDate();

        assertThat(OfficialDocumentService.lessonFileName(lessonDate))
                .isEqualTo(
                        c.getDocument
                                .official(c.clara.id(), ReportType.LESSON, lesson.id())
                                .fileName());
        assertThat(OfficialDocumentService.semesterFileName("2026-1"))
                .isEqualTo("relatorio-semestral-2026-1.pdf");
    }

    @Test
    void missingVersionFailsWithoutTryingAnotherOne() {
        LessonReport report = approvedLessonWithPdf();

        assertThat(
                        service.readOfficial(
                                ReportType.LESSON, report.id(), report.approvedVersion() + 1))
                .isEmpty();
        assertThat(service.readOfficial(ReportType.LESSON, UUID.randomUUID(), 1)).isEmpty();
    }

    @Test
    void unreadableDocumentFails() {
        LessonReport report = approvedLessonWithPdf();
        c.storage.files.clear();

        assertThat(service.readOfficial(ReportType.LESSON, report.id(), report.approvedVersion()))
                .isEmpty();
    }
}
