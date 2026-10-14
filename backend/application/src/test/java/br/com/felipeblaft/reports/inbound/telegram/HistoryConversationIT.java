package br.com.felipeblaft.reports.inbound.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Histórico pelo bot (sequência 5.5, estado #9). */
class HistoryConversationIT extends TelegramConversationBase {

    private Teacher clara;
    private Student matteo;

    @BeforeEach
    void prepare() throws Exception {
        clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
    }

    @Test
    void emptyHistoryOffersRegisteringLesson() throws Exception {
        sendCallback(USER_CLARA, "v1:history:");
        sendText(USER_CLARA, "Matteo");

        assertThat(lastMessage().get("text").asString())
                .isEqualTo("Este aluno ainda não tem relatórios aprovados.");
        assertThat(buttons(lastMessage())).containsExactly("v1:register:");
    }

    @Test
    void paginatedListWithMoreAndOfficialPdfOfItem() throws Exception {
        for (int day = 1; day <= 12; day++) {
            scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 3, day), "Aula " + day);
        }
        sendText(USER_CLARA, "histórico");
        sendText(USER_CLARA, "Matteo");

        JsonNode list = lastMessage();
        assertThat(list.get("text").asString())
                .startsWith("<b>Histórico de Matteo Ferreira</b>")
                .contains("\n1. Aula de 12/03/2026 · Aprovado em 21/09/2026 às 10:00")
                .contains("\n10. Aula de 03/03/2026")
                .doesNotContain("11. ");
        assertThat(buttons(list))
                .contains("v1:history-item:0", "v1:history-item:9", "v1:history-more:1")
                .doesNotContain("v1:history-item:10");

        sendCallback(USER_CLARA, "v1:history-more:1");
        assertThat(lastMessage().get("text").asString())
                .contains("\n11. Aula de 02/03/2026")
                .contains("\n12. Aula de 01/03/2026");
        assertThat(buttons(lastMessage()))
                .contains("v1:history-item:10", "v1:history-item:11")
                .doesNotContain("v1:history-more:2");

        API_BOTS.resetRequests();
        sendCallback(USER_CLARA, "v1:history-item:11");
        API_BOTS.verify(
                1,
                postRequestedFor(urlPathMatching(".*/sendDocument"))
                        .withRequestBody(containing("relatorio-aula-2026-03-01.pdf")));
    }

    @Test
    void itemOutsideListExpired() throws Exception {
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 3, 1), "Aula");
        sendText(USER_CLARA, "histórico");
        sendText(USER_CLARA, "Matteo");

        sendCallback(USER_CLARA, "v1:history-item:5");

        assertThat(sentTexts()).contains("Esta opção não vale mais. Veja abaixo a etapa atual.");
    }
}
