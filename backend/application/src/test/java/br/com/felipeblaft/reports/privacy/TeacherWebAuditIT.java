package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.PersonalDataSweep;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Eventos do professor na Web, pela pilha real (filtro de correlação, MDC, casos de uso e banco):
 * cada operação deixa exatamente um evento com papel, canal, alvo e versão, sem dado pessoal, e o
 * identificador de correlação do evento é o da requisição (FR-044, FR-050).
 */
class TeacherWebAuditIT extends ContractApiBase {

    private Student matteo;
    private Cookie session;

    @BeforeEach
    void load() throws Exception {
        Teacher clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        session = signIn(ScenarioIT.CODE_CLARA);
        // a entrada do professor é um evento de autenticação; a contagem parte daqui
        jdbc.update("alter table authorship_record disable trigger user");
        jdbc.update("delete from authorship_record");
        jdbc.update("alter table authorship_record enable trigger user");
    }

    private List<Map<String, Object>> events(String operation) {
        return jdbc.queryForList(
                "select * from authorship_record where operation = ? order by sequence", operation);
    }

    private UUID reportInReview() throws Exception {
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/students/{id}/lesson-reports", matteo.id())
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "lesson.webm",
                                                        "audio/webm;codecs=opus",
                                                        AudioNormalizationTest.AUDIO_WITH_SPEECH)),
                                session))
                .andExpect(status().isAccepted());
        scenario.processQueue();
        UUID id = jdbc.queryForObject("select id from lesson_report", UUID.class);
        mvc.perform(
                        authenticated(post("/api/v1/lesson-reports/{id}/answers", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"Estudou bem.\"}"))
                .andExpect(status().isAccepted());
        scenario.processQueue();
        return id;
    }

    @Test
    void approvalReadsAndSignOutLeaveExactlyOneEventEachWithTheRequestCorrelation()
            throws Exception {
        UUID id = reportInReview();

        mvc.perform(
                        authenticated(post("/api/v1/lesson-reports/{id}/approval", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirmedVersion\":1}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/lesson-reports/{id}/official-pdf", id).cookie(session))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/students/{id}/history", matteo.id()).cookie(session))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/session").cookie(session).with(csrfHeader()))
                .andExpect(status().is2xxSuccessful());

        Map<String, Object> approval = events("LESSON_REPORT_APPROVE").getFirst();
        assertThat(events("LESSON_REPORT_APPROVE")).hasSize(1);
        assertThat(approval.get("origin")).isEqualTo("WEB");
        assertThat(approval.get("author_role")).isEqualTo("TEACHER");
        assertThat(approval.get("target_type")).isEqualTo("LESSON_REPORT");
        assertThat(approval.get("target_id")).isEqualTo(id);
        assertThat(approval.get("document_version")).isEqualTo(1);
        assertThat(approval.get("correlation_id").toString()).matches("[0-9a-f-]{36}");

        assertThat(events("REPORT_DOCUMENT_OPEN")).hasSize(1);
        assertThat(events("REPORT_DOCUMENT_OPEN").getFirst().get("document_version")).isEqualTo(1);
        assertThat(events("HISTORY_QUERY")).hasSize(1);
        assertThat(events("HISTORY_QUERY").getFirst().get("target_id")).isEqualTo(matteo.id());
        assertThat(events("TEACHER_SIGN_OUT")).hasSize(1);

        // cada pedido tem a sua correlação: as quatro são distintas
        assertThat(
                        List.of(
                                        events("LESSON_REPORT_APPROVE"),
                                        events("REPORT_DOCUMENT_OPEN"),
                                        events("HISTORY_QUERY"),
                                        events("TEACHER_SIGN_OUT"))
                                .stream()
                                .map(rows -> rows.getFirst().get("correlation_id"))
                                .distinct())
                .hasSize(4);
    }

    @Test
    void theTrailCarriesNoPersonalData() throws Exception {
        UUID id = reportInReview();
        mvc.perform(
                        authenticated(post("/api/v1/lesson-reports/{id}/approval", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirmedVersion\":1}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/students/{id}/history", matteo.id()).cookie(session))
                .andExpect(status().isOk());

        PersonalDataSweep sweep = PersonalDataSweep.of(jdbc);
        var audit = sweep.newAudit();
        sweep.sweep(audit, "trilha do professor", null);
        audit.assertClean();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrfHeader() {
        return org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.csrf()
                .asHeader();
    }
}
