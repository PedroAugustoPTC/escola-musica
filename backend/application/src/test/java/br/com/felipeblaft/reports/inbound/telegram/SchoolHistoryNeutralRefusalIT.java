package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.usecase.document.GetSchoolReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.history.QuerySchoolHistoryUseCase;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * A visão da escola é só Web (feature 006, FR-001 e FR-026): no Telegram, "histórico da escola" e
 * variantes recebem a negativa neutra da 005 (FR-037) seguida do menu do professor, sem citar Web
 * nem administrador; a conta sem vínculo recebe só a orientação de entrada. Nenhum caso de uso da
 * visão da escola é chamado.
 */
class SchoolHistoryNeutralRefusalIT extends RegistryConversationBase {

    private static final List<String> ATTEMPTS =
            List.of(
                    "histórico da escola",
                    "histórico de todos os alunos",
                    "relatórios de todos os professores",
                    "ver relatórios da escola");

    private static final List<String> FORBIDDEN =
            List.of("web", "administra" + "dor", "administrativa", "portal", "secre" + "taria");

    @MockitoSpyBean QuerySchoolHistoryUseCase querySchoolHistory;
    @MockitoSpyBean GetSchoolReportDocumentUseCase getSchoolReportDocument;

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
        verifyNoInteractions(querySchoolHistory, getSchoolReportDocument);
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
        verifyNoInteractions(querySchoolHistory, getSchoolReportDocument);
    }
}
