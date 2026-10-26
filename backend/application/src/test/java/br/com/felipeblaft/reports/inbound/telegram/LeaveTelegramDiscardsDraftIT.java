package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.support.ConversationState;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import br.com.felipeblaft.reports.usecase.registry.CancelStudentRegistrationUseCase;

/**
 * "Sair" no meio de um cadastro de aluno encerra o vínculo e descarta o rascunho: o adaptador pede
 * o descarte antes de o vínculo terminar e nada é gravado. (A cascata do banco que remove o CPF
 * cifrado junto com o vínculo é da persistência, fatia S2, e é verificada no Fecho.)
 */
@ConversationState({2, 10})
class LeaveTelegramDiscardsDraftIT extends RegistryConversationBase {

    @BeforeEach
    void registrationAtTheCpfStep() {
        when(start.run(any(), any())).thenReturn(started(Kind.CREATE, null));
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Ana Souza");
        when(collect.run(any(), any())).thenReturn(next(Field.CPF));
        send("05/03/1990");
    }

    @Test
    void leavingDiscardsTheDraftEndsTheLinkAndTheAccountReceivesCodeRequired() {
        UUID link = linkId();

        send("sair");
        assertThat(lastText()).startsWith("Deseja sair?");
        assertThat(links()).isEqualTo(1);
        verify(cancel, never()).run(any(), any());

        send("sim");

        ArgumentCaptor<CancelStudentRegistrationUseCase.Command> command =
                ArgumentCaptor.forClass(CancelStudentRegistrationUseCase.Command.class);
        InOrder order = Mockito.inOrder(cancel);
        order.verify(cancel).run(any(), command.capture());
        assertThat(command.getValue().linkId()).isEqualTo(link);
        assertThat(lastText()).isEqualTo("Você saiu. Para voltar, envie o seu código de acesso.");
        assertThat(links()).isZero();
        verify(confirm, never()).run(any(), any());

        send("início");
        assertThat(lastText()).startsWith("Olá! Para usar o bot, envie o código de acesso");
        send(CPF);
        assertThat(lastText()).startsWith("Olá! Para usar o bot, envie o código de acesso");
        assertThat(everythingSent()).doesNotContain(CPF_DIGITS);
        verify(collect, org.mockito.Mockito.times(2)).run(any(), any());
    }

    @Test
    void leavingByTheButtonDiscardsTheDraftToo() {
        send("sair");

        tap("v1:sign-out-ok:");

        verify(cancel).run(any(), any());
        assertThat(links()).isZero();
    }

    @Test
    void givingUpOnLeavingKeepsTheDraft() {
        send("sair");

        send("não");

        verify(cancel, never()).run(any(), any());
        assertThat(links()).isEqualTo(1);
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CPF");
    }
}
