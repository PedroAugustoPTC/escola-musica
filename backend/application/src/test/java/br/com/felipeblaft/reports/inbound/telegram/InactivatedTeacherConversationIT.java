package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.support.ConversationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Professor inativado no meio de uma conversa (US2): a próxima ação é negada, nada é gravado e a
 * mensagem orienta procurar o administrador. A inativação em si e o encerramento do vínculo são do
 * núcleo (fatia S2); aqui entram os dois caminhos que o adaptador enxerga.
 */
@ConversationState({10})
class InactivatedTeacherConversationIT extends RegistryConversationBase {

    private static final String ENDED = "Seu acesso foi encerrado. Procure o administrador.";

    @BeforeEach
    void registrationInProgress() {
        when(start.run(any(), any())).thenReturn(started(Kind.CREATE, null));
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Ana Souza");
    }

    @Test
    void useCaseDenyingWithAccessEndedShowsTheEndedAccessMessageAndRecordsNothing() {
        when(collect.run(any(), any())).thenThrow(error(ErrorCode.ACCESS_ENDED));

        send("05/03/1990");

        assertThat(lastText()).isEqualTo(ENDED);
        assertThat(buttonData(lastMessage())).isEmpty();
        verify(confirm, never()).run(any(), any());
    }

    @Test
    void teacherInactiveIsPresentedAsEndedAccessNeverAsAReactivationInstruction() {
        when(confirm.run(any(), any())).thenThrow(error(ErrorCode.TEACHER_INACTIVE));
        when(collect.run(any(), any())).thenReturn(ready("Ana Souza", null, null));
        send("05/03/1990");

        tap("v1:reg-confirm:" + TOKEN);

        assertThat(lastText()).isEqualTo(ENDED).doesNotContain("Reative");
    }

    @Test
    void tappingAButtonWithAnEndedAccessAnswersTheSameWay() {
        when(confirm.run(any(), any())).thenThrow(error(ErrorCode.ACCESS_ENDED));

        tap("v1:reg-confirm:" + TOKEN);

        assertThat(lastText()).isEqualTo(ENDED);
    }

    @Test
    void inactivatedAccountLosesTheLinkAndTheNextMessageOnlyOrientsTowardsTheAdministrator() {
        jdbc.update("update teacher set active = false");

        send("05/03/1990");

        assertThat(lastText())
                .isEqualTo(
                        "Olá! Para usar o bot, envie o código de acesso que você recebeu do administrador da escola.");
        assertThat(links()).isZero();
        assertThat(everythingSent()).doesNotContain("05/03/1990");
        verify(collect, org.mockito.Mockito.times(1)).run(any(), any());
        verifyNoInteractions(confirm);

        tap("v1:reg-confirm:" + TOKEN);
        assertThat(lastText()).startsWith("Olá! Para usar o bot");
        verifyNoInteractions(confirm);
    }
}
