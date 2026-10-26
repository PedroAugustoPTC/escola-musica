package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.CallbackReply;
import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.support.ScenarioIT;
import org.junit.jupiter.api.Test;

/**
 * O bot só atende em conversa privada, e isso vale para os <strong>botões</strong> (verificação 6
 * do plano), não só para as mensagens: um toque num botão vindo de grupo, supergrupo ou canal é
 * recusado com a negativa neutra, sem executar nada nem mexer no estado.
 */
@ConversationState({1, 10})
class PrivateChatOnlyIT extends RegistryConversationBase {

    private static final String REFUSAL =
            "Eu só atendo em conversa privada. Fale comigo diretamente.";

    @Test
    void buttonsOutsideAPrivateChatAreRefusedForLinkedAndUnlinkedAccounts() {
        scenario.student(clara, "Matteo Mafra", "Piano");
        String before = persistedData();

        for (String type : new String[] {"group", "supergroup", "channel"}) {
            for (String data :
                    new String[] {
                        "v1:register:", "v1:home:", "v1:sign-out-ok:", "v1:approve:1", "v1:student:0"
                    }) {
                tap(USER, type, data);

                assertThat(messages()).hasSize(1);
                assertThat(lastText()).isEqualTo(REFUSAL);
                assertThat(out.getFirst()).isInstanceOf(CallbackReply.class);
            }
            tap(OTHER_USER, type, "v1:register:");
            assertThat(lastText()).isEqualTo(REFUSAL);
        }

        assertThat(links()).isEqualTo(1);
        assertThat(persistedFlow()).isEqualTo("NONE");
        assertThat(persistedData()).isEqualTo(before);
    }

    @Test
    void messagesOutsideAPrivateChatAreRefusedAndTheLinkCodeIsNotConsumed() {
        scenario.bruno();
        int attempts = jdbc.queryForObject("select count(*) from access_attempt", Integer.class);

        sendIn(OTHER_USER, "group", ScenarioIT.CODE_BRUNO);

        assertThat(lastText()).isEqualTo(REFUSAL);
        assertThat(links()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from access_attempt", Integer.class))
                .isEqualTo(attempts);
    }

    @Test
    void theSameButtonWorksInThePrivateChat() {
        scenario.student(clara, "Matteo Mafra", "Piano");

        tap(USER, "private", "v1:register:");

        assertThat(lastText()).isEqualTo("Para qual aluno?");
    }
}
