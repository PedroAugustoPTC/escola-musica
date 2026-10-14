package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Feature 003, FR-025: um botão antigo (mensagem enviada antes da migração, com a ação em português
 * no {@code callback_data}) cai no ramo de "ação desconhecida": o professor recebe o aviso de opção
 * expirada e a tela atual, sem exceção e sem efeito sobre o relatório. O formato continua {@code
 * v1:<ação>:<argumento>}; só o vocabulário da ação mudou.
 */
class OldButtonFormatIT extends TelegramConversationBase {

    private Teacher clara;

    @BeforeEach
    void load() {
        clara = scenario.clara();
        scenario.student(clara, "Matteo Mafra", "Piano");
    }

    @Test
    void approveButtonInTheOldFormatAnswersExpiredOptionAndShowsHome() throws Exception {
        sendText(USER_CLARA, "k7qm-2xpa");
        int before = sentTexts().size();

        sendCallback(USER_CLARA, "v1:aprov:3");

        assertThat(sentTexts().subList(before, sentTexts().size()))
                .anySatisfy(text -> assertThat(text).contains("Esta opção não vale mais."));
        assertThat(jdbc.queryForObject("select count(*) from lesson_report", Integer.class))
                .isZero();
    }

    @Test
    void everyOldActionVocabularyWordIsAnUnknownAction() throws Exception {
        sendText(USER_CLARA, "k7qm-2xpa");
        for (String old :
                new String[] {"aprovar", "cancelar", "descartar", "sair", "foco", "canc", "hist"}) {
            int before = sentTexts().size();

            sendCallback(USER_CLARA, "v1:" + old + ":1");

            assertThat(sentTexts().subList(before, sentTexts().size()))
                    .as("botão antigo v1:%s:1", old)
                    .anySatisfy(text -> assertThat(text).contains("Esta opção não vale mais."));
        }
    }
}
