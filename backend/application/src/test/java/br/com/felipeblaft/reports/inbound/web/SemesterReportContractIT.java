package br.com.felipeblaft.reports.inbound.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** Contrato do relatório semestral (FR-041 a FR-046): semestres, prévia, geração e o ciclo. */
class SemesterReportContractIT extends ContractApiBase {

    private Teacher clara;
    private Student matteo;
    private Student helena;
    private Cookie session;
    private Cookie sessionBruno;

    @BeforeEach
    void load() throws Exception {
        clara = scenario.clara();
        Teacher bruno = scenario.bruno();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        helena = scenario.student(bruno, "Helena Prado", "Flauta");
        session = signIn(ScenarioIT.CODE_CLARA);
        sessionBruno = signIn(ScenarioIT.CODE_BRUNO);
    }

    private void lessonsInSemester1() {
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 3, 10), "Escala de Dó");
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 5, 20), "Estudo nº 5");
    }

    private void generate(String body) throws Exception {
        mvc.perform(
                        authenticated(
                                        post("/api/v1/students/{id}/semester-reports", matteo.id()),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isAccepted())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.jobId").isString());
    }

    private UUID semesterInReview() throws Exception {
        lessonsInSemester1();
        generate("{\"periodKey\":\"2026-1\"}");
        scenario.processQueue();
        return jdbc.queryForObject("select id from semester_report", UUID.class);
    }

    @Test
    void semestersAndPreview() throws Exception {
        lessonsInSemester1();

        mvc.perform(get("/api/v1/students/{id}/semesters", matteo.id()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].key").value("2026-2"))
                .andExpect(jsonPath("$[0].label").value("2º semestre 2026"))
                .andExpect(jsonPath("$[0].inProgress").value(true))
                .andExpect(jsonPath("$[0].preselected").value(true))
                .andExpect(jsonPath("$[1].start").value("2026-01-01"));
        mvc.perform(
                        get("/api/v1/students/{id}/semesters/2026-1/preview", matteo.id())
                                .cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.approvedLessons").value(2))
                .andExpect(jsonPath("$.openLessonReports").value(0))
                .andExpect(jsonPath("$.approvedSemesterReportId").isEmpty());
        mvc.perform(get("/api/v1/students/{id}/semesters", helena.id()).cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(followsContract());
        mvc.perform(
                        get("/api/v1/students/{id}/semesters/2026-9/preview", matteo.id())
                                .cookie(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generationRefusedWithoutLessonsAndWithOpenSemester() throws Exception {
        mvc.perform(
                        authenticated(
                                        post("/api/v1/students/{id}/semester-reports", matteo.id()),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodKey\":\"2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("NO_APPROVED_LESSONS_IN_PERIOD"));

        lessonsInSemester1();
        generate("{\"periodKey\":\"2026-1\"}");
        mvc.perform(
                        authenticated(
                                        post("/api/v1/students/{id}/semester-reports", matteo.id()),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodKey\":\"2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_OPEN"))
                .andExpect(jsonPath("$.details.openReport.type").value("SEMESTER"));
        scenario.processQueue();
    }

    @Test
    void reviewChangeApprovalAndPdf() throws Exception {
        UUID id = semesterInReview();

        mvc.perform(get("/api/v1/semester-reports/{id}", id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.status").value("AWAITING_REVIEW"))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.periodKey").value("2026-1"))
                .andExpect(jsonPath("$.generalInformation.name").value("Matteo Ferreira"))
                .andExpect(jsonPath("$.generalInformation.consideredLessonCount").value(2))
                .andExpect(jsonPath("$.generalInformation.periodLabel").value("1º semestre 2026"))
                .andExpect(jsonPath("$.content.finalOpinion").isString())
                .andExpect(jsonPath("$.openReportCount").value(0));
        mvc.perform(get("/api/v1/semester-reports/{id}", id).cookie(sessionBruno))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
        mvc.perform(get("/api/v1/semester-reports/{id}/versions/1/pdf", id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "inline; filename=\"relatorio-semestral-2026-1-v1.pdf\""));

        mvc.perform(
                        authenticated(post("/api/v1/semester-reports/{id}/changes", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"text\":\"Parecer: excelente semestre.\",\"baseVersion\":1}"))
                .andExpect(status().isAccepted())
                .andExpect(followsContract());
        scenario.processQueue();
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/semester-reports/{id}/changes", id)
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "a.webm",
                                                        "audio/webm",
                                                        AudioNormalizationTest.AUDIO_WITH_SPEECH))
                                        .param("baseVersion", "2"),
                                session))
                .andExpect(status().isAccepted())
                .andExpect(replyFollowsContract());
        scenario.processQueue();
        mvc.perform(get("/api/v1/semester-reports/{id}", id).cookie(session))
                .andExpect(jsonPath("$.currentVersion").value(3))
                .andExpect(jsonPath("$.lastInteraction.medium").value("AUDIO"));

        mvc.perform(
                        authenticated(post("/api/v1/semester-reports/{id}/approval", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirmedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        mvc.perform(
                        authenticated(post("/api/v1/semester-reports/{id}/approval", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirmedVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.approvedVersion").value(3));
        mvc.perform(get("/api/v1/semester-reports/{id}/official-pdf", id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract());
        mvc.perform(
                        authenticated(
                                        post("/api/v1/students/{id}/semester-reports", matteo.id()),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodKey\":\"2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("SEMESTER_REPORT_ALREADY_APPROVED"));
    }

    @Test
    void openLessonReportsAskConfirmationAndCancellation() throws Exception {
        lessonsInSemester1();
        jdbc.update(
                "update lesson_report set status = 'AWAITING_REVIEW', approved_at = null,"
                        + " approved_version = null, approval_channel = null"
                        + " where id = (select r.id from lesson_report r join lesson a on a.id = r.lesson_id"
                        + " where a.lesson_date = '2026-05-20')");
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 4, 2), "Leitura");

        mvc.perform(
                        authenticated(
                                        post("/api/v1/students/{id}/semester-reports", matteo.id()),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodKey\":\"2026-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("OPEN_REPORTS_EXIST"))
                .andExpect(jsonPath("$.details.quantity").value(1));
        generate("{\"periodKey\":\"2026-1\",\"confirmDespiteOpenReports\":true}");
        scenario.processQueue();
        UUID id = jdbc.queryForObject("select id from semester_report", UUID.class);
        mvc.perform(get("/api/v1/semester-reports/{id}", id).cookie(session))
                .andExpect(jsonPath("$.openReportCount").value(1))
                .andExpect(jsonPath("$.generalInformation.consideredLessonCount").value(2));

        mvc.perform(authenticated(post("/api/v1/semester-reports/{id}/cancellation", id), session))
                .andExpect(status().isNoContent())
                .andExpect(followsContract());
        mvc.perform(authenticated(post("/api/v1/semester-reports/{id}/cancellation", id), session))
                .andExpect(status().isConflict())
                .andExpect(followsContract());
    }
}
