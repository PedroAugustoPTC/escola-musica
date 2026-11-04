package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Contrato das quatro rotas de PDF da visão da escola (feature 006, US2): {@code application/pdf},
 * {@code inline} para abrir e {@code attachment} para baixar, o mesmo nome sugerido do professor,
 * {@code no-store, private} e bytes iguais aos do PDF oficial do professor autor (FR-016). Pilha
 * real, sem dublê de caso de uso.
 */
class AdminSchoolReportDocumentContractIT extends SchoolHistoryBase {

    private Cookie administrator;
    private Cookie clara;
    private LessonReport lesson;
    private UUID semester;

    @BeforeEach
    void prepare() throws Exception {
        Teacher teacher = scenario.clara();
        Student student = scenario.student(teacher, "Matteo Mafra", "Piano");
        lesson =
                scenario.approvedLesson(
                        teacher, student, LocalDate.of(2026, 3, 10), "Sopro com apoio");
        administrator = signInNewAdministrator();
        clara = signInTeacher(ScenarioIT.CODE_CLARA);
        semester = approvedSemester(clara, student.id(), "2026-1");
    }

    private static String dispositionKind(MockHttpServletResponse response) {
        String header = response.getHeader("Content-Disposition");
        assertThat(header).isNotNull();
        return header.substring(0, header.indexOf(';'));
    }

    private static String suggestedName(MockHttpServletResponse response) {
        String header = response.getHeader("Content-Disposition");
        return header.substring(header.indexOf(';') + 1).strip();
    }

    private void assertPdf(MockHttpServletResponse response, String kind) {
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("application/pdf");
        assertThat(dispositionKind(response)).isEqualTo(kind);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store, private");
    }

    @Test
    void lessonPdfOpensAndDownloadsWithTheTeachersBytesAndName() throws Exception {
        MockHttpServletResponse teacher =
                call(HttpMethod.GET, "/api/v1/lesson-reports/" + lesson.id() + "/official-pdf", clara);
        assertThat(teacher.getStatus()).isEqualTo(200);

        MockHttpServletResponse open =
                call(HttpMethod.GET, lessonPdf(lesson.id()), administrator);
        MockHttpServletResponse download =
                call(HttpMethod.GET, lessonDownload(lesson.id()), administrator);

        assertPdf(open, "inline");
        assertPdf(download, "attachment");
        assertThat(open.getContentAsByteArray())
                .isNotEmpty()
                .isEqualTo(teacher.getContentAsByteArray());
        assertThat(download.getContentAsByteArray()).isEqualTo(teacher.getContentAsByteArray());
        assertThat(suggestedName(open)).isEqualTo(suggestedName(teacher));
        assertThat(suggestedName(download)).isEqualTo(suggestedName(teacher));
        assertThat(suggestedName(open)).contains("relatorio-aula-2026-03-10.pdf");
    }

    @Test
    void semesterPdfOpensAndDownloadsWithTheTeachersBytesAndName() throws Exception {
        MockHttpServletResponse teacher =
                call(
                        HttpMethod.GET,
                        "/api/v1/semester-reports/" + semester + "/official-pdf",
                        clara);
        assertThat(teacher.getStatus()).isEqualTo(200);

        MockHttpServletResponse open = call(HttpMethod.GET, semesterPdf(semester), administrator);
        MockHttpServletResponse download =
                call(HttpMethod.GET, semesterDownload(semester), administrator);

        assertPdf(open, "inline");
        assertPdf(download, "attachment");
        assertThat(open.getContentAsByteArray())
                .isNotEmpty()
                .isEqualTo(teacher.getContentAsByteArray());
        assertThat(download.getContentAsByteArray()).isEqualTo(teacher.getContentAsByteArray());
        assertThat(suggestedName(open)).isEqualTo(suggestedName(teacher));
        assertThat(suggestedName(open)).contains("relatorio-semestral-2026-1.pdf");
    }

    @Test
    void unknownReportIsNotFoundWithNoStore() throws Exception {
        MockHttpServletResponse response =
                call(HttpMethod.GET, lessonPdf(UUID.randomUUID()), administrator);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(errorCode(response)).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void withoutSessionIsUnauthorized() throws Exception {
        MockHttpServletResponse response = call(HttpMethod.GET, lessonPdf(lesson.id()), null);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotStartWith("%PDF");
    }
}
