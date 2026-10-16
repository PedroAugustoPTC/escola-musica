package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Início, busca de aluno (#10), sair (#2), ajuda, tipo não suportado e botão antigo. */
class HomeSearchConversationIT extends TelegramConversationBase {

    @MockitoSpyBean TelegramConversationInterpreter interpreter;

    @BeforeEach
    void load() throws Exception {
        Teacher clara = scenario.clara();
        Teacher bruno = scenario.bruno();
        scenario.student(clara, "Matteo Mafra", "Piano");
        scenario.student(clara, "Olavo Torrente", "Violão");
        scenario.student(clara, "Miguel de Castro Torrente", "Violino");
        scenario.student(bruno, "Helena Prado", "Flauta");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
    }

    @Test
    void unexpectedErrorRepliesInternalErrorAndDoesNotResendOnUpdateRepeat() throws Exception {
        doThrow(new IllegalStateException("falha com o aluno Matteo Mafra"))
                .when(interpreter)
                .interpret(any());

        String text = messageJson(nextUpdate(), USER_CLARA, "\"text\": \"oi\"");
        sendUpdate(text);
        assertThat(sentTexts())
                .containsExactly("Ocorreu um erro inesperado. Tente novamente mais tarde.");
        sendUpdate(text);
        assertThat(sentTexts()).hasSize(1);

        long touchId = nextUpdate();
        String touch =
                """
                {"update_id": %d, "callback_query": {"id": "cb%d", "data": "v1:home:",
                 "from": {"id": %d, "is_bot": false, "first_name": "Teste"},
                 "message": {"message_id": 55, "date": 1, "chat": {"id": %d, "type": "private"}}}}
                """
                        .formatted(touchId, touchId, USER_CLARA, USER_CLARA);
        sendUpdate(touch);
        assertThat(calls("answerCallbackQuery")).hasSize(1);
        assertThat(sentTexts()).hasSize(2);
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("Ocorreu um erro inesperado. Tente novamente mais tarde.");
        sendUpdate(touch);
        assertThat(calls("answerCallbackQuery")).hasSize(1);
        assertThat(sentTexts()).hasSize(2);
    }

    @Test
    @ConversationState({10})
    void homeAndMenuShowHomeBlock() throws Exception {
        for (String command : new String[] {"início", "/inicio", "MENU", "/start"}) {
            sendText(USER_CLARA, command);
            assertThat(lastMessage().get("text").asString())
                    .contains("<b>Olá, Clara</b>", "3 aluno(s)");
        }
    }

    @Test
    @ConversationState({10})
    void searchWithOneSeveralAndNoMatch() throws Exception {
        sendText(USER_CLARA, "matteo");
        assertThat(lastMessage().get("text").asString())
                .startsWith("Aula de hoje, 21/09/2026, com Matteo Mafra.")
                .contains("até 50 MB");

        sendText(USER_CLARA, "torrente");
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("Encontrei mais de um aluno. Qual deles?");
        assertThat(buttonLabels(lastMessage()))
                .containsExactly("Miguel de Castro Torrente", "Olavo Torrente", "Início");
        assertThat(buttons(lastMessage())).startsWith("v1:student:0", "v1:student:1");

        sendCallback(USER_CLARA, "v1:student:1");
        assertThat(lastMessage().get("text").asString()).contains("com Olavo Torrente");

        sendText(USER_CLARA, "Helena");
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("Nenhum aluno encontrado para \"Helena\".");
        assertThat(buttonLabels(lastMessage())).doesNotContain("Helena Prado").hasSize(4);
    }

    @Test
    @ConversationState({10})
    void registerLessonAsksForStudent() throws Exception {
        sendText(USER_CLARA, "registrar aula");
        assertThat(lastMessage().get("text").asString()).isEqualTo("Para qual aluno?");
        sendCallback(USER_CLARA, "v1:register:");
        assertThat(lastMessage().get("text").asString()).isEqualTo("Para qual aluno?");
        sendCallback(USER_CLARA, "v1:student:0");
        assertThat(lastMessage().get("text").asString()).contains("com Matteo Mafra");
    }

    @Test
    @ConversationState({2})
    void signOutAsksConfirmationAndUndoesLink() throws Exception {
        sendText(USER_CLARA, "sair");
        assertThat(lastMessage().get("text").asString()).startsWith("Deseja sair?");
        assertThat(buttons(lastMessage())).contains("v1:sign-out-ok:");

        sendText(USER_CLARA, "não");
        assertThat(jdbc.queryForObject("select count(*) from telegram_link", Integer.class))
                .isEqualTo(1);

        sendText(USER_CLARA, "sair");
        sendText(USER_CLARA, "Sim");
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("Você saiu. Para voltar, envie o seu código de acesso.");
        assertThat(jdbc.queryForObject("select count(*) from telegram_link", Integer.class))
                .isZero();

        sendText(USER_CLARA, "início");
        assertThat(lastMessage().get("text").asString()).startsWith("Olá! Para usar o bot");
    }

    @Test
    void signOutByButton() throws Exception {
        sendText(USER_CLARA, "sair");
        sendCallback(USER_CLARA, "v1:sign-out-ok:");
        assertThat(jdbc.queryForObject("select count(*) from telegram_link", Integer.class))
                .isZero();
    }

    @Test
    void helpUnsupportedTypeAndOldButton() throws Exception {
        sendText(USER_CLARA, "/help");
        assertThat(lastMessage().get("text").asString())
                .startsWith("Posso ajudar a registrar aulas");

        sendUpdate(
                messageJson(
                        nextUpdate(),
                        USER_CLARA,
                        "\"location\": {\"latitude\": 1, \"longitude\": 2}"));
        assertThat(lastMessage().get("text").asString())
                .isEqualTo(
                        "Não consigo usar este tipo de mensagem aqui. Envie o nome de um aluno, um áudio de aula ou escolha uma ação.");

        sendCallback(USER_CLARA, "v1:student:9");
        assertThat(sentTexts()).contains("Esta opção não vale mais. Veja abaixo a etapa atual.");
        sendCallback(USER_CLARA, "v1:sign-out-ok:");
        sendCallback(USER_CLARA, "trash");
        assertThat(jdbc.queryForObject("select count(*) from telegram_link", Integer.class))
                .isEqualTo(1);

        sendText(USER_CLARA, "aprovar");
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("Esta ação não está disponível nesta etapa do relatório.");
    }

    @Test
    void useThroughTelegramRenewsCode() throws Exception {
        clock.advance(java.time.Duration.ofDays(6));
        sendText(USER_CLARA, "início");
        java.time.Instant expires =
                jdbc.queryForObject(
                                "select access_code_expires_at from teacher where email = 'clara@example.test'",
                                java.sql.Timestamp.class)
                        .toInstant();
        assertThat(expires).isEqualTo(clock.now().plus(java.time.Duration.ofDays(7)));
    }
}
