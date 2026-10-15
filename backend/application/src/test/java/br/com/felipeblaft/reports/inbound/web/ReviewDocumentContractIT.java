package br.com.felipeblaft.reports.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Entrega autorizada dos PDFs (R-11, FR-048): inline, sem cache e sem identificador interno no
 * nome.
 */
class ReviewDocumentContractIT extends ContractApiBase {

    private Cookie session;
    private Cookie sessionBruno;
    private UUID reportId;

    @BeforeEach
    void reachReview() throws Exception {
        Teacher clara = scenario.clara();
        scenario.bruno();
        Student matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        session = signIn(ScenarioIT.CODE_CLARA);
        sessionBruno = signIn(ScenarioIT.CODE_BRUNO);
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/students/{id}/lesson-reports", matteo.id())
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "lesson.m4a",
                                                        "audio/mp4",
                                                        AudioNormalizationTest.AUDIO_WITH_SPEECH)),
                                session))
                .andExpect(status().isAccepted());
        scenario.processQueue();
        reportId = jdbc.queryForObject("select id from lesson_report", UUID.class);
        mvc.perform(
                        authenticated(
                                        post("/api/v1/lesson-reports/{id}/answers", reportId),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"Estudou bem.\"}"))
                .andExpect(status().isAccepted());
        scenario.processQueue();
    }

    @Test
    void inlineReviewPdfWithoutCacheAndNameWithoutIdentifier() throws Exception {
        MvcResult result =
                mvc.perform(
                                get("/api/v1/lesson-reports/{id}/versions/1/pdf", reportId)
                                        .cookie(session))
                        .andExpect(status().isOk())
                        .andExpect(followsContract())
                        .andExpect(header().string("Content-Type", "application/pdf"))
                        .andExpect(
                                header().string(
                                                "Content-Disposition",
                                                "inline; filename=\"relatorio-aula-2026-09-21-v1.pdf\""))
                        .andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control"))
                .contains("no-store")
                .contains("private");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .doesNotContain(reportId.toString());
        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");
    }

    @Test
    void otherTeacherAndMissingVersionGet404() throws Exception {
        mvc.perform(
                        get("/api/v1/lesson-reports/{id}/versions/1/pdf", reportId)
                                .cookie(sessionBruno))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
        mvc.perform(get("/api/v1/lesson-reports/{id}/versions/7/pdf", reportId).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
        mvc.perform(get("/api/v1/lesson-reports/{id}/versions/1/pdf", reportId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void officialPdfOnlyAfterApproval() throws Exception {
        mvc.perform(get("/api/v1/lesson-reports/{id}/official-pdf", reportId).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
        mvc.perform(
                        authenticated(
                                        post("/api/v1/lesson-reports/{id}/approval", reportId),
                                        session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirmedVersion\":1}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/lesson-reports/{id}/official-pdf", reportId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        "inline; filename=\"relatorio-aula-2026-09-21.pdf\""));
        mvc.perform(get("/api/v1/lesson-reports/{id}/official-pdf", reportId).cookie(sessionBruno))
                .andExpect(status().isNotFound());
    }
}
