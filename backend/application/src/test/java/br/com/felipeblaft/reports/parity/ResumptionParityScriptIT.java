package br.com.felipeblaft.reports.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Retomada entre canais (SC-009, quickstart V6 e V7): começar num canal e concluir no outro,
 * continuar ou descartar, nos dois sentidos, e a versão desatualizada recusada nos dois canais.
 */
class ResumptionParityScriptIT extends TelegramConversationBase {

    private Cookie session;
    private Student matteo;
    private Student helena;

    @BeforeEach
    void load() throws Exception {
        Teacher clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        helena = scenario.student(clara, "Helena Prado", "Violino");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        MvcResult input =
                mvc.perform(
                                post("/api/v1/session")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"code\":\"" + ScenarioIT.CODE_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = input.getResponse().getCookie("session");
        session = new Cookie(cookie.getName(), cookie.getValue());
    }

    // ---------------------------------------------------------------- ações na Web

    private void webRegister(Student student) throws Exception {
        mvc.perform(
                multipart("/api/v1/students/{id}/lesson-reports", student.id())
                        .file(
                                new MockMultipartFile(
                                        "audio",
                                        "lesson.webm",
                                        "audio/webm",
                                        AudioNormalizationTest.AUDIO_WITH_SPEECH))
                        .cookie(session)
                        .with(csrf().asHeader()));
        scenario.processQueue();
    }

    private JsonNode webOpen(Student student) throws Exception {
        MvcResult result =
                mvc.perform(get("/api/v1/students/{id}/open-report", student.id()).cookie(session))
                        .andReturn();
        return result.getResponse().getStatus() == 204
                ? JSON.nullNode()
                : JSON.readTree(result.getResponse().getContentAsString());
    }

    private String webPost(String path, Object body) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1" + path)
                                        .cookie(session)
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                body == null
                                                        ? "{}"
                                                        : JSON.writeValueAsString(body)))
                        .andReturn();
        if (result.getResponse().getStatus() / 100 == 2) {
            return "OK";
        }
        return JSON.readTree(result.getResponse().getContentAsString()).path("code").asString();
    }

    // ---------------------------------------------------------------- ações no bot

    private void botRegister(Student student) throws Exception {
        sendText(USER_CLARA, "registrar aula");
        sendText(USER_CLARA, student.name());
        prepareTelegramFile("lesson-" + student.id(), AudioNormalizationTest.AUDIO_WITH_SPEECH);
        sendVoice(USER_CLARA, "lesson-" + student.id(), 1000);
        sendCallback(USER_CLARA, "v1:confirm-student:");
        scenario.processQueue();
    }

    private String status(Student student) {
        List<String> status =
                jdbc.queryForList(
                        "select status from lesson_report where student_id = ? order by created_at",
                        String.class,
                        student.id());
        return String.join(",", status);
    }

    private UUID openReport(Student student) {
        return jdbc.queryForObject(
                "select id from lesson_report where student_id = ? and status not in ('CANCELLED', 'APPROVED')",
                UUID.class,
                student.id());
    }

    @Test
    void startOnWebAndFinishInBot() throws Exception {
        webRegister(matteo);
        webPost(
                "/lesson-reports/" + openReport(matteo) + "/answers",
                Map.of("text", "Estudou bem."));
        scenario.processQueue();

        sendText(USER_CLARA, "registrar aula");
        sendText(USER_CLARA, matteo.name());
        assertThat(lastMessage().get("text").asString()).contains("pelo portal");
        sendCallback(USER_CLARA, "v1:continue:");
        sendCallback(USER_CLARA, "v1:approve:1");
        sendCallback(USER_CLARA, "v1:approve-ok:1");

        assertThat(status(matteo)).isEqualTo("APPROVED");
        assertThat(
                        jdbc.queryForObject(
                                "select origin_channel || '>' || approval_channel from lesson_report",
                                String.class))
                .isEqualTo("WEB>TELEGRAM");
    }

    @Test
    void startInBotAndFinishOnWeb() throws Exception {
        botRegister(matteo);
        sendText(USER_CLARA, "Estudou bem.");
        scenario.processQueue();

        JsonNode openReport = webOpen(matteo);
        assertThat(openReport.path("originChannel").asString()).isEqualTo("TELEGRAM");
        assertThat(openReport.path("status").asString()).isEqualTo("AWAITING_REVIEW");
        assertThat(
                        webPost(
                                "/lesson-reports/"
                                        + openReport.path("reportId").asString()
                                        + "/approval",
                                Map.of("confirmedVersion", 1)))
                .isEqualTo("OK");

        assertThat(
                        jdbc.queryForObject(
                                "select origin_channel || '>' || approval_channel from lesson_report",
                                String.class))
                .isEqualTo("TELEGRAM>WEB");
    }

    @Test
    void discardInBothDirections() throws Exception {
        // começou na Web, descartado no bot
        webRegister(matteo);
        sendText(USER_CLARA, "registrar aula");
        sendText(USER_CLARA, matteo.name());
        sendCallback(USER_CLARA, "v1:discard:");
        sendCallback(USER_CLARA, "v1:discard-ok:");
        // começou no bot, descartado na Web
        botRegister(helena);
        String discardWeb =
                webPost("/lesson-reports/" + openReport(helena) + "/cancellation", null);

        assertThat(status(matteo)).isEqualTo("CANCELLED");
        assertThat(discardWeb).isEqualTo("OK");
        assertThat(status(helena)).isEqualTo("CANCELLED");
        assertThat(webOpen(matteo).isNull()).isTrue();
        assertThat(webOpen(helena).isNull()).isTrue();
    }

    @Test
    void staleVersionIsRefusedInBothChannels() throws Exception {
        webRegister(matteo);
        UUID id = openReport(matteo);
        webPost("/lesson-reports/" + id + "/answers", Map.of("text", "Estudou bem."));
        scenario.processQueue();
        // o bot apresenta a versão 1
        sendText(USER_CLARA, "registrar aula");
        sendText(USER_CLARA, matteo.name());
        sendCallback(USER_CLARA, "v1:continue:");
        // a Web altera para a versão 2
        webPost(
                "/lesson-reports/" + id + "/changes",
                Map.of("text", "Diga que foi ótima.", "baseVersion", 1));
        scenario.processQueue();

        // o bot tenta aprovar a versão 1 que apresentou
        API_BOTS.resetRequests();
        sendText(USER_CLARA, "aprovar");
        String noBot = sentTexts().getFirst();
        // a Web tenta aprovar a versão 1 que tinha na tela
        String naWeb =
                webPost("/lesson-reports/" + id + "/approval", Map.of("confirmedVersion", 1));

        assertThat(noBot).startsWith("Esta versão está desatualizada").contains("versão 2");
        assertThat(naWeb).isEqualTo("STALE_VERSION");
        assertThat(status(matteo)).isEqualTo("AWAITING_REVIEW");
        assertThat(buttons(lastMessage())).contains("v1:approve:2");
    }
}
