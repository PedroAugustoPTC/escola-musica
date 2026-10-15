package br.com.felipeblaft.reports.inbound.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Contrato do histórico e dos PDFs oficiais (FR-047, FR-048). */
class HistoryContractIT extends ContractApiBase {

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

    @Test
    void emptyHistoryAndOtherTeachersStudent() throws Exception {
        mvc.perform(get("/api/v1/students/{id}/history", matteo.id()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.student.name").value("Matteo Ferreira"))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/v1/students/{id}/history", helena.id()).cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(followsContract());
    }

    @Test
    void lessonsAndSemesterFromNewestToOldestWithOfficialPdf() throws Exception {
        LessonReport milestone =
                scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 3, 10), "Escala");
        clock.advance(Duration.ofMinutes(1));
        LessonReport september =
                scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 9, 1), "Estudo");
        mvc.perform(
                        authenticated(
                                        post("/api/v1/students/{id}/semester-reports", matteo.id()),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"periodKey\":\"2026-1\"}"))
                .andExpect(status().isAccepted());
        scenario.processQueue();
        UUID semester = jdbc.queryForObject("select id from semester_report", UUID.class);
        mvc.perform(
                        authenticated(
                                        post("/api/v1/semester-reports/{id}/approval", semester),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirmedVersion\":1}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/students/{id}/history", matteo.id()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].reportId").value(september.id().toString()))
                .andExpect(jsonPath("$.items[0].label").value("Aula de 01/09/2026"))
                .andExpect(jsonPath("$.items[0].lessonDate").value("2026-09-01"))
                .andExpect(jsonPath("$.items[1].reportId").value(milestone.id().toString()))
                .andExpect(jsonPath("$.items[2].type").value("SEMESTER"))
                .andExpect(
                        jsonPath("$.items[2].label").value("Semestral (01/01/2026 a 30/06/2026)"))
                .andExpect(jsonPath("$.items[2].periodStart").value("2026-01-01"));

        mvc.perform(get("/api/v1/lesson-reports/{id}/official-pdf", september.id()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "inline; filename=\"relatorio-aula-2026-09-01.pdf\""));
        mvc.perform(get("/api/v1/semester-reports/{id}/official-pdf", semester).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "inline; filename=\"relatorio-semestral-2026-1.pdf\""));
        mvc.perform(
                        get("/api/v1/lesson-reports/{id}/official-pdf", september.id())
                                .cookie(sessionBruno))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
        mvc.perform(
                        get("/api/v1/semester-reports/{id}/official-pdf", semester)
                                .cookie(sessionBruno))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
    }
}
