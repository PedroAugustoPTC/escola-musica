package br.com.felipeblaft.reports.inbound.telegram.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.channel.ChannelActionMapper.MappedAction;
import br.com.felipeblaft.reports.support.ActionsSchemaV1;
import br.com.felipeblaft.reports.support.OrchestratorDeliveryBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Ação v1 {@code DELETE_MESSAGE} (feature 005, FR-036) contra o contrato normativo da 002 ({@code
 * actions-v1.schema.json}): o formato é fechado, o código de falha {@code MESSAGE_NOT_DELETABLE}
 * está documentado, o backend gera a compensação só para ele e um receptor antigo, que não conhece
 * o tipo, responde {@code ACTION_NOT_SUPPORTED} sem que o backend compense nem se perca.
 */
class DeleteMessageActionContractIT extends OrchestratorDeliveryBase {

    private static final long CHAT = USER_CLARA;
    private static final long MESSAGE = 89L;

    @Autowired SaveChannelActionsUseCase save;
    @Autowired ChannelActionMapper mapper;
    @Autowired MessageCatalog catalog;

    /** A ação como o interpretador a mandaria, com o aviso do catálogo como alternativa. */
    private MappedAction mapped(String actionId) {
        return mapper.deleteMessage(
                actionId,
                CHAT,
                MESSAGE,
                new TelegramOutput.Message(
                        CHAT, catalog.text("CPF_MESSAGE_NOT_DELETED"), Keyboard.NONE));
    }

    private String publishDeletion(String batch) {
        MappedAction action = mapped(batch + ":0");
        save.run(
                batch,
                ChannelActionOrigin.PROACTIVE,
                null,
                List.of(action.toNewAction(clara.id())));
        return action.actionId();
    }

    private MvcResult confirm(String resultsJson) throws Exception {
        return mvc.perform(
                        post("/internal/v1/channel/telegram/confirmations")
                                .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":\"v1\",\"results\":[" + resultsJson + "]}"))
                .andReturn();
    }

    private String reason(String actionId) {
        return jdbc.queryForObject(
                "select reason_code from channel_action where id = ?", String.class, actionId);
    }

    // ---- formato

    @Test
    void mappedActionFollowsTheNormativeSchemaAndTheSchemaIsClosed() {
        JsonNode node = mapped("u9-0").replyNode();

        assertThat(ActionsSchemaV1.validate("DeleteMessageAction", node.toString())).isEmpty();
        assertThat(ActionsSchemaV1.validate("Action", node.toString())).isEmpty();
        assertThat(node.path("type").asString()).isEqualTo("DELETE_MESSAGE");
        assertThat(node.has("text")).isFalse();

        String extra = node.toString().replace("}", ",\"text\":\"x\"}");
        assertThat(ActionsSchemaV1.validate("DeleteMessageAction", extra)).isNotEmpty();
        String withoutMessage = node.toString().replace(",\"messageId\":89", "");
        assertThat(ActionsSchemaV1.validate("DeleteMessageAction", withoutMessage)).isNotEmpty();
        String zeroMessage = node.toString().replace("\"messageId\":89", "\"messageId\":0");
        assertThat(ActionsSchemaV1.validate("DeleteMessageAction", zeroMessage)).isNotEmpty();
        String otherType = node.toString().replace("DELETE_MESSAGE", "DELETE_CHAT");
        assertThat(ActionsSchemaV1.validate("Action", otherType)).isNotEmpty();
    }

    @Test
    void failureCodeMessageNotDeletableIsDocumentedAndAcceptedByTheConfirmationSchema()
            throws Exception {
        String contract = Files.readString(ActionsSchemaV1.contract("actions-v1.schema.json"));

        assertThat(JSON.readTree(contract).at("/$defs/ReasonCode/description").asString())
                .contains("MESSAGE_NOT_DELETABLE");
        ActionsSchemaV1.require(
                "ConfirmationRequest",
                "{\"version\":\"v1\",\"results\":[{\"actionId\":\"u9-0\",\"outcome\":\"FAILED\","
                        + "\"reasonCode\":\"MESSAGE_NOT_DELETABLE\"}]}");
        ActionsSchemaV1.require(
                "ConfirmationRequest",
                "{\"version\":\"v1\",\"results\":[{\"actionId\":\"u9-0\",\"outcome\":\"EXECUTED\"}]}");
    }

    @Test
    void proactiveDeliveryCarriesTheDeletionInsideAValidBatch() throws Exception {
        link(CHAT, ScenarioIT.CODE_CLARA);
        String id = publishDeletion("p:cpf:1");

        task.dispatch();

        assertThat(deliveries()).hasSize(1);
        JsonNode batch = body(deliveries().getFirst());
        JsonNode action = batch.get("actions").get(0);
        assertThat(action.path("actionId").asString()).isEqualTo(id);
        assertThat(action.path("type").asString()).isEqualTo("DELETE_MESSAGE");
        assertThat(action.path("chatId").asLong()).isEqualTo(CHAT);
        assertThat(action.path("messageId").asLong()).isEqualTo(MESSAGE);
        assertThat(action.size()).isEqualTo(4);
    }

    // ---- compensação

    @Test
    void refusedDeletionGeneratesTheNoticeOnceAndDeliversItInTheSameChat() throws Exception {
        link(CHAT, ScenarioIT.CODE_CLARA);
        String id = publishDeletion("p:cpf:2");

        MvcResult first =
                confirm(
                        "{\"actionId\":\""
                                + id
                                + "\",\"outcome\":\"FAILED\",\"reasonCode\":\"MESSAGE_NOT_DELETABLE\"}");
        confirm(
                "{\"actionId\":\""
                        + id
                        + "\",\"outcome\":\"FAILED\",\"reasonCode\":\"MESSAGE_NOT_DELETABLE\"}");

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        ActionsSchemaV1.require("ConfirmationResponse", first.getResponse().getContentAsString());
        assertThat(state(id)).isEqualTo("FAILED");
        assertThat(reason(id)).isEqualTo("MESSAGE_NOT_DELETABLE");
        assertThat(state(id + "-c")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isEqualTo(2);

        drain(6);

        assertThat(state(id + "-c")).isEqualTo("EXECUTED");
        assertThat(sentTexts()).containsExactly(catalog.text("CPF_MESSAGE_NOT_DELETED"));
        assertThat(calls("deleteMessage")).isEmpty();
        JsonNode compensation = body(deliveries().getLast()).get("actions").get(0);
        assertThat(body(deliveries().getLast()).path("origin").asString())
                .isEqualTo("COMPENSATION");
        assertThat(compensation.path("actionId").asString()).isEqualTo(id + "-c");
        assertThat(compensation.path("chatId").asLong()).isEqualTo(CHAT);
        assertThat(compensation.path("type").asString()).isEqualTo("MESSAGE");
    }

    @Test
    void oldReceiverAnswersActionNotSupportedAndTheBackendKeepsTheStateWithoutCompensating()
            throws Exception {
        link(CHAT, ScenarioIT.CODE_CLARA);
        String id = publishDeletion("p:cpf:3");

        MvcResult result =
                confirm(
                        "{\"actionId\":\""
                                + id
                                + "\",\"outcome\":\"FAILED\",\"reasonCode\":\"ACTION_NOT_SUPPORTED\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode response = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("accepted").asInt()).isEqualTo(1);
        assertThat(response.path("ignored")).isEmpty();
        assertThat(state(id)).isEqualTo("FAILED");
        assertThat(reason(id)).isEqualTo("ACTION_NOT_SUPPORTED");
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isEqualTo(1);
        assertThat(sentTexts()).isEmpty();
    }

    @Test
    void unknownCodeFromANewerReceiverBecomesTelegramErrorWithoutCompensating() throws Exception {
        link(CHAT, ScenarioIT.CODE_CLARA);
        String id = publishDeletion("p:cpf:4");

        confirm(
                "{\"actionId\":\""
                        + id
                        + "\",\"outcome\":\"FAILED\",\"reasonCode\":\"MESSAGE_PINNED\"}");

        assertThat(reason(id)).isEqualTo("TELEGRAM_ERROR");
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void executedDeletionIsFinalAndDoesNotCompensate() throws Exception {
        link(CHAT, ScenarioIT.CODE_CLARA);
        String id = publishDeletion("p:cpf:5");

        confirm("{\"actionId\":\"" + id + "\",\"outcome\":\"EXECUTED\"}");

        assertThat(state(id)).isEqualTo("EXECUTED");
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isEqualTo(1);
    }
}
