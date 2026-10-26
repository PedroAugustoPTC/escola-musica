package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.DeleteMessage;
import br.com.felipeblaft.reports.support.ConversationState;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Endurecimento do "Meus alunos" no Telegram (revisão pré-PR da S3): duplicidade sem nome para o
 * professor, texto com forma de CPF nunca ecoado nem guardado, CPF digitado no telefone do
 * responsável, token de confirmação dentro do limite de 64 bytes e botão de confirmação fora de
 * estado.
 */
@ConversationState({10})
class StudentRegistryHardeningIT extends RegistryConversationBase {

    private static final String UNAVAILABLE =
            "Não foi possível usar este CPF. Procure o administrador.";
    private static final String EXPIRED = "Este botão não vale mais. Use o menu.";

    @BeforeEach
    void prepare() {
        when(start.run(any(), any())).thenReturn(started(Kind.CREATE, null));
    }

    private void reachCpfStep() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(next(Field.BIRTH_DATE));
        send("Ana Souza");
        when(collect.run(any(), any())).thenReturn(next(Field.CPF));
        send("05/03/1990");
    }

    private void reachGuardianPhoneStep() {
        reachCpfStep();
        when(collect.run(any(), any()))
                .thenReturn(
                        new br.com.felipeblaft.reports.usecase.registry
                                .CollectStudentRegistrationFieldUseCase.Result(
                                Field.GUARDIAN, true, null, null));
        send(CPF);
        send("Marta Lima");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_GUARDIAN_PHONE");
    }

    private static DomainException duplicate(ErrorCode code, String name) {
        return new DomainException(code, Map.of("name", name));
    }

    private int deletions() {
        return (int) out.stream().filter(DeleteMessage.class::isInstance).count();
    }

    // ---- 3. duplicidade nunca mostra o nome ao professor

    @Test
    void duplicateCpfWhileCollectingShowsOnlyTheNeutralTextWithoutTheName() {
        reachCpfStep();
        when(collect.run(any(), any()))
                .thenThrow(duplicate(ErrorCode.DUPLICATE_CPF, "Joana Prado"));

        send(CPF);

        assertThat(lastText()).contains(UNAVAILABLE).contains("Qual é o CPF do aluno?");
        assertThat(everythingSent()).doesNotContain("Joana").doesNotContain("Prado");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CPF");
    }

    @Test
    void duplicateEmailOrCpfOnConfirmationShowsOnlyTheNeutralText() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(ready("Ana Souza", null, null));
        send("Ana Souza");

        when(confirm.run(any(), any()))
                .thenThrow(duplicate(ErrorCode.DUPLICATE_CPF, "Joana Prado"));
        tap("v1:reg-confirm:" + TOKEN);

        assertThat(lastText()).isEqualTo(UNAVAILABLE);
        assertThat(everythingSent()).doesNotContain("Joana").doesNotContain("Prado");

        send("cadastrar aluno");
        when(collect.run(any(), any()))
                .thenThrow(duplicate(ErrorCode.DUPLICATE_EMAIL, "Joana Prado"));
        send("Ana Souza");
        assertThat(lastText()).contains(UNAVAILABLE);
        assertThat(everythingSent()).doesNotContain("Joana").doesNotContain("e-mail");
    }

    // ---- 4. forma de CPF: nunca ecoada, nunca guardada

    @Test
    void cpfShapedSearchWithSeparatorsIsDeletedNeverSearchedNorStored() {
        when(list.run(any(), any())).thenReturn(page(0, 1, view("Ana Souza", 36, null, true, null)));
        send("meus alunos");
        verify(list, times(1)).run(any(), any());

        for (String shaped : new String[] {"529 982 247 25", "529.982.247/25", "52998224725"}) {
            send(shaped);

            assertThat(deletions()).as(shaped).isEqualTo(1);
            assertThat(everythingSent()).as(shaped).doesNotContain("529").doesNotContain("247");
            assertThat(persistedData())
                    .as(shaped)
                    .doesNotContain("529")
                    .doesNotContain("regSearch");
        }
        verify(list, times(1)).run(any(), any());
    }

    @Test
    void cpfShapedTextOnTheHomeSearchIsNotEchoedInTheNotFoundMessage() {
        send("529 982 247 25");

        assertThat(deletions()).isEqualTo(1);
        assertThat(everythingSent())
                .doesNotContain("529")
                .doesNotContain("Nenhum aluno encontrado");
    }

    @Test
    void validCpfTypedAtTheGuardianPhoneStepIsDeletedAndRefusedAsPhone() {
        reachGuardianPhoneStep();
        int collectedBefore = org.mockito.Mockito.mockingDetails(collect).getInvocations().size();

        send("529.982.247-25");

        assertThat(deletions()).isEqualTo(1);
        DeleteMessage delete =
                (DeleteMessage)
                        out.stream()
                                .filter(DeleteMessage.class::isInstance)
                                .findFirst()
                                .orElseThrow();
        assertThat(delete.messageId()).isEqualTo(lastIncomingMessageId);
        assertThat(lastText())
                .contains("Informe um telefone com DDD")
                .contains("telefone do responsável");
        assertThat(everythingSent()).doesNotContain("529").doesNotContain("247");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_GUARDIAN_PHONE");
        assertThat(persistedData()).doesNotContain("529");
        assertThat(org.mockito.Mockito.mockingDetails(collect).getInvocations())
                .hasSize(collectedBefore);
    }

    @Test
    void elevenDigitPhoneThatIsNotAValidCpfStillGoesToTheCore() {
        reachGuardianPhoneStep();
        when(collect.run(any(), any()))
                .thenReturn(
                        new br.com.felipeblaft.reports.usecase.registry
                                .CollectStudentRegistrationFieldUseCase.Result(
                                Field.GUARDIAN, true, null, null));

        send("11 98888-7777");

        assertThat(deletions()).isZero();
        assertThat(lastText()).startsWith("Qual é o e-mail do responsável?");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_GUARDIAN_EMAIL");
    }

    // ---- 5. reg-confirm

    @Test
    void tokenThatDoesNotFitInCallbackDataOffersOnlyCancelAndYesStillConfirms() {
        String longToken = "T".repeat(60);
        send("cadastrar aluno");
        when(collect.run(any(), any()))
                .thenReturn(
                        new br.com.felipeblaft.reports.usecase.registry
                                .CollectStudentRegistrationFieldUseCase.Result(
                                null,
                                false,
                                new br.com.felipeblaft.reports.usecase.registry
                                        .CollectStudentRegistrationFieldUseCase.Summary(
                                        "Ana Souza", null, null, null),
                                longToken));
        send("Ana Souza");

        assertThat(lastText()).startsWith("Confira o cadastro:");
        assertThat(buttonData(lastMessage())).containsExactly("v1:reg-cancel:");
        assertThat(persistedStep()).isEqualTo("REGISTRY_CONFIRM");

        when(confirm.run(any(), any())).thenReturn(view("Ana Souza", 36, null, true, null));
        send("sim");
        assertThat(lastText()).startsWith("Pronto! Ana Souza foi cadastrado(a)");
    }

    @Test
    void confirmTapWithATokenAboveTheLimitIsExpiredAndNeverReachesTheCore() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(ready("Ana Souza", null, null));
        send("Ana Souza");

        tap("v1:reg-confirm:" + "T".repeat(60));

        assertThat(lastText()).isEqualTo(EXPIRED);
        verify(confirm, never()).run(any(), any());
        assertThat(persistedStep()).isEqualTo("REGISTRY_CONFIRM");
    }

    @Test
    void confirmTapOutsideTheRegistryStateIsExpiredLikeCancel() {
        tap("v1:reg-confirm:" + TOKEN);

        assertThat(lastText()).isEqualTo(EXPIRED);
        verify(confirm, never()).run(any(), any());
    }

    @Test
    void secondConfirmTapAfterTheFlowEndedIsExpiredAndTheCoreIsCalledOnce() {
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(ready("Ana Souza", null, null));
        send("Ana Souza");
        when(confirm.run(any(), any())).thenReturn(view("Ana Souza", 36, null, true, null));
        tap("v1:reg-confirm:" + TOKEN);

        tap("v1:reg-confirm:" + TOKEN);

        assertThat(lastText()).isEqualTo(EXPIRED);
        verify(confirm, times(1)).run(any(), any());
    }

    @Test
    void successTextFollowsTheKindOfTheConfirmedDraft() {
        // inativar: o rascunho de INATIVAR leva ao texto de inativado
        var student = view("Ana Souza", 36, "Piano", true, null);
        when(list.run(any(), any())).thenReturn(page(0, 1, student));
        when(get.run(any(), any())).thenReturn(student);
        send("meus alunos");
        tap("v1:reg-open:0");
        when(start.run(any(), any())).thenReturn(started(Kind.INACTIVATE, TOKEN));
        tap("v1:reg-inactivate:");
        when(confirm.run(any(), any())).thenReturn(student);
        tap("v1:reg-confirm:" + TOKEN);

        assertThat(lastText()).contains("inativado");
        assertThat(persistedFlow()).isEqualTo("NONE");

        // novo cadastro logo depois: o texto volta a ser o de cadastrado (não herda o anterior)
        when(start.run(any(), any())).thenReturn(started(Kind.CREATE, null));
        send("cadastrar aluno");
        when(collect.run(any(), any())).thenReturn(ready("Bia Lima", null, null));
        send("Bia Lima");
        tap("v1:reg-confirm:" + TOKEN);
        assertThat(lastText()).contains("cadastrado(a)");
    }
}
