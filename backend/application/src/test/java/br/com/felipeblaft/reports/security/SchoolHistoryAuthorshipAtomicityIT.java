package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Atomicidade do registro de autoria da visão da escola (FR-023): se o registro de sucesso não
 * puder ser gravado, a lista e o PDF respondem {@code INTERNAL_ERROR} com {@code reference}, sem
 * itens e sem bytes de PDF, e nenhum registro de sucesso fica no banco.
 */
class SchoolHistoryAuthorshipAtomicityIT extends SchoolHistoryBase {

    @MockitoSpyBean AuthorshipRecorderPort recorder;

    private Cookie administrator;
    private UUID lesson;

    @BeforeEach
    void prepare() throws Exception {
        Teacher clara = scenario.clara();
        Student student = scenario.student(clara, "Matteo Mafra", "Piano");
        lesson = scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 10), "Sopro").id();
        administrator = signInNewAdministrator();
        clearAuthorship();
    }

    private void failRecording() {
        doThrow(new IllegalStateException("falha simulada")).when(recorder).record(any());
    }

    private void assertInternalErrorWithReference(MockHttpServletResponse response)
            throws Exception {
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(errorCode(response)).isEqualTo("INTERNAL_ERROR");
        assertThat(body(response).path("reference").asString()).isNotBlank();
    }

    @Test
    void listFailsWithoutItemsWhenTheRecordCannotBeWritten() throws Exception {
        failRecording();

        MockHttpServletResponse response = call(HttpMethod.GET, SCHOOL_HISTORY, administrator);

        assertInternalErrorWithReference(response);
        assertThat(response.getContentAsString()).doesNotContain("items", "reportId", "Matteo");
        assertThat(authorship("SCHOOL_HISTORY_QUERY")).isEmpty();
    }

    @Test
    void pdfFailsWithoutBytesWhenTheRecordCannotBeWritten() throws Exception {
        failRecording();

        for (String route : new String[] {lessonPdf(lesson), lessonDownload(lesson)}) {
            MockHttpServletResponse response = call(HttpMethod.GET, route, administrator);

            assertInternalErrorWithReference(response);
            assertThat(response.getContentType()).doesNotContain("pdf");
            assertThat(new String(response.getContentAsByteArray())).doesNotContain("%PDF");
        }
        assertThat(authorship("SCHOOL_REPORT_DOCUMENT_OPEN")).isEmpty();
        assertThat(authorship("SCHOOL_REPORT_DOCUMENT_DOWNLOAD")).isEmpty();
    }
}
