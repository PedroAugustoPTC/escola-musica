package br.com.felipeblaft.reports.inbound.telegram.channel;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Button;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.channel.ChannelActionMapper.MappedAction;
import br.com.felipeblaft.reports.support.ActionsSchemaV1;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tradução da exclusão de mensagem para a ação v1 {@code DELETE_MESSAGE} (feature 005, FR-036): só
 * formato. A ação leva {@code chatId} e {@code messageId}, nunca texto; a compensação (aviso se o
 * Telegram recusar apagar) é uma mensagem simples no mesmo chat, com o texto que o apresentador
 * entregou.
 */
class ChannelActionMapperDeleteMessageTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String NOTICE_TEXT =
            "Não consegui apagar a mensagem com o CPF. Apague-a você mesmo(a) para não deixá-lo no chat.";

    private final ChannelActionMapper mapper = new ChannelActionMapper(null);

    @Test
    void deletionBecomesActionV1WithOnlyChatAndMessage() {
        MappedAction action = mapper.deleteMessage("u90211-1", 4567L, 89L, null);

        assertThat(action.actionId()).isEqualTo("u90211-1");
        assertThat(action.type()).isEqualTo(ChannelActionType.DELETE_MESSAGE);
        assertThat(action.chatId()).isEqualTo(4567L);
        assertThat(action.jobId()).isNull();
        assertThat(action.replyNode().toString())
                .isEqualTo(
                        "{\"actionId\":\"u90211-1\",\"type\":\"DELETE_MESSAGE\",\"chatId\":4567,"
                                + "\"messageId\":89}");
        ActionsSchemaV1.require("DeleteMessageAction", action.replyNode());
        ActionsSchemaV1.require("Action", action.replyNode());
    }

    @Test
    void storedPayloadHasNoActionIdAndNothingElse() {
        MappedAction action = mapper.deleteMessage("u90211-1", 4567L, 89L, null);

        assertThat(action.payload())
                .isEqualTo("{\"type\":\"DELETE_MESSAGE\",\"chatId\":4567,\"messageId\":89}");
        assertThat(ChannelActionMapper.withActionId("u90211-1", action.payload()).toString())
                .isEqualTo(action.replyNode().toString());
    }

    @Test
    void withoutNoticeThereIsNoCompensation() {
        assertThat(mapper.deleteMessage("u90211-1", 4567L, 89L, null).compensation()).isNull();
    }

    @Test
    void noticeBecomesMessageCompensationInTheSameChatWithoutActionId() {
        TelegramOutput.Message notice =
                new TelegramOutput.Message(4567L, NOTICE_TEXT, Keyboard.NONE);

        MappedAction action = mapper.deleteMessage("u90211-1", 4567L, 89L, notice);

        assertThat(action.compensation())
                .isEqualTo(
                        "{\"type\":\"MESSAGE\",\"chatId\":4567,\"text\":\"" + NOTICE_TEXT + "\"}");
        // a compensação, com id <id>-c, também é uma ação v1 válida
        ActionsSchemaV1.require(
                "MessageAction",
                ChannelActionMapper.withActionId("u90211-1-c", action.compensation()));
        // o aviso nunca carrega o conteúdo apagado
        assertThat(action.payload()).doesNotContain("text");
    }

    @Test
    void noticeKeepsItsKeyboardWhenThereIsOne() {
        TelegramOutput.Message notice =
                new TelegramOutput.Message(
                        4567L, NOTICE_TEXT, Keyboard.of(new Button("Entendi", "v1:menu:")));

        MappedAction action = mapper.deleteMessage("u90211-1", 4567L, 89L, notice);

        assertThat(JSON.readTree(action.compensation()).path("keyboard").path("rows")).hasSize(1);
    }

    @Test
    void mappedDeletionBecomesNewActionForTheOutbox() {
        UUID teacher = UUID.randomUUID();
        MappedAction action = mapper.deleteMessage("u90211-1", 4567L, 89L, null);

        var created = action.toNewAction(teacher);

        assertThat(created.id()).isEqualTo("u90211-1");
        assertThat(created.type()).isEqualTo(ChannelActionType.DELETE_MESSAGE);
        assertThat(created.chatId()).isEqualTo(4567L);
        assertThat(created.teacherId()).isEqualTo(teacher);
        assertThat(created.payload()).contains("\"messageId\":89");
    }
}
