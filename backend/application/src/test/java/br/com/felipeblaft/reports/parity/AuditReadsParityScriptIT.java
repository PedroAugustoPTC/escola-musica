package br.com.felipeblaft.reports.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Paridade das leituras do professor na trilha (FR-044, SC-002): o mesmo histórico e o mesmo PDF
 * lidos pela Web e pelo Telegram geram eventos iguais, exceto o canal ({@code origin}) e a
 * operação de entrega ({@code REPORT_DOCUMENT_OPEN} na Web, {@code REPORT_DOCUMENT_SENT} no bot).
 */
class AuditReadsParityScriptIT extends TelegramConversationBase {

    private Cookie session;
    private Student matteo;

    @BeforeEach
    void load() throws Exception {
        Teacher clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
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
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 3, 10), "Escalas");
        clearEvents();
    }

    private void clearEvents() {
        jdbc.update("alter table authorship_record disable trigger user");
        jdbc.update("delete from authorship_record");
        jdbc.update("alter table authorship_record enable trigger user");
    }

    /** Eventos de leitura em forma neutra: tudo menos canal, operação de entrega e identificadores. */
    private List<Map<String, Object>> readEvents() {
        return jdbc.queryForList(
                """
                select case operation
                         when 'REPORT_DOCUMENT_OPEN' then 'REPORT_DOCUMENT'
                         when 'REPORT_DOCUMENT_SENT' then 'REPORT_DOCUMENT'
                         else operation end as operation,
                       author_role, target_type, target_id, result, error_code,
                       document_version, page_number
                  from authorship_record
                 where operation in ('HISTORY_QUERY', 'REPORT_DOCUMENT_OPEN', 'REPORT_DOCUMENT_SENT')
                 order by sequence
                """);
    }

    private List<String> origins() {
        return jdbc.queryForList(
                "select origin from authorship_record"
                        + " where operation in ('HISTORY_QUERY', 'REPORT_DOCUMENT_OPEN',"
                        + " 'REPORT_DOCUMENT_SENT') order by sequence",
                String.class);
    }

    private List<String> operations() {
        return jdbc.queryForList(
                "select operation from authorship_record"
                        + " where operation in ('HISTORY_QUERY', 'REPORT_DOCUMENT_OPEN',"
                        + " 'REPORT_DOCUMENT_SENT') order by sequence",
                String.class);
    }

    @Test
    void sameHistoryAndSamePdfProduceEqualEventsExceptChannelAndDelivery() throws Exception {
        String reportId =
                jdbc.queryForObject("select id::text from lesson_report", String.class);

        JsonNode web =
                JSON.readTree(
                        mvc.perform(get("/api/v1/students/{id}/history", matteo.id()).cookie(session))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(web.path("items")).hasSize(1);
        mvc.perform(get("/api/v1/lesson-reports/{id}/official-pdf", reportId).cookie(session));
        List<Map<String, Object>> viaWeb = readEvents();
        List<String> webOrigins = origins();
        List<String> webOperations = operations();
        clearEvents();

        sendText(USER_CLARA, "histórico");
        sendText(USER_CLARA, matteo.name());
        sendCallback(USER_CLARA, "v1:history-item:0");
        List<Map<String, Object>> viaBot = readEvents();

        assertThat(viaWeb).hasSize(2);
        assertThat(viaBot).isEqualTo(viaWeb);
        assertThat(webOperations).containsExactly("HISTORY_QUERY", "REPORT_DOCUMENT_OPEN");
        assertThat(operations()).containsExactly("HISTORY_QUERY", "REPORT_DOCUMENT_SENT");
        assertThat(origins()).doesNotContainAnyElementsOf(webOrigins);
    }
}
