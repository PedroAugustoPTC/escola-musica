package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.support.ScenarioIT;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Orientação sem alunos e sem vínculo (FR-042): o professor sem alunos é convidado a cadastrar o
 * primeiro; conta já vinculada a outro professor e código inválido orientam procurar o
 * administrador; nenhuma resposta cita a "secretaria".
 */
@ConversationState({1, 10})
class NoStudentsOrientationConversationIT extends RegistryConversationBase {

    private static final String INVITATION =
            "Você ainda não tem alunos. Cadastre o primeiro em “Meus alunos”.";

    private void assertNeverMentionsTheSecretariat() {
        assertThat(everythingSent().toLowerCase(Locale.ROOT)).doesNotContain("secre" + "taria");
    }

    @Test
    void homeWithoutStudentsInvitesRegisteringTheFirstOne() {
        send("início");

        assertThat(lastText()).contains("Olá, Clara").contains(INVITATION);
        assertThat(buttonData(lastMessage())).contains("v1:reg-new:");
        assertThat(buttonLabels(lastMessage())).contains("Cadastrar aluno");
        assertNeverMentionsTheSecretariat();
    }

    @Test
    void registeringALessonOrSearchingWithoutStudentsInvitesToo() {
        send("registrar aula");
        assertThat(lastText()).isEqualTo(INVITATION);
        assertThat(buttonData(lastMessage())).contains("v1:reg-new:");
        assertNeverMentionsTheSecretariat();

        send("Joana");
        assertThat(lastText()).isEqualTo(INVITATION);
        assertThat(buttonData(lastMessage())).contains("v1:reg-new:");
        assertNeverMentionsTheSecretariat();
    }

    @Test
    void emptyMyStudentsListInvitesToo() {
        when(list.run(any(), any())).thenReturn(page(0, 0));

        send("meus alunos");

        assertThat(lastText()).isEqualTo(INVITATION);
        assertThat(buttonData(lastMessage())).contains("v1:reg-new:");
        assertNeverMentionsTheSecretariat();
    }

    @Test
    void accountLinkedToAnotherTeacherAndInvalidCodeSendToTheAdministrator() {
        scenario.bruno();

        send(OTHER_USER, ScenarioIT.CODE_CLARA);
        assertThat(lastText())
                .isEqualTo(
                        "Seu cadastro já está vinculado a outra conta do Telegram. Procure o administrador.");
        assertNeverMentionsTheSecretariat();

        send(USER, ScenarioIT.CODE_BRUNO);
        assertThat(lastText())
                .isEqualTo(
                        "Esta conta do Telegram já está vinculada a outro professor. Procure o administrador.");
        assertNeverMentionsTheSecretariat();

        send(OTHER_USER, "ZZZZZZZZ");
        assertThat(lastText()).isEqualTo("Código inválido ou vencido. Procure o administrador.");
        assertNeverMentionsTheSecretariat();
    }

    @Test
    void accountWithoutLinkOnlyReceivesTheEntryOrientation() {
        send(OTHER_USER, "meus alunos");
        assertThat(lastText())
                .isEqualTo(
                        "Olá! Para usar o bot, envie o código de acesso que você recebeu do administrador da escola.");
        assertThat(buttonData(lastMessage())).isEmpty();
        assertNeverMentionsTheSecretariat();
    }
}
