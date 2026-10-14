package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Sequência 5.1 e estado #1 de contracts/telegram-conversa.md. */
class LinkingConversationIT extends TelegramConversationBase {

    private Teacher clara;

    @BeforeEach
    void load() {
        clara = scenario.clara();
        scenario.student(clara, "Matteo Mafra", "Piano");
        scenario.student(clara, "Olavo Torrente", "Violão");
    }

    @Test
    void unlinkedReceivesCodeRequest() throws Exception {
        sendText(USER_CLARA, "olá, bom dia!");
        sendText(USER_CLARA, "/start");
        assertThat(sentTexts())
                .containsOnly(
                        "Olá! Para usar o bot, envie o código de acesso que você recebeu da secretaria da escola.");
        assertThat(jdbc.queryForObject("select count(*) from access_attempt", Integer.class))
                .isZero();
    }

    @Test
    void validCodeLinksAndShowsHome() throws Exception {
        sendText(USER_CLARA, "k7qm-2xpa");

        String text = lastMessage().get("text").asString();
        assertThat(text)
                .startsWith("Conta vinculada com sucesso, Clara!")
                .contains("<b>Olá, Clara</b>")
                .contains("Nenhum relatório em aberto.")
                .contains("2 aluno(s) associado(s)")
                .contains("• Matteo Mafra · Piano");
        assertThat(buttons(lastMessage())).containsExactly("v1:reg:", "v1:sem:", "v1:hist:");
        assertThat(lastMessage().get("parse_mode").asString()).isEqualTo("HTML");
        assertThat(lastMessage().get("chat_id").asLong()).isEqualTo(USER_CLARA);
        assertThat(
                        jdbc.queryForObject(
                                "select teacher_id from telegram_link", java.util.UUID.class))
                .isEqualTo(clara.id());
    }

    @Test
    void wrongExpiredCodeAndLock() throws Exception {
        sendText(USER_CLARA, "ZZZZZZZZ");
        assertThat(lastMessage().get("text").asString()).startsWith("Código inválido.");
        for (int i = 0; i < 4; i++) {
            sendText(USER_CLARA, "ZZZZ-ZZZZ");
        }
        sendText(USER_CLARA, ScenarioIT.CODE_CLARA);
        assertThat(lastMessage().get("text").asString())
                .isEqualTo(
                        "Acesso bloqueado temporariamente por muitas tentativas incorretas. Tente novamente em 15 minuto(s).");
        clock.advance(Duration.ofDays(7));
        sendText(USER_CLARA, ScenarioIT.CODE_CLARA);
        assertThat(lastMessage().get("text").asString())
                .startsWith("Este código venceu após 7 dias sem uso.");
    }

    @Test
    void linkConflicts() throws Exception {
        scenario.bruno();
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        sendText(USER_BRUNO, ScenarioIT.CODE_CLARA);
        assertThat(lastMessage().get("text").asString())
                .isEqualTo(
                        "Seu cadastro já está vinculado a outra conta do Telegram. Procure a secretaria da escola.");
    }

    @Test
    void wrongSecretAndRepeatedUpdate() throws Exception {
        mvc.perform(
                        post("/telegram/webhook")
                                .header("X-Telegram-Bot-Api-Secret-Token", "errado")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/telegram/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        long update = nextUpdate();
        String json = messageJson(update, USER_CLARA, "\"text\": \"oi\"");
        sendUpdate(json);
        sendUpdate(json);
        assertThat(sentTexts()).hasSize(1);
        sendUpdate("{ nao é json");
        sendUpdate("{\"update_id\": " + nextUpdate() + ", \"edited_message\": {}}");
        assertThat(sentTexts()).hasSize(1);
    }

    @Test
    void groupRefusedAndOtherTypesAskForCode() throws Exception {
        sendUpdate(messageJson(nextUpdate(), USER_CLARA, "group", "\"text\": \"K7QM2XPA\""));
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("Eu só atendo em conversa privada. Fale comigo diretamente.");
        sendUpdate(messageJson(nextUpdate(), USER_CLARA, "\"sticker\": {\"file_id\": \"x\"}"));
        assertThat(lastMessage().get("text").asString()).startsWith("Olá! Para usar o bot");
        sendCallback(USER_CLARA, "v1:reg:");
        assertThat(calls("answerCallbackQuery")).hasSize(1);
        assertThat(lastMessage().get("text").asString()).startsWith("Olá! Para usar o bot");
        assertThat(jdbc.queryForObject("select count(*) from telegram_link", Integer.class))
                .isZero();
    }
}
