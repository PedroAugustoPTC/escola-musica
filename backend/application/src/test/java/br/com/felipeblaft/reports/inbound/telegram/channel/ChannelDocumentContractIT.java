package br.com.felipeblaft.reports.inbound.telegram.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ChannelOpenApiContract;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import br.com.felipeblaft.reports.usecase.channel.EmitChannelDocumentUseCase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

/**
 * V14, C12, SC-007: o PDF sai por identificador opaco, idêntico ao do portal; qualquer
 * identificador inexistente, vencido, malformado ou em forma de URL/caminho resulta em {@code 404}
 * sem corpo e sem diferença observável; o backend nunca faz chamada de rede a partir do
 * identificador.
 */
class ChannelDocumentContractIT extends TelegramConversationBase {

    private static final String BASE = "/internal/v1/channel/telegram/documents/";

    @Autowired EmitChannelDocumentUseCase emit;

    private Teacher clara;
    private Student matteo;

    private MvcResult search(String id) throws Exception {
        return mvc.perform(get(BASE + id).header("X-N8N-Service-Token", TOKEN_SERVICE)).andReturn();
    }

    private Cookie signIn() throws Exception {
        MvcResult input =
                mvc.perform(
                                post("/api/v1/session")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"code\":\"" + ScenarioIT.CODE_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = input.getResponse().getCookie("session");
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private byte[] doPortal(String path) throws Exception {
        MvcResult result = mvc.perform(get(path).cookie(signIn())).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getContentAsByteArray();
    }

    private LessonReport official() {
        clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        return scenario.approvedLesson(
                clara, matteo, LocalDate.of(2026, 3, 10), "Escalas e arpejos");
    }

    private UUID review() throws Exception {
        clara = scenario.clara();
        scenario.student(clara, "Matteo Ferreira", "Piano");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        prepareTelegramFile("lesson", AudioNormalizationTest.AUDIO_WITH_SPEECH);
        sendText(USER_CLARA, "Matteo");
        sendVoice(USER_CLARA, "lesson", 1000);
        sendCallback(USER_CLARA, "v1:confirm-student:");
        scenario.processQueue();
        sendText(USER_CLARA, "Estudou bem; vai preparar a valsa.");
        scenario.processQueue();
        API_BOTS.resetRequests();
        return jdbc.queryForObject("select id from lesson_report", UUID.class);
    }

    @Test
    void officialPdfGoesOutIdenticalToPortalOneWithNoCacheHeaders() throws Exception {
        LessonReport report = official();
        String id = emit.run(clara.id(), ReportType.LESSON, report.id(), null, "official.pdf");

        MvcResult result = search(id);

        MockHttpServletResponse response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getHeader("Content-Disposition"))
                .startsWith("inline")
                .contains("filename=");
        assertThat(response.getContentAsByteArray())
                .isEqualTo(doPortal("/api/v1/lesson-reports/" + report.id() + "/official-pdf"));
        assertThat(response.getContentAsByteArray()).startsWith("%PDF".getBytes());
        ChannelOpenApiContract.requireReply(result);
    }

    @Test
    void reviewPdfGoesOutIdenticalToPortalOne() throws Exception {
        UUID report = review();
        String id = emit.run(clara.id(), ReportType.LESSON, report, 1, "review.pdf");

        byte[] channel = search(id).getResponse().getContentAsByteArray();

        assertThat(channel)
                .isEqualTo(doPortal("/api/v1/lesson-reports/" + report + "/versions/1/pdf"));
    }

    @Test
    void repetitionWithinDeadlineStaysValid() throws Exception {
        LessonReport report = official();
        String id = emit.run(clara.id(), ReportType.LESSON, report.id(), null, "official.pdf");

        assertThat(search(id).getResponse().getStatus()).isEqualTo(200);
        clock.advance(Duration.ofHours(24));
        assertThat(search(id).getResponse().getStatus()).isEqualTo(200);
        assertThat(search(id).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void invalidIdentifiersBecome404WithoutBodyAndWithoutObservableDifference() throws Exception {
        LessonReport report = official();
        String expired = emit.run(clara.id(), ReportType.LESSON, report.id(), null, "official.pdf");
        clock.advance(Duration.ofHours(26));
        String withoutAssignment =
                emit.run(clara.id(), ReportType.LESSON, report.id(), null, "official.pdf");
        jdbc.update("delete from teacher_student_assignment");

        List<String> ids =
                List.of(
                        "A".repeat(43), // bem formado, inexistente
                        expired, // vencido
                        withoutAssignment, // dono sem associação ao aluno: checagem repetida na
                        // leitura
                        "short",
                        "A".repeat(44),
                        "A".repeat(42) + "+",
                        "http:localhost:" + API_BOTS.port() + ":x",
                        "file:etc:passwd");
        List<String> signatures = new ArrayList<>();
        for (String id : ids) {
            MvcResult result = search(id);
            MockHttpServletResponse response = result.getResponse();
            assertThat(response.getStatus()).as(id).isEqualTo(404);
            assertThat(response.getContentAsByteArray()).as(id).isEmpty();
            ChannelOpenApiContract.requireReply(result);
            List<String> headers = new ArrayList<>();
            for (String name : response.getHeaderNames()) {
                headers.add(name + "=" + response.getHeaders(name));
            }
            headers.sort(String::compareTo);
            signatures.add(response.getStatus() + "|" + headers);
        }
        assertThat(signatures).as("respostas indistinguíveis").containsOnly(signatures.get(0));
        // barras codificadas (URL, caminho) já são barradas pelo firewall antes do controlador: 4xx
        // e
        // nunca uma resposta 2xx nem 5xx
        for (String id :
                List.of(
                        "http%3A%2F%2Flocalhost%3A" + API_BOTS.port() + "%2Fx",
                        "..%2F..%2Fetc%2Fpasswd",
                        "a%20b",
                        "file%3A%2F%2F%2Fetc%2Fpasswd")) {
            int status;
            try {
                status = search(id).getResponse().getStatus();
            } catch (RuntimeException
                    | java.io.IOException
                    | jakarta.servlet.ServletException rejected) {
                status = 400;
            }
            assertThat(status).as(id).isBetween(400, 404);
        }
        // nenhuma chamada de rede nasceu do identificador
        assertThat(API_BOTS.getAllServeEvents()).isEmpty();
    }

    @Test
    void withoutServiceTokenIs401() throws Exception {
        LessonReport report = official();
        String id = emit.run(clara.id(), ReportType.LESSON, report.id(), null, "official.pdf");
        MvcResult result = mvc.perform(get(BASE + id)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
    }

    @Test
    void onlyHashStaysInDatabaseAndNeverIdentifier() throws Exception {
        LessonReport report = official();
        String id = emit.run(clara.id(), ReportType.LESSON, report.id(), null, "official.pdf");
        List<String> hashes =
                jdbc.queryForList("select token_hash from channel_document", String.class);
        assertThat(hashes).hasSize(1);
        assertThat(hashes.get(0)).hasSize(64).isNotEqualTo(id);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from channel_document where token_hash = ?",
                                Integer.class,
                                id))
                .isZero();
    }
}
