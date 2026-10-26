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
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.DeleteMessage;
import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.usecase.registry.CollectStudentRegistrationFieldUseCase;
import br.com.felipeblaft.reports.usecase.registry.ConfirmStudentRegistrationUseCase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Cadastro guiado de aluno pelo Telegram (contracts/telegram-student-registry.md §3.1 e §4): nome,
 * data, CPF, responsável se menor, instrumento ou "pular", resumo sem CPF e confirmação. O núcleo é
 * um dublê (fatia S2); o adaptador só encaminha, apresenta e apaga a mensagem com o CPF.
 */
@ConversationState({10})
class StudentRegistryConversationIT extends RegistryConversationBase {

    @BeforeEach
    void prepare() {
        when(start.run(any(), any())).thenReturn(started(Kind.CREATE, null));
    }

    @Test
    void guidedRegistrationOfAnAdultWithSkippedInstrument() {
        send("cadastrar aluno");
        assertThat(lastText()).isEqualTo("Qual é o nome do aluno?");
        assertThat(persistedFlow()).isEqualTo("STUDENT_REGISTRY");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_NAME");

        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Ana Souza");
        assertThat(lastText()).startsWith("Qual é a data de nascimento?");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_BIRTH_DATE");

        when(collect.run(any(), any())).thenReturn(next(Field.CPF));
        send("05/03/1990");
        assertThat(lastText()).startsWith("Qual é o CPF do aluno?");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CPF");

        when(collect.run(any(), any())).thenReturn(next(Field.INSTRUMENT));
        send(CPF);
        assertThat(lastText()).startsWith("Qual é o instrumento?");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_INSTRUMENT");

        when(collect.run(any(), any())).thenReturn(ready("Ana Souza", null, null));
        send("pular");
        assertThat(lastText()).startsWith("Confira o cadastro:").contains("Nome: Ana Souza");
        assertThat(buttonData(lastMessage()))
                .containsExactly("v1:reg-confirm:" + TOKEN, "v1:reg-cancel:");
        assertThat(persistedStep()).isEqualTo("REGISTRY_CONFIRM");

        ArgumentCaptor<CollectStudentRegistrationFieldUseCase.Command> commands =
                ArgumentCaptor.forClass(CollectStudentRegistrationFieldUseCase.Command.class);
        verify(collect, org.mockito.Mockito.times(4)).run(any(), commands.capture());
        assertThat(commands.getAllValues())
                .extracting(CollectStudentRegistrationFieldUseCase.Command::field)
                .containsExactly(Field.NAME, Field.BIRTH_DATE, Field.CPF, Field.INSTRUMENT);
        assertThat(commands.getAllValues().get(3).text()).isEmpty();
        assertThat(commands.getAllValues().get(2).text()).isEqualTo(CPF);
        assertThat(commands.getAllValues()).allMatch(c -> c.linkId().equals(linkId()));

        when(confirm.run(any(), any())).thenReturn(view("Ana Souza", 36, null, true, null));
        tap("v1:reg-confirm:" + TOKEN);
        assertThat(lastText())
                .isEqualTo("Pronto! Ana Souza foi cadastrado(a) e está entre os seus alunos.");
        ArgumentCaptor<ConfirmStudentRegistrationUseCase.Command> confirmation =
                ArgumentCaptor.forClass(ConfirmStudentRegistrationUseCase.Command.class);
        verify(confirm).run(any(), confirmation.capture());
        assertThat(confirmation.getValue().confirmationToken()).isEqualTo(TOKEN);
        assertThat(confirmation.getValue().linkId()).isEqualTo(linkId());
        assertThat(persistedFlow()).isEqualTo("NONE");
        assertThat(persistedStep()).isEqualTo("NONE");
    }

    @Test
    void confirmByTextYesUsesTheStoredToken() {
        send("novo aluno");
        when(collect.run(any(), any())).thenReturn(ready("Ana Souza", "Piano", null));
        send("Ana Souza");

        when(confirm.run(any(), any())).thenReturn(view("Ana Souza", 36, "Piano", true, null));
        send("sim");

        ArgumentCaptor<ConfirmStudentRegistrationUseCase.Command> confirmation =
                ArgumentCaptor.forClass(ConfirmStudentRegistrationUseCase.Command.class);
        verify(confirm).run(any(), confirmation.capture());
        assertThat(confirmation.getValue().confirmationToken()).isEqualTo(TOKEN);
        assertThat(lastText()).startsWith("Pronto! Ana Souza foi cadastrado(a)");
    }

    @Test
    void minorGetsThreeGuardianQuestionsAndTheSummaryShowsOnlyTheGuardianName() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Davi Lima");
        when(collect.run(any(), any())).thenReturn(next(Field.CPF));
        send("05/03/2014");
        when(collect.run(any(), any()))
                .thenReturn(
                        new CollectStudentRegistrationFieldUseCase.Result(
                                Field.GUARDIAN, true, null, null));
        send(CPF);
        assertThat(lastText())
                .startsWith("O aluno tem menos de 18 anos. Qual é o nome do responsável?");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_GUARDIAN_NAME");

        when(collect.run(any(), any()))
                .thenReturn(
                        new CollectStudentRegistrationFieldUseCase.Result(
                                Field.GUARDIAN, true, null, null));
        send("Marta Lima");
        assertThat(lastText()).startsWith("Qual é o telefone do responsável");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_GUARDIAN_PHONE");

        send("(11) 98888-7777");
        assertThat(lastText()).startsWith("Qual é o e-mail do responsável?");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_GUARDIAN_EMAIL");

        when(collect.run(any(), any())).thenReturn(next(Field.INSTRUMENT));
        send("marta@example.test");
        assertThat(lastText()).startsWith("Qual é o instrumento?");

        when(collect.run(any(), any())).thenReturn(ready("Davi Lima", "Violão", "Marta Lima"));
        send("Violão");
        String summary = lastText();
        assertThat(summary)
                .contains("Nome: Davi Lima", "Instrumento: Violão", "Responsável: Marta Lima")
                .doesNotContain("98888", "marta@example.test", CPF_DIGITS);

        ArgumentCaptor<CollectStudentRegistrationFieldUseCase.Command> commands =
                ArgumentCaptor.forClass(CollectStudentRegistrationFieldUseCase.Command.class);
        verify(collect, org.mockito.Mockito.times(6)).run(any(), commands.capture());
        assertThat(commands.getAllValues())
                .extracting(CollectStudentRegistrationFieldUseCase.Command::field)
                .containsExactly(
                        Field.NAME,
                        Field.BIRTH_DATE,
                        Field.CPF,
                        Field.GUARDIAN,
                        Field.GUARDIAN,
                        Field.GUARDIAN);
    }

    @Test
    void cpfMessageIsDeletedBeforeTheNextQuestionAndNeverEchoed() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Ana Souza");
        when(collect.run(any(), any())).thenReturn(next(Field.CPF));
        send("05/03/1990");

        when(collect.run(any(), any())).thenReturn(next(Field.INSTRUMENT));
        send(CPF);

        int deletion = indexOfDeletion();
        int question = out.indexOf(lastMessage());
        assertThat(deletion).isGreaterThanOrEqualTo(0).isLessThan(question);
        DeleteMessage delete = (DeleteMessage) out.get(deletion);
        assertThat(delete.messageId()).isEqualTo(lastIncomingMessageId);
        assertThat(delete.chatId()).isEqualTo(USER);
        assertThat(everythingSent()).doesNotContain(CPF_DIGITS).doesNotContain(CPF);
        assertThat(persistedData()).doesNotContain(CPF_DIGITS).doesNotContain("529.982");
    }

    @Test
    void invalidCpfIsStillDeletedAndTheQuestionIsRepeatedWithoutTheValue() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Ana Souza");
        when(collect.run(any(), any())).thenReturn(next(Field.CPF));
        send("05/03/1990");

        when(collect.run(any(), any())).thenThrow(invalid("cpf", "INVALID_CPF"));
        send("111.111.111-11");

        assertThat(indexOfDeletion()).isGreaterThanOrEqualTo(0);
        assertThat(lastText())
                .contains("O CPF informado não é válido.")
                .contains("Qual é o CPF do aluno?")
                .doesNotContain("111");
        assertThat(everythingSent()).doesNotContain("111.111");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CPF");
    }

    @Test
    void validationErrorRepeatsTheQuestionWithTheReasonAndWithoutTheTypedValue() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Ana Souza");

        when(collect.run(any(), any())).thenThrow(invalid("birthDate", "INVALID_BIRTH_DATE"));
        send("31/02/1999");

        assertThat(lastText())
                .contains("Informe uma data de nascimento real")
                .contains("Qual é a data de nascimento?")
                .doesNotContain("31/02/1999");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_BIRTH_DATE");
        assertThat(indexOfDeletion()).isNegative();
    }

    @Test
    void unavailableCpfKeepsTheDraftAndAsksForTheCpfAgain() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Ana Souza");
        when(collect.run(any(), any())).thenReturn(next(Field.CPF));
        send("05/03/1990");

        when(collect.run(any(), any())).thenThrow(error(ErrorCode.CPF_UNAVAILABLE));
        send(CPF);

        assertThat(lastText())
                .contains("Não foi possível usar este CPF. Procure o administrador.")
                .contains("Qual é o CPF do aluno?");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CPF");
        verify(cancel, never()).run(any(), any());

        when(collect.run(any(), any())).thenThrow(error(ErrorCode.STUDENT_ALREADY_YOURS));
        send(CPF);
        assertThat(lastText()).contains("Este aluno já está entre os seus.");
    }

    @Test
    void cancelDiscardsTheDraftAndRecordsNothing() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(ready("Ana Souza", null, null));
        send("Ana Souza");

        tap("v1:reg-cancel:");

        verify(cancel).run(any(), any());
        verify(confirm, never()).run(any(), any());
        assertThat(lastText()).isEqualTo("Cadastro cancelado. Nada foi gravado.");
        assertThat(persistedFlow()).isEqualTo("NONE");
    }

    @Test
    void cancelByTextWordWorksTooAndAHomeCommandDiscardsTheDraft() {
        send("cadastrar aluno");
        send("cancelar");
        verify(cancel).run(any(), any());
        assertThat(lastText()).isEqualTo("Cadastro cancelado. Nada foi gravado.");

        send("cadastrar aluno");
        send("início");
        verify(cancel, org.mockito.Mockito.times(2)).run(any(), any());
        assertThat(lastText()).contains("Olá, Clara");
        assertThat(persistedFlow()).isEqualTo("NONE");
    }

    @Test
    void expiredDraftIsReportedAndTheFlowEnds() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenThrow(error(ErrorCode.CONFIRMATION_EXPIRED));
        send("Ana Souza");

        assertThat(lastText())
                .isEqualTo(
                        "O cadastro ficou parado por muito tempo e foi descartado. Nada foi gravado.");
        assertThat(persistedFlow()).isEqualTo("NONE");
        verify(confirm, never()).run(any(), any());
    }

    @Test
    void secondConfirmationAnswersAlreadyDoneAndKeepsTheConversation() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(ready("Ana Souza", null, null));
        send("Ana Souza");

        when(confirm.run(any(), any())).thenReturn(view("Ana Souza", 36, null, true, null));
        tap("v1:reg-confirm:" + TOKEN);
        when(confirm.run(any(), any())).thenThrow(error(ErrorCode.CONFIRMATION_ALREADY_DONE));
        tap("v1:reg-confirm:" + TOKEN);

        assertThat(lastText()).isEqualTo("Essa operação já foi concluída.");
        verify(confirm, org.mockito.Mockito.times(2)).run(any(), any());
    }

    @Test
    void anyOtherTextAtTheSummaryOnlyRepeatsHowToConfirm() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(ready("Ana Souza", null, null));
        send("Ana Souza");

        send("talvez");

        assertThat(lastText()).contains("Responda \"sim\" para confirmar");
        verify(confirm, never()).run(any(), any());
        assertThat(persistedStep()).isEqualTo("REGISTRY_CONFIRM");
    }

    @Test
    void skipOutsideTheOptionalStepIsNotAllowed() {
        send("cadastrar aluno");
        send("pular");

        assertThat(lastText()).isEqualTo("Esta ação não está disponível nesta etapa do relatório.");
        verifyNoInteractions(collect);
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_NAME");
    }

    @Test
    void oldButtonsOutsideTheRegistryStateAnswerExpired() {
        tap("v1:reg-edit:cpf");
        assertThat(lastText()).isEqualTo("Este botão não vale mais. Use o menu.");
        tap("v1:reg-page:2");
        assertThat(lastText()).isEqualTo("Este botão não vale mais. Use o menu.");
        tap("v1:reg-open:0");
        assertThat(lastText()).isEqualTo("Este botão não vale mais. Use o menu.");
        tap("v1:reg-skip:");
        assertThat(lastText()).isEqualTo("Este botão não vale mais. Use o menu.");
        verifyNoInteractions(collect, list, get);
    }

    @Test
    void firstTapOnTheMenuStartsTheRegistration() {
        tap("v1:reg-new:");
        assertThat(lastText()).isEqualTo("Qual é o nome do aluno?");
        verify(start).run(any(), any());
        assertThat(List.of(persistedFlow(), persistedStep()))
                .containsExactly("STUDENT_REGISTRY", "REGISTRY_ASK_NAME");
    }

    private int indexOfDeletion() {
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) instanceof DeleteMessage) {
                return i;
            }
        }
        return -1;
    }
}
