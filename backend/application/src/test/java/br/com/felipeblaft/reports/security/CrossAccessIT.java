package br.com.felipeblaft.reports.security;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * SC-008: com a sessão e o vínculo de Clara, todo endpoint e toda ação do bot sobre o aluno e os
 * relatórios de Bruno são recusados (404 na Web, igual ao aluno inexistente, "Nenhum aluno
 * encontrado…" ou ação expirada no bot), sem revelar nenhum dado de Bruno nem de Helena.
 */
class CrossAccessIT extends TelegramConversationBase {

    private static final String BRUNO_CONTENT = "Sopro com apoio diafragmático";

    private Cookie sessionClara;
    private Student helena;
    private UUID approvedLesson;
    private UUID openLesson;
    private UUID job;
    private UUID semester;

    @BeforeEach
    void prepare() throws Exception {
        Teacher clara = scenario.clara();
        Teacher bruno = scenario.bruno();
        scenario.student(clara, "Matteo Mafra", "Piano");
        helena = scenario.student(bruno, "Helena Prado", "Flauta");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        sessionClara = signIn(ScenarioIT.CODE_CLARA);
        Cookie sessionBruno = signIn(ScenarioIT.CODE_BRUNO);

        LessonReport approved =
                scenario.approvedLesson(bruno, helena, LocalDate.of(2026, 3, 10), BRUNO_CONTENT);
        approvedLesson = approved.id();
        JsonNode generation =
                JSON.readTree(
                        mvc.perform(
                                        post("/api/v1/students/{id}/semester-reports", helena.id())
                                                .cookie(sessionBruno)
                                                .with(csrf().asHeader())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"periodKey\":\"2026-1\"}"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        job = UUID.fromString(generation.path("jobId").asString());
        mvc.perform(
                multipart("/api/v1/students/{id}/lesson-reports", helena.id())
                        .file(audio())
                        .cookie(sessionBruno)
                        .with(csrf().asHeader()));
        scenario.processQueue();
        semester = jdbc.queryForObject("select id from semester_report", UUID.class);
        openLesson =
                jdbc.queryForObject(
                        "select id from lesson_report where status <> 'APPROVED'", UUID.class);
    }

    private Cookie signIn(String code) throws Exception {
        MvcResult input =
                mvc.perform(
                                post("/api/v1/session")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"code\":\"" + code + "\"}"))
                        .andReturn();
        Cookie cookie = input.getResponse().getCookie("session");
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private static MockMultipartFile audio() {
        return new MockMultipartFile(
                "audio", "lesson.webm", "audio/webm", AudioNormalizationTest.AUDIO_WITH_SPEECH);
    }

    private record Call(
            String description, AbstractMockHttpServletRequestBuilder<?> request, int expected) {}

    private static MockHttpServletRequestBuilder json(
            String path, String body, Object... variables) {
        return post(path, variables).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void everyEndpointAboutBrunoDataIsRefusedWithoutRevealingAnything() throws Exception {
        UUID student = helena.id();
        List<Call> calls =
                List.of(
                        new Call("student", get("/api/v1/students/{id}", student), 404),
                        new Call(
                                "em aberto",
                                get("/api/v1/students/{id}/open-report", student),
                                404),
                        new Call(
                                "registrar aula",
                                multipart("/api/v1/students/{id}/lesson-reports", student)
                                        .file(audio()),
                                404),
                        new Call("semesters", get("/api/v1/students/{id}/semesters", student), 404),
                        new Call(
                                "prévia",
                                get("/api/v1/students/{id}/semesters/2026-1/preview", student),
                                404),
                        new Call(
                                "gerar semestral",
                                json(
                                        "/api/v1/students/{id}/semester-reports",
                                        "{\"periodKey\":\"2026-1\"}",
                                        student),
                                404),
                        new Call("histórico", get("/api/v1/students/{id}/history", student), 404),
                        new Call("job", get("/api/v1/jobs/{id}", job), 404),
                        new Call("descartar trabalho", post("/api/v1/jobs/{id}/discard", job), 404),
                        new Call(
                                "relatório de aula",
                                get("/api/v1/lesson-reports/{id}", openLesson),
                                404),
                        new Call(
                                "answers",
                                json(
                                        "/api/v1/lesson-reports/{id}/answers",
                                        "{\"text\":\"Sim.\"}",
                                        openLesson),
                                404),
                        new Call(
                                "alteração de aula",
                                json(
                                        "/api/v1/lesson-reports/{id}/changes",
                                        "{\"text\":\"Mude.\",\"baseVersion\":1}",
                                        approvedLesson),
                                404),
                        new Call(
                                "nova tentativa",
                                post("/api/v1/lesson-reports/{id}/retry", openLesson),
                                404),
                        new Call(
                                "aprovar aula",
                                json(
                                        "/api/v1/lesson-reports/{id}/approval",
                                        "{\"confirmedVersion\":1}",
                                        openLesson),
                                404),
                        new Call(
                                "cancelar aula",
                                post("/api/v1/lesson-reports/{id}/cancellation", openLesson),
                                404),
                        new Call(
                                "PDF de conferência de aula",
                                get("/api/v1/lesson-reports/{id}/versions/1/pdf", approvedLesson),
                                404),
                        new Call(
                                "PDF oficial de aula",
                                get("/api/v1/lesson-reports/{id}/official-pdf", approvedLesson),
                                404),
                        new Call("semestral", get("/api/v1/semester-reports/{id}", semester), 404),
                        new Call(
                                "alteração semestral",
                                json(
                                        "/api/v1/semester-reports/{id}/changes",
                                        "{\"text\":\"Mude.\",\"baseVersion\":1}",
                                        semester),
                                404),
                        new Call(
                                "aprovar semestral",
                                json(
                                        "/api/v1/semester-reports/{id}/approval",
                                        "{\"confirmedVersion\":1}",
                                        semester),
                                404),
                        new Call(
                                "cancelar semestral",
                                post("/api/v1/semester-reports/{id}/cancellation", semester),
                                404),
                        new Call(
                                "PDF de conferência semestral",
                                get("/api/v1/semester-reports/{id}/versions/1/pdf", semester),
                                404),
                        new Call(
                                "PDF oficial semestral",
                                get("/api/v1/semester-reports/{id}/official-pdf", semester),
                                404));

        List<String> failures = new ArrayList<>();
        for (Call call : calls) {
            MvcResult result =
                    mvc.perform(call.request().cookie(sessionClara).with(csrf().asHeader()))
                            .andReturn();
            int status = result.getResponse().getStatus();
            String body = result.getResponse().getContentAsString();
            if (status != call.expected()) {
                failures.add(
                        call.description()
                                + " → "
                                + status
                                + " (expected "
                                + call.expected()
                                + ")");
            }
            if (body.contains("Helena") || body.contains("Bruno") || body.contains(BRUNO_CONTENT)) {
                failures.add(call.description() + " revelou dados: " + body);
            }
        }
        assertThat(failures).isEmpty();

        // nada de Bruno mudou
        assertThat(jdbc.queryForObject("select count(*) from lesson_report", Integer.class))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "select status from lesson_report where id = ?",
                                String.class,
                                openLesson))
                .isNotEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status from semester_report", String.class))
                .isEqualTo("AWAITING_REVIEW");
    }

    @Test
    void botDoesNotFindStudentNorActOnBrunoReports() throws Exception {
        sendText(USER_CLARA, "Helena");
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("Nenhum aluno encontrado para \"Helena\".");

        for (String action :
                List.of(
                        "v1:focus:a" + openLesson,
                        "v1:focus:a" + approvedLesson,
                        "v1:focus:s" + semester,
                        "v1:focus:t" + job,
                        "v1:approve:1",
                        "v1:pdf:",
                        "v1:cancel:",
                        "v1:generate:2026-1")) {
            sendCallback(USER_CLARA, action);
        }
        sendText(USER_CLARA, "histórico");
        sendText(USER_CLARA, "Helena");

        assertThat(String.join("\n", sentTexts()))
                .doesNotContain("Helena Prado")
                .doesNotContain("Bruno")
                .doesNotContain(BRUNO_CONTENT);
        assertThat(API_BOTS.findAll(postRequestedFor(urlPathMatching(".*/sendDocument"))))
                .isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select status from lesson_report where id = ?",
                                String.class,
                                openLesson))
                .isNotEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select status from semester_report", String.class))
                .isEqualTo("AWAITING_REVIEW");
    }
}
