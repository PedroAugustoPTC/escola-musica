package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.felipeblaft.reports.support.ConversationState;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * O Telegram nunca reconhece operação administrativa (SC-003, contracts/telegram-student-registry.md
 * §6): "criar professor", "reemitir código", e-mail e senha recebem a resposta de texto não
 * reconhecido seguida do menu do professor, sem citar Web, administrador ou área administrativa. A
 * conta sem vínculo recebe só a orientação de entrada.
 */
class AdminOperationDeniedConversationIT extends RegistryConversationBase {

    private static final List<String> ATTEMPTS =
            List.of("criar professor", "reemitir código", "maria@example.test", "S3nh@Forte-2026");

    private static final List<String> FORBIDDEN =
            List.of("web", "administrador", "administrativa", "portal", "secre" + "taria");

    @BeforeEach
    void students() {
        scenario.student(clara, "Matteo Mafra", "Piano");
    }

    @Test
    @ConversationState({10})
    void linkedTeacherGetsTheNeutralDenialFollowedByTheTeacherMenu() {
        for (String attempt : ATTEMPTS) {
            send(attempt);

            String reply = lastText();
            assertThat(reply).isEqualTo("Nenhum aluno encontrado para \"" + attempt + "\".");
            assertThat(reply.replace(attempt, "").toLowerCase(Locale.ROOT))
                    .doesNotContain(FORBIDDEN.toArray(String[]::new));
            assertThat(buttonData(lastMessage()))
                    .contains(
                            "v1:register:",
                            "v1:semester:",
                            "v1:history:",
                            "v1:reg-list:",
                            "v1:reg-new:",
                            "v1:home:");
        }
        verifyNoInteractions(start, collect, confirm, cancel, list, get);
        assertThat(jdbc.queryForObject("select count(*) from teacher", Integer.class)).isEqualTo(1);
    }

    @Test
    @ConversationState({1})
    void accountWithoutLinkOnlyReceivesTheEntryOrientation() {
        for (String attempt : ATTEMPTS) {
            send(OTHER_USER, attempt);

            assertThat(messages()).hasSize(1);
            assertThat(lastText())
                    .isEqualTo(
                            "Olá! Para usar o bot, envie o código de acesso que você recebeu do administrador da escola.");
            assertThat(buttonData(lastMessage())).isEmpty();
        }
        assertThat(links()).isEqualTo(1);
    }

    @Test
    @ConversationState({10})
    void administrativeWordsAreNotKeywordsNorButtons() {
        send("início");

        assertThat(buttonLabels(lastMessage()).toString().toLowerCase(Locale.ROOT))
                .doesNotContain("administra", "professor", "web");
        assertThat(buttonData(lastMessage()))
                .noneMatch(data -> data.contains("admin") || data.contains("teacher"));
    }
}
