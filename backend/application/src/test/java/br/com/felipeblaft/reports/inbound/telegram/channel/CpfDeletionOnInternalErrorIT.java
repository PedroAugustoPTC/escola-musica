package br.com.felipeblaft.reports.inbound.telegram.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.inbound.telegram.TelegramConversationInterpreter;
import br.com.felipeblaft.reports.support.ActionsSchemaV1;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

/**
 * FR-036 diante de falha inesperada: a mensagem com o CPF sai do chat mesmo quando o interpretador
 * (ou o mapeamento das ações) lança exceção. A exclusão vem antes do aviso {@code INTERNAL_ERROR},
 * com a compensação gravada, e nada do CPF aparece nas ações.
 */
class CpfDeletionOnInternalErrorIT extends TelegramConversationBase {

    private static final String BASE = "/internal/v1/channel/telegram";
    private static final String INTERNAL_ERROR_TEXT =
            "Algo deu errado\\. Informe o código de referência [A-Z0-9]{8} ao suporte\\.";
    private static final long MESSAGE_ID = 987L;

    @MockitoSpyBean TelegramConversationInterpreter interpreter;

    @MockitoSpyBean ChannelActionMapper mapper;

    private JsonNode update(String json) throws Exception {
        var result =
                mvc.perform(
                                post(BASE + "/update")
                                        .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                        .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        ActionsSchemaV1.require("UpdateResponse", result.getResponse().getContentAsString());
        return JSON.readTree(result.getResponse().getContentAsString()).get("actions");
    }

    private String text(long updateId, String text) throws Exception {
        return """
                {"update_id": %d, "message": {"message_id": %d, "date": 1,
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "chat": {"id": %d, "type": "private"}, "text": %s}}
                """
                .formatted(
                        updateId,
                        MESSAGE_ID,
                        USER_CLARA,
                        USER_CLARA,
                        JSON.writeValueAsString(text));
    }

    private void failInterpretation() {
        doThrow(new IllegalStateException("falha com o aluno Matteo Ferreira"))
                .when(interpreter)
                .interpret(any());
    }

    private void assertDeletionThenInternalError(JsonNode actions, long updateId, String suffix) {
        assertThat(actions).hasSize(2);
        JsonNode deletion = actions.get(0);
        assertThat(deletion.path("type").asString()).isEqualTo("DELETE_MESSAGE");
        assertThat(deletion.path("chatId").asLong()).isEqualTo(USER_CLARA);
        assertThat(deletion.path("messageId").asLong()).isEqualTo(MESSAGE_ID);
        assertThat(deletion.path("actionId").asString()).isEqualTo("u" + updateId + suffix + "0");
        assertThat(actions.get(1).path("type").asString()).isEqualTo("MESSAGE");
        assertThat(actions.get(1).path("text").asString()).matches(INTERNAL_ERROR_TEXT);
        assertThat(actions.get(1).path("actionId").asString())
                .isEqualTo("u" + updateId + suffix + "1");
        assertThat(actions.toString()).doesNotContain("529").doesNotContain("Matteo");
    }

    @Test
    void cpfShapedTextThatBreaksTheInterpreterIsDeletedBeforeTheInternalError() throws Exception {
        scenario.clara();
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        failInterpretation();
        long id = nextUpdate();
        String body = text(id, "529 982 247 25");

        JsonNode actions = update(body);

        assertDeletionThenInternalError(actions, id, "-");
        // a compensação (aviso se o Telegram não apagar) foi gravada com a ação
        assertThat(
                        jdbc.queryForObject(
                                "select compensation is not null from channel_action where id = ?",
                                Boolean.class,
                                "u" + id + "-0"))
                .isTrue();
        // a repetição devolve exatamente a mesma resposta
        assertThat(update(body)).isEqualTo(actions);
    }

    @Test
    void anyTextWhileTheConversationAwaitsTheCpfIsDeletedBeforeTheInternalError() throws Exception {
        scenario.clara();
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        jdbc.update(
                "update telegram_link set flow = 'STUDENT_REGISTRY',"
                        + " flow_step = 'REGISTRY_ASK_CPF'");
        failInterpretation();
        long id = nextUpdate();

        JsonNode actions = update(text(id, "meu cpf é cinco dois nove"));

        assertDeletionThenInternalError(actions, id, "-");
    }

    @Test
    void ordinaryTextThatBreaksTheInterpreterGetsOnlyTheInternalError() throws Exception {
        scenario.clara();
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        failInterpretation();

        JsonNode actions = update(text(nextUpdate(), "oi"));

        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).path("type").asString()).isEqualTo("MESSAGE");
    }

    @Test
    void mappingFailureAlsoDeletesTheCpfMessageBeforeTheInternalError() throws Exception {
        doThrow(new IllegalStateException("falha com o aluno Matteo Ferreira"))
                .doCallRealMethod()
                .when(mapper)
                .map(any(), anyLong(), any());
        long id = nextUpdate();
        String body = text(id, "529.982.247-25");

        JsonNode actions = update(body);

        assertDeletionThenInternalError(actions, id, "-e");
        assertThat(update(body)).isEqualTo(actions);
    }
}
