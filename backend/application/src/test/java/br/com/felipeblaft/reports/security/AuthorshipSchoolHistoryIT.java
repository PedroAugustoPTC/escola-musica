package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AdministratorFixture;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Registro de autoria da visão da escola (feature 006, US4): os eventos E1 a E7 de {@code
 * contracts/authorship-events.md}, um registro por requisição, com autor, papel, canal, operação,
 * alvo, detalhe, resultado e correlação. Postgres real, pilha inteira.
 *
 * <p>Desvio conhecido do E7: a credencial cruzada (professor nas rotas {@code /admin}) ainda não
 * grava {@code AUTHORIZATION_DENIED} (pendência aceita da feature 005); o teste confirma a resposta
 * de rota inexistente e que nenhuma operação da visão da escola é registrada, sem exigir o registro
 * que hoje não existe.
 */
class AuthorshipSchoolHistoryIT extends SchoolHistoryBase {

    private static final String DETAIL_COLUMNS =
            "author_id, author_role, origin, operation, target_type, target_id, result,"
                    + " error_code, correlation_id, filter_teacher_id, filter_report_type,"
                    + " filter_period_start, filter_period_end, page_number, document_version";

    private AdministratorFixture fixture;
    private Cookie administrator;
    private Cookie clara;
    private Teacher claraTeacher;
    private Student student;
    private UUID lesson;
    private UUID semester;

    @BeforeEach
    void prepare() throws Exception {
        claraTeacher = scenario.clara();
        student = scenario.student(claraTeacher, "Matteo Mafra", "Piano");
        lesson =
                scenario.approvedLesson(claraTeacher, student, LocalDate.of(2026, 3, 10), "Sopro")
                        .id();
        fixture = insertAdministrator();
        administrator = signInAdministrator(fixture);
        clara = signInTeacher(ScenarioIT.CODE_CLARA);
        semester = approvedSemester(clara, student.id(), "2026-1");
        clearAuthorship();
    }

    private List<Map<String, Object>> reads(String operation) {
        return jdbc.queryForList(
                "select "
                        + DETAIL_COLUMNS
                        + " from authorship_record where operation = ? order by occurred_at, ctid",
                operation);
    }

    private Map<String, Object> only(String operation) {
        List<Map<String, Object>> rows = reads(operation);
        assertThat(rows).as("registros de " + operation).hasSize(1);
        return rows.getFirst();
    }

    private void assertAdministratorWeb(Map<String, Object> row, String result) {
        assertThat(row.get("author_id")).isEqualTo(fixture.administrator().id());
        assertThat(row.get("author_role")).isEqualTo("ADMINISTRATOR");
        assertThat(row.get("origin")).isEqualTo("WEB");
        assertThat(row.get("result")).isEqualTo(result);
        assertThat((String) row.get("correlation_id")).isNotBlank();
    }

    @Test
    void e1ListPageDeliveredLeavesOneSuccessWithFiltersAndPage() throws Exception {
        MockHttpServletResponse response =
                call(
                        HttpMethod.GET,
                        SCHOOL_HISTORY
                                + "?studentId="
                                + student.id()
                                + "&teacherId="
                                + claraTeacher.id()
                                + "&type=LESSON&from=2026-03-01&to=2026-03-31&page=0&size=5",
                        administrator);
        assertThat(response.getStatus()).isEqualTo(200);

        Map<String, Object> row = only("SCHOOL_HISTORY_QUERY");
        assertAdministratorWeb(row, "SUCCESS");
        assertThat(row.get("target_type")).isEqualTo("STUDENT");
        assertThat(row.get("target_id")).isEqualTo(student.id());
        assertThat(row.get("error_code")).isNull();
        assertThat(row.get("filter_teacher_id")).isEqualTo(claraTeacher.id());
        assertThat(row.get("filter_report_type")).isEqualTo("LESSON");
        assertThat(String.valueOf(row.get("filter_period_start"))).isEqualTo("2026-03-01");
        assertThat(String.valueOf(row.get("filter_period_end"))).isEqualTo("2026-03-31");
        assertThat(row.get("page_number")).isEqualTo(0);
        assertThat(row.get("document_version")).isNull();
    }

    @Test
    void e1WithoutStudentHasNoTargetAndEachPageIsOneRecord() throws Exception {
        assertThat(call(HttpMethod.GET, SCHOOL_HISTORY + "?size=1", administrator).getStatus())
                .isEqualTo(200);
        assertThat(
                        call(HttpMethod.GET, SCHOOL_HISTORY + "?size=1&page=1", administrator)
                                .getStatus())
                .isEqualTo(200);

        List<Map<String, Object>> rows = reads("SCHOOL_HISTORY_QUERY");
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(row -> row.get("target_type") == null);
        assertThat(rows).allMatch(row -> row.get("target_id") == null);
        assertThat(rows).extracting(row -> row.get("page_number")).containsExactly(0, 1);
        assertThat(rows.get(0).get("correlation_id"))
                .isNotEqualTo(rows.get(1).get("correlation_id"));
    }

    @Test
    void e2RefusedFilterLeavesOneFailureWithTheValidFilters() throws Exception {
        MockHttpServletResponse response =
                call(
                        HttpMethod.GET,
                        SCHOOL_HISTORY
                                + "?studentId="
                                + student.id()
                                + "&teacherId="
                                + claraTeacher.id()
                                + "&from=2026-05-02&to=2026-05-01",
                        administrator);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo("VALIDATION_FAILED");

        Map<String, Object> row = only("SCHOOL_HISTORY_QUERY");
        assertAdministratorWeb(row, "FAILURE");
        assertThat(row.get("error_code")).isEqualTo("VALIDATION_FAILED");
        assertThat(row.get("target_type")).isEqualTo("STUDENT");
        assertThat(row.get("target_id")).isEqualTo(student.id());
        assertThat(row.get("filter_teacher_id")).isEqualTo(claraTeacher.id());
    }

    @Test
    void e3AndE4OpenAndDownloadLeaveOneSuccessEachWithTheDocumentVersion() throws Exception {
        assertThat(call(HttpMethod.GET, lessonPdf(lesson), administrator).getStatus())
                .isEqualTo(200);
        assertThat(call(HttpMethod.GET, semesterDownload(semester), administrator).getStatus())
                .isEqualTo(200);

        Map<String, Object> open = only("SCHOOL_REPORT_DOCUMENT_OPEN");
        assertAdministratorWeb(open, "SUCCESS");
        assertThat(open.get("target_type")).isEqualTo("LESSON_REPORT");
        assertThat(open.get("target_id")).isEqualTo(lesson);
        assertThat(open.get("document_version")).isEqualTo(1);
        assertThat(open.get("page_number")).isNull();
        assertThat(open.get("filter_teacher_id")).isNull();

        Map<String, Object> download = only("SCHOOL_REPORT_DOCUMENT_DOWNLOAD");
        assertAdministratorWeb(download, "SUCCESS");
        assertThat(download.get("target_type")).isEqualTo("SEMESTER_REPORT");
        assertThat(download.get("target_id")).isEqualTo(semester);
        assertThat(download.get("document_version")).isEqualTo(1);
    }

    @Test
    void e5UnknownOrNotApprovedReportLeavesOneFailureResourceNotFound() throws Exception {
        UUID absent = UUID.fromString("00000000-0000-4000-8000-000000000999");
        UUID notApproved =
                scenario.approvedLesson(claraTeacher, student, LocalDate.of(2026, 4, 1), "x").id();
        revertLessonTo(notApproved, "AWAITING_REVIEW");

        assertThat(call(HttpMethod.GET, lessonPdf(absent), administrator).getStatus())
                .isEqualTo(404);
        assertThat(call(HttpMethod.GET, lessonDownload(notApproved), administrator).getStatus())
                .isEqualTo(404);

        Map<String, Object> open = only("SCHOOL_REPORT_DOCUMENT_OPEN");
        assertAdministratorWeb(open, "FAILURE");
        assertThat(open.get("error_code")).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(open.get("target_type")).isEqualTo("LESSON_REPORT");
        assertThat(open.get("target_id")).isEqualTo(absent);
        assertThat(open.get("document_version")).isNull();

        Map<String, Object> download = only("SCHOOL_REPORT_DOCUMENT_DOWNLOAD");
        assertAdministratorWeb(download, "FAILURE");
        assertThat(download.get("error_code")).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(download.get("target_id")).isEqualTo(notApproved);
    }

    @Test
    void e6ApprovedPdfUnavailableLeavesOneFailureInternalError() throws Exception {
        jdbc.update("delete from pdf_document where type = 'OFFICIAL'");

        MockHttpServletResponse response = call(HttpMethod.GET, lessonPdf(lesson), administrator);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(errorCode(response)).isEqualTo("INTERNAL_ERROR");
        Map<String, Object> row = only("SCHOOL_REPORT_DOCUMENT_OPEN");
        assertAdministratorWeb(row, "FAILURE");
        assertThat(row.get("error_code")).isEqualTo("INTERNAL_ERROR");
        assertThat(row.get("target_id")).isEqualTo(lesson);
    }

    @Test
    void e7TeacherCredentialOnSchoolRoutesGetsTheUnknownRouteAnswerAndNoReadRecord()
            throws Exception {
        for (String route :
                List.of(SCHOOL_HISTORY, lessonPdf(lesson), semesterDownload(semester))) {
            assertThat(call(HttpMethod.GET, route, clara).getStatus()).isEqualTo(404);
        }

        // desvio registrado: AUTHORIZATION_DENIED da credencial cruzada é pendência da 005
        for (String operation :
                List.of(
                        "SCHOOL_HISTORY_QUERY",
                        "SCHOOL_REPORT_DOCUMENT_OPEN",
                        "SCHOOL_REPORT_DOCUMENT_DOWNLOAD")) {
            assertThat(reads(operation)).as(operation).isEmpty();
        }
    }

    @Test
    void studentSearchAndTeacherListLeaveNoReadRecord() throws Exception {
        assertThat(
                        call(HttpMethod.GET, ADMIN + "/students?search=Matteo", administrator)
                                .getStatus())
                .isEqualTo(200);
        assertThat(call(HttpMethod.GET, ADMIN + "/teachers", administrator).getStatus())
                .isEqualTo(200);

        assertThat(authorship()).isEmpty();
    }
}
