package br.com.felipeblaft.reports.inbound.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Semestral pelo bot (sequência 5.4, estado #8). */
class SemesterConversationIT extends TelegramConversationBase {

    private Teacher clara;
    private Student matteo;

    @BeforeEach
    void prepare() throws Exception {
        clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 3, 10), "Escala de Dó");
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 5, 20), "Estudo nº 5");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
    }

    private void chooseMatteo() throws Exception {
        sendText(USER_CLARA, "semestral");
        sendText(USER_CLARA, "Matteo");
    }

    private Map<String, Object> conversation() {
        return jdbc.queryForMap("select * from telegram_link");
    }

    /** Gera o semestral do 1º semestre e chega à conferência. */
    private UUID generateInFirstSemester() throws Exception {
        chooseMatteo();
        sendCallback(USER_CLARA, "v1:period:1");
        sendCallback(USER_CLARA, "v1:generate:");
        scenario.processQueue();
        API_BOTS.resetRequests();
        return jdbc.queryForObject("select id from semester_report", UUID.class);
    }

    @Test
    void periodsWithStarAndInProgressAndPreview() throws Exception {
        chooseMatteo();

        JsonNode periods = lastMessage();
        assertThat(periods.get("text").asString())
                .startsWith("Escolha o semestre de Matteo Ferreira:")
                .contains("2º semestre 2026: 01/07/2026 a 31/12/2026")
                .contains("1º semestre 2026: 01/01/2026 a 30/06/2026");
        assertThat(buttonLabels(periods))
                .startsWith("★ 2º semestre 2026 · em andamento", "1º semestre 2026");
        assertThat(buttons(periods)).startsWith("v1:period:0", "v1:period:1");

        sendCallback(USER_CLARA, "v1:period:0");
        assertThat(lastMessage().get("text").asString())
                .startsWith("Nenhum relatório de aula aprovado neste período.");
        assertThat(buttons(lastMessage())).containsExactly("v1:back:");

        sendCallback(USER_CLARA, "v1:back:");
        assertThat(lastMessage().get("text").asString()).startsWith("Escolha o semestre");

        sendCallback(USER_CLARA, "v1:period:1");
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("O 1º semestre 2026 tem 2 relatório(s) de aula aprovado(s).");
        assertThat(buttons(lastMessage())).containsExactly("v1:generate:", "v1:back:");
    }

    @Test
    void generateShowsStepAndReviewWithPdf() throws Exception {
        chooseMatteo();
        sendCallback(USER_CLARA, "v1:period:1");
        API_BOTS.resetRequests();

        sendCallback(USER_CLARA, "v1:generate:");
        assertThat(editedTexts()).contains("<i>Consolidando as aulas do semestre…</i>");
        scenario.processQueue();

        String review =
                sentTexts().stream()
                        .filter(t -> t.contains("Confira o relatório"))
                        .findFirst()
                        .orElseThrow();
        assertThat(review)
                .contains("Aluno: Matteo Ferreira")
                .contains("Período: 1º semestre 2026")
                .contains("Versão 1")
                .contains("2 relatório(s) de aula considerado(s) nesta consolidação.")
                .contains("<b>1. Informações Gerais</b>")
                .contains("<b>10. Parecer Final</b>");
        API_BOTS.verify(1, postRequestedFor(urlPathMatching(".*/sendDocument")));
        assertThat(buttons(lastMessage())).contains("v1:approve:1", "v1:change:", "v1:cancel:");
        assertThat(conversation().get("focused_report_type")).isEqualTo("SEMESTER");
    }

    @Test
    void changeAndApproveAfterwardsDoesNotGenerateAgain() throws Exception {
        generateInFirstSemester();

        sendText(USER_CLARA, "Parecer: excelente semestre.");
        assertThat(lastMessage().get("text").asString()).isEqualTo("<i>Aplicando a alteração…</i>");
        scenario.processQueue();
        assertThat(String.join("\n", sentTexts()))
                .contains("Alteração aplicada. Confira a versão 2.")
                .contains("Entendi: “Parecer: excelente semestre.”")
                .contains("Parecer: excelente semestre.");

        sendText(USER_CLARA, "aprovar");
        sendText(USER_CLARA, "sim");
        assertThat(lastMessage().get("text").asString())
                .startsWith("Relatório aprovado em 21/09/2026");
        assertThat(jdbc.queryForObject("select status from semester_report", String.class))
                .isEqualTo("APPROVED");

        chooseMatteo();
        sendCallback(USER_CLARA, "v1:period:1");
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("O relatório semestral deste período já foi aprovado.");
    }

    @Test
    void openSemesterOffersContinueOrDiscard() throws Exception {
        generateInFirstSemester();
        chooseMatteo();
        sendCallback(USER_CLARA, "v1:period:1");
        assertThat(lastMessage().get("text").asString())
                .startsWith("Você tem um relatório em aberto.");
        assertThat(buttons(lastMessage())).containsExactly("v1:continue:", "v1:discard:");
        API_BOTS.resetRequests();

        sendCallback(USER_CLARA, "v1:continue:");
        assertThat(String.join("\n", sentTexts())).contains("Confira o relatório antes de aprovar");

        chooseMatteo();
        sendCallback(USER_CLARA, "v1:period:1");
        sendCallback(USER_CLARA, "v1:discard:");
        sendCallback(USER_CLARA, "v1:discard-ok:");

        assertThat(jdbc.queryForObject("select status from semester_report", String.class))
                .isEqualTo("CANCELLED");
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("O 1º semestre 2026 tem 2 relatório(s) de aula aprovado(s).");
    }

    @Test
    void openLessonReportsAskGenerateAnyway() throws Exception {
        jdbc.update(
                "update lesson_report set status = 'AWAITING_REVIEW', approved_at = null,"
                        + " approved_version = null, approval_channel = null"
                        + " where id = (select r.id from lesson_report r join lesson a on a.id = r.lesson_id"
                        + " where a.lesson_date = '2026-05-20')");
        chooseMatteo();

        sendCallback(USER_CLARA, "v1:period:1");

        assertThat(lastMessage().get("text").asString())
                .contains(
                        "Há 1 relatório(s) de aula em aberto neste período; eles não serão considerados.");
        assertThat(buttons(lastMessage())).containsExactly("v1:generate-yes:", "v1:back:");
        sendCallback(USER_CLARA, "v1:generate:");
        assertThat(lastMessage().get("text").asString())
                .isEqualTo(
                        "Há 1 relatório(s) de aula em aberto neste período; eles não serão considerados.");
        sendCallback(USER_CLARA, "v1:period:1");
        sendCallback(USER_CLARA, "v1:generate-yes:");
        scenario.processQueue();
        assertThat(
                        jdbc.queryForObject(
                                "select open_report_count from semester_report", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void cancelByButton() throws Exception {
        generateInFirstSemester();

        sendCallback(USER_CLARA, "v1:cancel:");
        sendCallback(USER_CLARA, "v1:cancel-ok:");

        assertThat(sentTexts()).contains("Relatório cancelado.");
        assertThat(jdbc.queryForObject("select status from semester_report", String.class))
                .isEqualTo("CANCELLED");
        assertThat(conversation().get("focused_report_id")).isNull();
    }
}
