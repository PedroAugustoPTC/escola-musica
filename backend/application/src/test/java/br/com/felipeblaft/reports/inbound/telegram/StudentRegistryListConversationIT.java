package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.DeleteMessage;
import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.usecase.registry.GetStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.ListStudentsUseCase;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * "Meus alunos": lista de 6 por página, filtro Ativos/Inativos, busca por texto, cartão sem CPF e
 * lista vazia (contracts/telegram-student-registry.md §3.2). A ordem alfabética e o escopo do
 * professor são do núcleo (dublê); o adaptador apresenta e repassa.
 */
@ConversationState({10})
class StudentRegistryListConversationIT extends RegistryConversationBase {

    private final StudentView ana = view("Ana Souza", 36, "Piano", true, null);
    private final StudentView bia = view("Bia Lima", 12, "Violão", true, "Marta Lima");

    private ListStudentsUseCase.Command lastListCommand() {
        ArgumentCaptor<ListStudentsUseCase.Command> captor =
                ArgumentCaptor.forClass(ListStudentsUseCase.Command.class);
        verify(list, org.mockito.Mockito.atLeastOnce()).run(any(), captor.capture());
        return captor.getAllValues().getLast();
    }

    @Test
    void firstPageShowsSixPerPageWithNextButtonOnly() {
        when(list.run(any(), any())).thenReturn(page(0, 8, ana, bia, ana, bia, ana, bia));

        send("meus alunos");

        assertThat(lastText())
                .isEqualTo(
                        "Seus alunos (ativos), página 1 de 2. Toque em um aluno ou envie parte do nome ou do instrumento.");
        List<String> data = buttonData(lastMessage());
        assertThat(data)
                .contains("v1:reg-open:0", "v1:reg-open:5", "v1:reg-page:1")
                .doesNotContain("v1:reg-page:-1", "v1:reg-open:6");
        assertThat(buttonLabels(lastMessage()))
                .contains("Ana Souza", "Próxima ›", "Ativos", "Inativos")
                .doesNotContain("‹ Anterior");
        assertThat(lastListCommand().page().size()).isEqualTo(6);
        assertThat(lastListCommand().page().page()).isZero();
        assertThat(lastListCommand().status()).isEqualTo(StudentStatusFilter.ACTIVE);
        assertThat(persistedFlow()).isEqualTo("STUDENT_REGISTRY");
        assertThat(persistedStep()).isEqualTo("REGISTRY_LIST");
    }

    @Test
    void secondPageOffersPreviousAndKeepsTheFilter() {
        when(list.run(any(), any())).thenReturn(page(0, 8, ana, bia, ana, bia, ana, bia));
        send("alunos");

        when(list.run(any(), any())).thenReturn(page(1, 8, ana, bia));
        tap("v1:reg-page:1");

        assertThat(lastText()).contains("página 2 de 2");
        assertThat(buttonLabels(lastMessage())).contains("‹ Anterior").doesNotContain("Próxima ›");
        assertThat(buttonData(lastMessage())).contains("v1:reg-page:0");
        assertThat(lastListCommand().page().page()).isEqualTo(1);
    }

    @Test
    void inactiveFilterRestartsAtThePageZero() {
        when(list.run(any(), any())).thenReturn(page(0, 1, ana));
        send("meus alunos");

        when(list.run(any(), any()))
                .thenReturn(page(0, 1, view("Caio Reis", 9, null, false, "Rita")));
        tap("v1:reg-filter:INACTIVE");

        assertThat(lastText()).contains("Seus alunos (inativos), página 1 de 1");
        assertThat(lastListCommand().status()).isEqualTo(StudentStatusFilter.INACTIVE);
        assertThat(lastListCommand().page().page()).isZero();
    }

    @Test
    void textInTheListIsASearchByNameOrInstrument() {
        when(list.run(any(), any())).thenReturn(page(0, 2, ana, bia));
        send("meus alunos");

        when(list.run(any(), any())).thenReturn(page(0, 1, bia));
        send("violão");

        assertThat(lastListCommand().search()).isEqualTo("violão");
        assertThat(buttonLabels(lastMessage())).contains("Bia Lima").doesNotContain("Ana Souza");

        tap("v1:reg-page:0");
        assertThat(lastListCommand().search()).isEqualTo("violão");
    }

    @Test
    void textShapedLikeACpfNeverBecomesASearchNorIsStored() {
        when(list.run(any(), any())).thenReturn(page(0, 2, ana, bia));
        send("meus alunos");
        org.mockito.Mockito.clearInvocations(list);

        send(CPF);

        verify(list, never()).run(any(), any());
        assertThat(out.getFirst()).isInstanceOf(DeleteMessage.class);
        assertThat(((DeleteMessage) out.getFirst()).messageId()).isEqualTo(lastIncomingMessageId);
        assertThat(lastText()).isEqualTo("Confira os campos indicados e tente de novo.");
        assertThat(everythingSent()).doesNotContain(CPF_DIGITS).doesNotContain(CPF);
        assertThat(persistedData()).doesNotContain("529").doesNotContain(CPF_DIGITS);

        send(CPF_DIGITS);
        assertThat(out.getFirst()).isInstanceOf(DeleteMessage.class);
        verify(list, never()).run(any(), any());
        assertThat(persistedData()).doesNotContain(CPF_DIGITS);
    }

    @Test
    void cardShowsNameAgeInstrumentStatusAndOnlyTheGuardianName() {
        when(list.run(any(), any())).thenReturn(page(0, 2, ana, bia));
        send("meus alunos");
        when(get.run(any(), any())).thenReturn(bia);

        tap("v1:reg-open:1");

        ArgumentCaptor<GetStudentUseCase.Command> command =
                ArgumentCaptor.forClass(GetStudentUseCase.Command.class);
        verify(get).run(any(), command.capture());
        assertThat(command.getValue().studentId()).isEqualTo(bia.id());
        assertThat(lastText())
                .isEqualTo("Bia Lima\n12 anos · Violão\nSituação: Ativo\nResponsável: Marta Lima");
        assertThat(everythingSent()).doesNotContain("11999990000").doesNotContain("g@example.test");
        assertThat(buttonData(lastMessage()))
                .containsExactly("v1:reg-edit:", "v1:reg-inactivate:", "v1:reg-back:");
        assertThat(persistedStep()).isEqualTo("REGISTRY_CARD");
    }

    @Test
    void cardOfAnInactiveStudentOffersReactivation() {
        StudentView caio = view("Caio Reis", 9, null, false, null);
        when(list.run(any(), any())).thenReturn(page(0, 1, caio));
        send("meus alunos");
        when(get.run(any(), any())).thenReturn(caio);

        tap("v1:reg-open:0");

        assertThat(lastText()).contains("Situação: Inativo");
        assertThat(buttonData(lastMessage()))
                .contains("v1:reg-reactivate:")
                .doesNotContain("v1:reg-inactivate:");
    }

    @Test
    void tamperedPositionOrStudentOfAnotherTeacherLooksNonexistent() {
        when(list.run(any(), any())).thenReturn(page(0, 2, ana, bia));
        send("meus alunos");

        tap("v1:reg-open:9");
        assertThat(lastText()).isEqualTo("Este botão não vale mais. Use o menu.");
        tap("v1:reg-open:abc");
        assertThat(lastText()).isEqualTo("Este botão não vale mais. Use o menu.");
        verify(get, never()).run(any(), any());

        when(get.run(any(), any())).thenThrow(error(ErrorCode.RESOURCE_NOT_FOUND));
        tap("v1:reg-open:0");
        assertThat(lastText()).isEqualTo("Não encontramos o que você procurou.");
    }

    @Test
    void emptyListInvitesRegisteringTheFirstStudent() {
        when(list.run(any(), any())).thenReturn(page(0, 0));

        send("meus alunos");

        assertThat(lastText())
                .isEqualTo("Você ainda não tem alunos. Cadastre o primeiro em “Meus alunos”.")
                .doesNotContainIgnoringCase("secretaria");
        assertThat(buttonData(lastMessage())).contains("v1:reg-new:");
        assertThat(buttonLabels(lastMessage())).contains("Cadastrar aluno");
    }

    @Test
    void emptyFilterResultSaysSo() {
        when(list.run(any(), any())).thenReturn(page(0, 1, ana));
        send("meus alunos");
        when(list.run(any(), any())).thenReturn(page(0, 0));

        tap("v1:reg-filter:INACTIVE");

        assertThat(lastText()).isEqualTo("Nenhum aluno encontrado nesta lista.");
        assertThat(buttonData(lastMessage())).contains("v1:reg-filter:ACTIVE");
    }

    @Test
    void backFromTheCardReturnsToTheListAndBackFromTheListGoesHome() {
        when(list.run(any(), any())).thenReturn(page(0, 1, ana));
        send("meus alunos");
        when(get.run(any(), any())).thenReturn(ana);
        tap("v1:reg-open:0");

        tap("v1:reg-back:");
        assertThat(persistedStep()).isEqualTo("REGISTRY_LIST");
        assertThat(lastText()).startsWith("Seus alunos (ativos)");

        send("voltar");
        assertThat(lastText()).contains("Olá, Clara");
        assertThat(persistedFlow()).isEqualTo("NONE");
    }

    @Test
    void theMenuOffersMyStudentsAndRegisterStudent() {
        send("início");

        assertThat(buttonLabels(lastMessage())).contains("Meus alunos", "Cadastrar aluno");
        assertThat(buttonData(lastMessage())).contains("v1:reg-list:", "v1:reg-new:");

        when(list.run(any(), any())).thenReturn(page(0, 1, ana));
        tap("v1:reg-list:");
        assertThat(lastText()).startsWith("Seus alunos (ativos)");
    }
}
