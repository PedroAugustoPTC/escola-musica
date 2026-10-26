package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.DeleteMessage;
import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.usecase.registry.CollectStudentRegistrationFieldUseCase;
import br.com.felipeblaft.reports.usecase.registry.StartStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Edição campo a campo (contracts/telegram-student-registry.md §3.3): o resumo traz só o que muda,
 * o CPF aparece como "será substituído" e a mensagem com o novo CPF é apagada.
 */
@ConversationState({10})
class StudentRegistryEditConversationIT extends RegistryConversationBase {

    private final StudentView bia = view("Bia Lima", 12, "Violão", true, "Marta Lima");

    @BeforeEach
    void openTheCard() {
        when(list.run(any(), any())).thenReturn(page(0, 1, bia));
        when(get.run(any(), any())).thenReturn(bia);
        when(start.run(any(), any())).thenReturn(started(Kind.UPDATE, null));
        send("meus alunos");
        tap("v1:reg-open:0");
    }

    @Test
    void editingStartsAnUpdateDraftAndOffersTheFields() {
        tap("v1:reg-edit:");

        ArgumentCaptor<StartStudentRegistrationUseCase.Command> command =
                ArgumentCaptor.forClass(StartStudentRegistrationUseCase.Command.class);
        verify(start).run(any(), command.capture());
        assertThat(command.getValue().kind()).isEqualTo(Kind.UPDATE);
        assertThat(command.getValue().studentId()).isEqualTo(bia.id());
        assertThat(command.getValue().linkId()).isEqualTo(linkId());
        assertThat(lastText()).isEqualTo("O que você quer editar?");
        assertThat(buttonLabels(lastMessage()))
                .contains("Nome", "Nascimento", "CPF", "Responsável", "Instrumento", "Cancelar");
        assertThat(buttonData(lastMessage()))
                .contains(
                        "v1:reg-edit:name",
                        "v1:reg-edit:birth",
                        "v1:reg-edit:cpf",
                        "v1:reg-edit:guardian",
                        "v1:reg-edit:instrument");
        assertThat(persistedStep()).isEqualTo("REGISTRY_EDIT_FIELD");
    }

    @Test
    void editingTheNameShowsASummaryWithOnlyWhatChanges() {
        tap("v1:reg-edit:");
        tap("v1:reg-edit:name");
        assertThat(lastText()).isEqualTo("Qual é o nome do aluno?");

        when(collect.run(any(), any())).thenReturn(ready("Beatriz Lima", "Violão", "Marta Lima"));
        send("Beatriz Lima");

        ArgumentCaptor<CollectStudentRegistrationFieldUseCase.Command> command =
                ArgumentCaptor.forClass(CollectStudentRegistrationFieldUseCase.Command.class);
        verify(collect).run(any(), command.capture());
        assertThat(command.getValue().field()).isEqualTo(Field.NAME);
        assertThat(lastText()).startsWith("Confira as alterações em Bia Lima:");
        assertThat(lastText())
                .contains("Nome: Beatriz Lima")
                .doesNotContain("Instrumento", "Responsável", "Nascimento");
        assertThat(buttonData(lastMessage()))
                .containsExactly("v1:reg-confirm:" + TOKEN, "v1:reg-cancel:");
    }

    @Test
    void replacingTheCpfSaysItWillBeReplacedAndDeletesTheMessage() {
        tap("v1:reg-edit:");
        tap("v1:reg-edit:cpf");
        assertThat(lastText()).startsWith("Qual é o CPF do aluno?");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_CPF");

        when(collect.run(any(), any())).thenReturn(ready("Bia Lima", "Violão", "Marta Lima"));
        send("390.533.447-05");

        int deletion = out.stream().map(Object::getClass).toList().indexOf(DeleteMessage.class);
        assertThat(deletion).isZero();
        assertThat(lastText()).contains("O CPF será substituído.");
        assertThat(everythingSent()).doesNotContain("390").doesNotContain("533.447");
        assertThat(persistedData()).doesNotContain("390533447");
    }

    @Test
    void makingTheStudentAMinorAsksForTheGuardianBeforeTheSummary() {
        tap("v1:reg-edit:");
        tap("v1:reg-edit:birth");
        when(collect.run(any(), any()))
                .thenReturn(
                        new CollectStudentRegistrationFieldUseCase.Result(
                                Field.GUARDIAN, true, null, null));

        send("05/03/2015");

        assertThat(lastText()).startsWith("O aluno tem menos de 18 anos");
        assertThat(persistedStep()).isEqualTo("REGISTRY_ASK_GUARDIAN_NAME");
    }

    @Test
    void confirmingTheEditSaysTheDataWasUpdated() {
        tap("v1:reg-edit:");
        tap("v1:reg-edit:instrument");
        when(collect.run(any(), any())).thenReturn(ready("Bia Lima", "Piano", "Marta Lima"));
        send("Piano");

        when(confirm.run(any(), any())).thenReturn(view("Bia Lima", 12, "Piano", true, "Marta Lima"));
        tap("v1:reg-confirm:" + TOKEN);

        assertThat(lastText()).isEqualTo("Pronto! Os dados de Bia Lima foram atualizados.");
        assertThat(persistedFlow()).isEqualTo("NONE");
    }
}
