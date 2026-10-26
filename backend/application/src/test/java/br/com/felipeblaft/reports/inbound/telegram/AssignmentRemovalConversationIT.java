package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Retirada da associação (US5): depois dela o aluno some da lista do professor e o botão antigo
 * responde como aluno inexistente; um rascunho de edição, inativação ou reativação já aberto
 * responde como aluno inexistente na confirmação, sem gravar. A decisão é do núcleo (dublê); o
 * adaptador só apresenta a resposta única de "não encontramos".
 */
@ConversationState({10})
class AssignmentRemovalConversationIT extends RegistryConversationBase {

    private static final String NOT_FOUND = "Não encontramos o que você procurou.";

    private final StudentView ana = view("Ana Souza", 36, "Piano", true, null);
    private final StudentView bia = view("Bia Lima", 12, "Violão", true, "Marta Lima");

    @BeforeEach
    void listWithBothStudents() {
        when(list.run(any(), any())).thenReturn(page(0, 2, ana, bia));
        send("meus alunos");
    }

    @Test
    void afterTheRemovalTheStudentLeavesTheListAndTheOldButtonLooksNonexistent() {
        when(list.run(any(), any())).thenReturn(page(0, 1, ana));
        send("meus alunos");
        assertThat(buttonLabels(lastMessage())).contains("Ana Souza").doesNotContain("Bia Lima");

        // o botão da lista antiga (posição 1) aponta para um aluno que deixou de ser visível
        when(get.run(any(), any())).thenThrow(error(ErrorCode.RESOURCE_NOT_FOUND));
        tap("v1:reg-open:1");

        assertThat(lastText()).isEqualTo(NOT_FOUND);
        assertThat(everythingSent()).doesNotContain("Bia");
    }

    @Test
    void removalBetweenStartingAndConfirmingAnInactivationAnswersNonexistentWithoutRecording() {
        when(get.run(any(), any())).thenReturn(bia);
        tap("v1:reg-open:1");
        when(start.run(any(), any())).thenReturn(started(Kind.INACTIVATE, TOKEN));
        tap("v1:reg-inactivate:");

        when(confirm.run(any(), any())).thenThrow(error(ErrorCode.RESOURCE_NOT_FOUND));
        tap("v1:reg-confirm:" + TOKEN);

        assertThat(lastText()).isEqualTo(NOT_FOUND);
        assertThat(persistedFlow()).isEqualTo("NONE");
        verify(cancel).run(any(), any());
    }

    @Test
    void sameForAnEditAndAReactivationAlreadyOpen() {
        when(get.run(any(), any())).thenReturn(bia);
        tap("v1:reg-open:1");
        when(start.run(any(), any())).thenReturn(started(Kind.UPDATE, null));
        tap("v1:reg-edit:");
        tap("v1:reg-edit:instrument");
        when(collect.run(any(), any())).thenReturn(ready("Bia Lima", "Piano", "Marta Lima"));
        send("Piano");

        when(confirm.run(any(), any())).thenThrow(error(ErrorCode.RESOURCE_NOT_FOUND));
        send("sim");

        assertThat(lastText()).isEqualTo(NOT_FOUND);
        assertThat(persistedFlow()).isEqualTo("NONE");

        send("meus alunos");
        when(get.run(any(), any())).thenReturn(view("Bia Lima", 12, null, false, null));
        tap("v1:reg-open:1");
        when(start.run(any(), any())).thenReturn(started(Kind.REACTIVATE, "REAC1234"));
        tap("v1:reg-reactivate:");
        tap("v1:reg-confirm:REAC1234");
        assertThat(lastText()).isEqualTo(NOT_FOUND);
    }
}
