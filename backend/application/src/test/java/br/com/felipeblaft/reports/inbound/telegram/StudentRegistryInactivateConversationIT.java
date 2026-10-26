package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.usecase.registry.ConfirmStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.registry.StartStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Inativar e reativar (contracts/telegram-student-registry.md §3.4): resumo que nomeia a ação e o
 * aluno, Confirmar/Cancelar com token de uso único. Escopo, token e prazo são do núcleo (dublê).
 */
@ConversationState({10})
class StudentRegistryInactivateConversationIT extends RegistryConversationBase {

    private final StudentView bia = view("Bia Lima", 12, "Violão", true, "Marta Lima");
    private final StudentView caio = view("Caio Reis", 9, null, false, null);

    private void open(StudentView student) {
        when(list.run(any(), any())).thenReturn(page(0, 1, student));
        when(get.run(any(), any())).thenReturn(student);
        send("meus alunos");
        tap("v1:reg-open:0");
    }

    @BeforeEach
    void stubStart() {
        when(start.run(any(), any())).thenReturn(started(Kind.INACTIVATE, TOKEN));
    }

    @Test
    void inactivateShowsASummaryNamingTheStudentAndConfirmsWithTheToken() {
        open(bia);

        tap("v1:reg-inactivate:");

        ArgumentCaptor<StartStudentRegistrationUseCase.Command> command =
                ArgumentCaptor.forClass(StartStudentRegistrationUseCase.Command.class);
        verify(start).run(any(), command.capture());
        assertThat(command.getValue().kind()).isEqualTo(Kind.INACTIVATE);
        assertThat(command.getValue().studentId()).isEqualTo(bia.id());
        assertThat(lastText())
                .isEqualTo(
                        "Inativar Bia Lima? O aluno deixa de aparecer para novas aulas; nada é apagado.");
        assertThat(buttonLabels(lastMessage())).containsExactly("Confirmar", "Cancelar");
        assertThat(buttonData(lastMessage()))
                .containsExactly("v1:reg-confirm:" + TOKEN, "v1:reg-cancel:");
        assertThat(persistedStep()).isEqualTo("REGISTRY_CONFIRM");

        when(confirm.run(any(), any()))
                .thenReturn(view("Bia Lima", 12, "Violão", false, "Marta Lima"));
        tap("v1:reg-confirm:" + TOKEN);

        ArgumentCaptor<ConfirmStudentRegistrationUseCase.Command> confirmation =
                ArgumentCaptor.forClass(ConfirmStudentRegistrationUseCase.Command.class);
        verify(confirm).run(any(), confirmation.capture());
        assertThat(confirmation.getValue().confirmationToken()).isEqualTo(TOKEN);
        assertThat(lastText()).isEqualTo("Bia Lima foi inativado(a).");
        assertThat(persistedFlow()).isEqualTo("NONE");
    }

    @Test
    void reactivateFollowsTheSameMechanism() {
        when(start.run(any(), any())).thenReturn(started(Kind.REACTIVATE, "ZX98WV76"));
        open(caio);

        tap("v1:reg-reactivate:");

        assertThat(lastText()).isEqualTo("Reativar Caio Reis?");
        assertThat(buttonData(lastMessage())).contains("v1:reg-confirm:ZX98WV76");

        when(confirm.run(any(), any())).thenReturn(view("Caio Reis", 9, null, true, null));
        tap("v1:reg-confirm:ZX98WV76");
        assertThat(lastText()).isEqualTo("Caio Reis foi reativado(a).");
    }

    @Test
    void sharedStudentIsDeniedWithTheOrientation() {
        open(bia);
        when(start.run(any(), any())).thenThrow(error(ErrorCode.SHARED_STUDENT_ACTION_DENIED));

        tap("v1:reg-inactivate:");

        assertThat(lastText())
                .isEqualTo(
                        "Este aluno tem mais de um professor. Procure o administrador para inativá-lo ou reativá-lo.");
        verify(confirm, never()).run(any(), any());
    }

    @Test
    void sharedStudentDeniedOnlyAtConfirmationRecordsNothingAndEndsTheFlow() {
        open(bia);
        tap("v1:reg-inactivate:");
        when(confirm.run(any(), any())).thenThrow(error(ErrorCode.SHARED_STUDENT_ACTION_DENIED));

        tap("v1:reg-confirm:" + TOKEN);

        assertThat(lastText()).contains("Procure o administrador");
        assertThat(persistedFlow()).isEqualTo("NONE");
    }

    @Test
    void secondTapAnswersAlreadyDoneWithoutRecordingAgain() {
        open(bia);
        tap("v1:reg-inactivate:");
        when(confirm.run(any(), any()))
                .thenReturn(view("Bia Lima", 12, "Violão", false, "Marta Lima"));
        tap("v1:reg-confirm:" + TOKEN);

        when(confirm.run(any(), any())).thenThrow(error(ErrorCode.CONFIRMATION_ALREADY_DONE));
        tap("v1:reg-confirm:" + TOKEN);

        assertThat(lastText()).isEqualTo("Essa operação já foi concluída.");
        verify(confirm, times(2)).run(any(), any());
    }

    @Test
    void expiredTokenAnswersConfirmationExpiredAndEndsTheFlow() {
        open(bia);
        tap("v1:reg-inactivate:");
        when(confirm.run(any(), any())).thenThrow(error(ErrorCode.CONFIRMATION_EXPIRED));

        tap("v1:reg-confirm:" + TOKEN);

        assertThat(lastText()).isEqualTo("Esta confirmação venceu. Comece de novo.");
        assertThat(persistedFlow()).isEqualTo("NONE");
    }

    @Test
    void buttonOfAnotherLinkIsRefusedByTheCoreAndRecordsNothing() {
        open(bia);
        tap("v1:reg-inactivate:");
        // o dono do token é conferido no núcleo: o vínculo de quem toca é sempre o enviado
        when(confirm.run(any(), any())).thenThrow(error(ErrorCode.CONFIRMATION_ALREADY_DONE));

        tap("v1:reg-confirm:OTHERTOK");

        ArgumentCaptor<ConfirmStudentRegistrationUseCase.Command> confirmation =
                ArgumentCaptor.forClass(ConfirmStudentRegistrationUseCase.Command.class);
        verify(confirm).run(any(), confirmation.capture());
        assertThat(confirmation.getValue().linkId()).isEqualTo(linkId());
        assertThat(lastText()).isEqualTo("Essa operação já foi concluída.");
    }

    @Test
    void newSummaryReplacesThePreviousOneAndTheOldTokenIsRefused() {
        open(bia);
        tap("v1:reg-inactivate:");
        String firstToken = TOKEN;
        when(start.run(any(), any())).thenReturn(started(Kind.INACTIVATE, "NEWTOK12"));
        send("meus alunos");
        verify(cancel).run(any(), any());
        tap("v1:reg-open:0");
        tap("v1:reg-inactivate:");
        assertThat(buttonData(lastMessage())).contains("v1:reg-confirm:NEWTOK12");

        when(confirm.run(any(), any())).thenThrow(error(ErrorCode.CONFIRMATION_ALREADY_DONE));
        tap("v1:reg-confirm:" + firstToken);

        assertThat(lastText()).isEqualTo("Essa operação já foi concluída.");
        // a conversa continua esperando a confirmação do resumo novo
        assertThat(persistedStep()).isEqualTo("REGISTRY_CONFIRM");
        when(confirm.run(any(), any()))
                .thenReturn(view("Bia Lima", 12, "Violão", false, "Marta Lima"));
        send("sim");
        assertThat(lastText()).isEqualTo("Bia Lima foi inativado(a).");
    }

    @Test
    void cancelDiscardsTheDraft() {
        open(bia);
        tap("v1:reg-inactivate:");

        tap("v1:reg-cancel:");

        verify(cancel).run(any(), any());
        verify(confirm, never()).run(any(), any());
        assertThat(persistedFlow()).isEqualTo("NONE");
    }
}
