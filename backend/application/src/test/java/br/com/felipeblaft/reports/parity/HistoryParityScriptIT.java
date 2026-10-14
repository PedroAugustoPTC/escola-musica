package br.com.felipeblaft.reports.parity;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Histórico nos dois canais (FR-047, FR-048): a mesma lista, na mesma ordem, com os mesmos rótulos
 * e datas, e o PDF oficial igual byte a byte na Web e no bot.
 */
class HistoryParityScriptIT extends TelegramConversationBase {

    @Autowired private MessageFormatting formatting;

    private Cookie session;
    private Student matteo;

    @BeforeEach
    void load() throws Exception {
        Teacher clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        MvcResult input =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + ScenarioIT.CODE_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = input.getResponse().getCookie("sessao");
        session = new Cookie(cookie.getName(), cookie.getValue());

        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 3, 10), "Escalas");
        clock.advance(Duration.ofMinutes(5));
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 9, 1), "Estudo");
        clock.advance(Duration.ofMinutes(5));
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 5, 20), "Leitura");
        mvc.perform(
                post("/api/v1/alunos/{id}/relatorios-semestrais", matteo.id())
                        .cookie(session)
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodoChave\":\"2026-1\"}"));
        scenario.processQueue();
        String semester = jdbc.queryForObject("select id::text from semester_report", String.class);
        mvc.perform(
                post("/api/v1/relatorios-semestrais/{id}/aprovacao", semester)
                        .cookie(session)
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versaoConfirmada\":1}"));
    }

    private JsonNode historyWeb() throws Exception {
        return JSON.readTree(
                mvc.perform(get("/api/v1/alunos/{id}/historico", matteo.id()).cookie(session))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private byte[] pdfWeb(JsonNode item) throws Exception {
        String resource =
                item.path("tipo").asString().equals("AULA")
                        ? "relatorios-aula"
                        : "relatorios-semestrais";
        return mvc.perform(
                        get(
                                        "/api/v1/" + resource + "/{id}/pdf-oficial",
                                        item.path("relatorioId").asString())
                                .cookie(session))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    private byte[] pdfBot(int index) throws Exception {
        API_BOTS.resetRequests();
        sendCallback(USER_CLARA, "v1:hitem:" + index);
        return API_BOTS.findAll(postRequestedFor(urlPathMatching(".*/sendDocument")))
                .getFirst()
                .getPart("document")
                .getBody()
                .asBytes();
    }

    @Test
    void sameListAndSamePdfsInBothChannels() throws Exception {
        JsonNode web = historyWeb();
        List<String> webRows = new ArrayList<>();
        for (int i = 0; i < web.path("itens").size(); i++) {
            JsonNode item = web.path("itens").get(i);
            webRows.add(
                    (i + 1)
                            + ". "
                            + item.path("rotulo").asString()
                            + " · "
                            + formatting.approvedAt(
                                    Instant.parse(item.path("aprovadoEm").asString())));
        }

        sendText(USER_CLARA, "histórico");
        sendText(USER_CLARA, matteo.name());
        List<String> botRows =
                List.of(lastMessage().get("text").asString().split("\n"))
                        .subList(1, webRows.size() + 1);

        assertThat(webRows).hasSize(4);
        assertThat(botRows).isEqualTo(webRows);
        assertThat(webRows.getFirst()).startsWith("1. Aula de 01/09/2026");
        assertThat(webRows.getLast()).startsWith("4. Semestral (01/01/2026 a 30/06/2026)");
        assertThat(buttonLabels(lastMessage()))
                .startsWith(
                        web.path("itens").get(0).path("rotulo").asString(),
                        web.path("itens").get(1).path("rotulo").asString());

        for (int i = 0; i < webRows.size(); i++) {
            byte[] naWeb = pdfWeb(web.path("itens").get(i));
            assertThat(naWeb).isNotEmpty();
            assertThat(pdfBot(i)).as("PDF do item %d", i + 1).isEqualTo(naWeb);
        }
    }
}
