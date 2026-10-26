package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.support.ConversationState;
import org.junit.jupiter.api.Test;

/**
 * Fora de conversa privada (grupo, supergrupo, canal) nem mensagens nem botões de "Meus alunos"
 * funcionam: a resposta é a negativa neutra e nenhum caso de uso é chamado.
 */
@ConversationState({10})
class StudentRegistryPrivateOnlyIT extends RegistryConversationBase {

    private static final String REFUSAL =
            "Eu só atendo em conversa privada. Fale comigo diretamente.";

    @Test
    void messagesInGroupsAndChannelsAreRefused() {
        for (String type : new String[] {"group", "supergroup", "channel"}) {
            sendIn(USER, type, "cadastrar aluno");
            assertThat(lastText()).isEqualTo(REFUSAL);
            sendIn(USER, type, "meus alunos");
            assertThat(lastText()).isEqualTo(REFUSAL);
            sendIn(USER, type, CPF);
            assertThat(lastText()).isEqualTo(REFUSAL);
            assertThat(everythingSent()).doesNotContain(CPF_DIGITS);
        }
        verifyNoInteractions(start, collect, confirm, cancel, list, get);
        assertThat(persistedFlow()).isEqualTo("NONE");
    }

    @Test
    void buttonsInGroupsAndChannelsAreRefusedToo() {
        for (String type : new String[] {"group", "supergroup", "channel"}) {
            for (String data :
                    new String[] {
                        "v1:reg-new:",
                        "v1:reg-list:",
                        "v1:reg-open:0",
                        "v1:reg-confirm:" + TOKEN,
                        "v1:reg-inactivate:",
                        "v1:reg-cancel:"
                    }) {
                tap(USER, type, data);
                assertThat(lastText()).isEqualTo(REFUSAL);
                assertThat(buttonData(lastMessage())).isEmpty();
            }
        }
        verifyNoInteractions(start, collect, confirm, cancel, list, get);
        assertThat(persistedFlow()).isEqualTo("NONE");
    }

    @Test
    void theSameButtonsWorkInThePrivateChat() {
        when(start.run(any(), any())).thenReturn(started(Kind.CREATE, null));

        tap(USER, "private", "v1:reg-new:");

        assertThat(lastText()).isEqualTo("Qual é o nome do aluno?");
    }
}
