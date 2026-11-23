package br.com.felipeblaft.reports.inbound.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

/**
 * Contrato dos endpoints de relatório de aula (registro, respostas, alteração, aprovação,
 * cancelamento).
 */
class LessonReportContractIT extends ContractApiBase {

    private Student matteo;
    private Student helena;
    private Cookie session;
    private Cookie sessionBruno;

    @BeforeEach
    void load() throws Exception {
        Teacher clara = scenario.clara();
        Teacher bruno = scenario.bruno();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        helena = scenario.student(bruno, "Helena Prado", "Flauta");
        session = signIn(ScenarioIT.CODE_CLARA);
        sessionBruno = signIn(ScenarioIT.CODE_BRUNO);
    }

    private static MockMultipartFile audio(String type, byte[] content) {
        return new MockMultipartFile("audio", "lesson.webm", type, content);
    }

    private static MockMultipartFile audio() {
        return audio("audio/webm;codecs=opus", AudioNormalizationTest.AUDIO_WITH_SPEECH);
    }

    private UUID register() throws Exception {
        String body =
                mvc.perform(
                                authenticated(
                                        multipart(
                                                        "/api/v1/students/{id}/lesson-reports",
                                                        matteo.id())
                                                .file(audio()),
                                        session))
                        .andExpect(status().isAccepted())
                        .andExpect(replyFollowsContract())
                        .andExpect(jsonPath("$.jobId").isString())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1"));
    }

    private UUID reportWithQuestions() throws Exception {
        register();
        scenario.processQueue();
        return jdbc.queryForObject("select id from lesson_report", UUID.class);
    }

    private UUID reportInReview() throws Exception {
        UUID id = reportWithQuestions();
        mvc.perform(
                        authenticated(post("/api/v1/lesson-reports/{id}/answers", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"Estudou bem.\"}"))
                .andExpect(status().isAccepted());
        scenario.processQueue();
        return id;
    }

    @Test
    void registerAcceptsAudioAndReturnsJob() throws Exception {
        UUID jobId = register();

        mvc.perform(get("/api/v1/jobs/{id}", jobId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.type").value("LESSON_REGISTRATION"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.step").value("UPLOADING"))
                .andExpect(jsonPath("$.delayed").value(false));
    }

    @Test
    void registerRefusalUnassignedStudentFormatSizeAndOpenReport() throws Exception {
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/students/{id}/lesson-reports", helena.id())
                                        .file(audio()),
                                session))
                .andExpect(status().isNotFound())
                .andExpect(replyFollowsContract())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/students/{id}/lesson-reports", matteo.id())
                                        .file(
                                                new MockMultipartFile(
                                                        "audio",
                                                        "notes.txt",
                                                        "text/plain",
                                                        new byte[] {1})),
                                session))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(replyFollowsContract())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FORMAT"));
        // SEC-003: tipo declarado de áudio não basta; a assinatura do arquivo decide
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/students/{id}/lesson-reports", matteo.id())
                                        .file(
                                                audio(
                                                        "audio/mpeg",
                                                        "<!doctype html><script>alert(1)</script>"
                                                                .getBytes(
                                                                        java.nio.charset
                                                                                .StandardCharsets
                                                                                .UTF_8))),
                                session))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(replyFollowsContract())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FORMAT"));
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/students/{id}/lesson-reports", matteo.id())
                                        .file(audio("audio/mpeg", new byte[50 * 1024 * 1024 + 1])),
                                session))
                .andExpect(status().isContentTooLarge())
                .andExpect(replyFollowsContract())
                .andExpect(jsonPath("$.code").value("AUDIO_TOO_LARGE"))
                .andExpect(jsonPath("$.details.limitMb").value(50));

        register();
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/students/{id}/lesson-reports", matteo.id())
                                        .file(audio()),
                                session))
                .andExpect(status().isConflict())
                .andExpect(replyFollowsContract())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_OPEN"))
                .andExpect(jsonPath("$.message", startsWith("Você tem um relatório em aberto.")))
                .andExpect(jsonPath("$.details.openReport.originChannel").value("WEB"));
    }

    @Test
    void getReportWithQuestionsAndOtherTeacherGets404() throws Exception {
        UUID id = reportWithQuestions();

        mvc.perform(get("/api/v1/lesson-reports/{id}", id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.status").value("AWAITING_ANSWERS"))
                .andExpect(jsonPath("$.studentName").value("Matteo Ferreira"))
                .andExpect(jsonPath("$.teacherName").value("Clara Souza"))
                .andExpect(jsonPath("$.lessonDate").value("2026-09-21"))
                .andExpect(jsonPath("$.pendingQuestions", hasSize(2)))
                .andExpect(jsonPath("$.currentVersion").value(0))
                .andExpect(jsonPath("$.pdfAvailable").value(false))
                .andExpect(jsonPath("$.originChannel").value("WEB"));
        mvc.perform(get("/api/v1/lesson-reports/{id}", id).cookie(sessionBruno))
                .andExpect(status().isNotFound())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void answersByTextAndAudioAndEmptyAnswer() throws Exception {
        UUID id = reportWithQuestions();

        mvc.perform(
                        authenticated(post("/api/v1/lesson-reports/{id}/answers", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("EMPTY_ANSWER"));
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/lesson-reports/{id}/answers", id).file(audio()),
                                session))
                .andExpect(status().isAccepted())
                .andExpect(replyFollowsContract());
        mvc.perform(
                        authenticated(post("/api/v1/lesson-reports/{id}/answers", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"Estudou bem.\"}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("PROCESSING_IN_PROGRESS"));
    }

    @Test
    void changesWithBaseVersionAndNewAttemptOutsideStep() throws Exception {
        UUID id = reportInReview();

        mvc.perform(get("/api/v1/lesson-reports/{id}", id).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.status").value("AWAITING_REVIEW"))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.pdfAvailable").value(true))
                .andExpect(jsonPath("$.lastInteraction.type").value("QUESTION_ANSWER"))
                .andExpect(jsonPath("$.lastInteraction.text").value("Estudou bem."));
        mvc.perform(
                        authenticated(post("/api/v1/lesson-reports/{id}/changes", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"Diga que foi ótima.\",\"baseVersion\":3}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.details.currentVersion").value(1));
        mvc.perform(
                        authenticated(post("/api/v1/lesson-reports/{id}/changes", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"   \",\"baseVersion\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("EMPTY_CHANGE"));
        mvc.perform(authenticated(post("/api/v1/lesson-reports/{id}/retry", id), session))
                .andExpect(status().isConflict())
                .andExpect(followsContract());
        mvc.perform(
                        authenticated(
                                multipart("/api/v1/lesson-reports/{id}/changes", id)
                                        .file(audio())
                                        .param("baseVersion", "1"),
                                session))
                .andExpect(status().isAccepted())
                .andExpect(replyFollowsContract());
        scenario.processQueue();

        mvc.perform(get("/api/v1/lesson-reports/{id}", id).cookie(session))
                .andExpect(jsonPath("$.currentVersion").value(2))
                .andExpect(jsonPath("$.lastInteraction.medium").value("AUDIO"));
    }

    @Test
    void approveCitingVersionAndSecondApprovalRefused() throws Exception {
        UUID id = reportInReview();

        mvc.perform(
                        authenticated(post("/api/v1/lesson-reports/{id}/approval", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirmedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedVersion").value(1))
                .andExpect(jsonPath("$.approvedAt").value("2026-09-21T13:00:00Z"));
        mvc.perform(
                        authenticated(post("/api/v1/lesson-reports/{id}/approval", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirmedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_APPROVED"));
    }

    @Test
    void cancelAndAfterwardsAcceptsNoMoreActions() throws Exception {
        UUID id = reportInReview();

        mvc.perform(
                        authenticated(
                                post("/api/v1/lesson-reports/{id}/cancellation", id), sessionBruno))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
        mvc.perform(authenticated(post("/api/v1/lesson-reports/{id}/cancellation", id), session))
                .andExpect(status().isNoContent())
                .andExpect(followsContract());
        mvc.perform(
                        authenticated(post("/api/v1/lesson-reports/{id}/approval", id), session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"confirmedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("REPORT_CANCELLED"));
    }

    @Test
    void otherTeachersJobDoesNotExist() throws Exception {
        UUID jobId = register();

        mvc.perform(get("/api/v1/jobs/{id}", jobId).cookie(sessionBruno))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
        scenario.processQueue();
        mvc.perform(get("/api/v1/jobs/{id}", jobId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.resultCode").value("PENDING_QUESTIONS"))
                .andExpect(jsonPath("$.reportType").value("LESSON"));
    }
}
