package br.com.felipeblaft.reports.inbound.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Perguntas, conferência, alteração, aprovação e cancelamento no chat (estados #3 a #6, 5.2). */
class ReviewConversationIT extends TelegramConversationBase {

    private static final String REMINDER =
            "Texto ou áudio enviado aqui será aplicado a este relatório.";

    private UUID reportId;

    @BeforeEach
    void reachQuestions() throws Exception {
        Teacher clara = scenario.clara();
        scenario.student(clara, "Matteo Ferreira", "Piano");
        scenario.student(clara, "Helena Prado", "Violino");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        prepareTelegramFile("aula", AudioNormalizationTest.AUDIO_WITH_SPEECH);
        sendText(USER_CLARA, "Matteo");
        sendVoice(USER_CLARA, "aula", 1000);
        sendCallback(USER_CLARA, "v1:confal:");
        scenario.processQueue();
        reportId = jdbc.queryForObject("select id from lesson_report", UUID.class);
        API_BOTS.resetRequests();
    }

    private Map<String, Object> conversation() {
        return jdbc.queryForMap("select * from telegram_link");
    }

    private String status() {
        return jdbc.queryForObject("select status from lesson_report", String.class);
    }

    /** Responde às perguntas e processa, chegando à conferência da versão 1. */
    private void reachReview() throws Exception {
        sendText(USER_CLARA, "Estudou bem; vai preparar a valsa.");
        scenario.processQueue();
        API_BOTS.resetRequests();
    }

    @Test
    void answerByTextGoesStraightToProcessingAndReachesReviewWithPdf() throws Exception {
        sendText(USER_CLARA, "Estudou bem; vai preparar a valsa.");

        assertThat(lastMessage().get("text").asString())
                .isEqualTo("<i>Incorporando suas respostas…</i>");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from processing_job where type = 'RESPOSTA_PERGUNTAS' and status = 'PENDENTE'",
                                Integer.class))
                .isEqualTo(1);

        scenario.processQueue();

        List<String> texts = sentTexts();
        String review =
                texts.stream()
                        .filter(t -> t.contains("Confira o relatório antes de aprovar"))
                        .findFirst()
                        .orElseThrow();
        assertThat(review)
                .startsWith(
                        "Respostas incorporadas ao relatório. Entendi: “Estudou bem; vai preparar a valsa.”")
                .contains("Aluno: Matteo Ferreira")
                .contains("Professor: Clara Souza")
                .contains("Data da aula: 21/09/2026")
                .contains("Versão 1")
                .contains("<b>Conteúdos trabalhados</b>")
                .contains("<b>Observações</b>");
        API_BOTS.verify(1, postRequestedFor(urlPathMatching(".*/sendDocument")));
        JsonNode actions = lastMessage();
        assertThat(actions.get("text").asString()).contains(REMINDER);
        assertThat(buttons(actions))
                .containsExactly("v1:aprov:1", "v1:alt:", "v1:pdf:", "v1:canc:", "v1:reg:");
        assertThat(conversation().get("presented_version")).isEqualTo(1);
        assertThat(status()).isEqualTo("AGUARDANDO_CONFERENCIA");
    }

    @Test
    void answerByAudioDoesNotAskConfirmation() throws Exception {
        prepareTelegramFile("resposta", AudioNormalizationTest.AUDIO_WITH_SPEECH);

        sendVoice(USER_CLARA, "resposta", 1000);

        assertThat(lastMessage().get("text").asString())
                .isEqualTo("<i>Transcrevendo o pedido…</i>");
        assertThat(conversation().get("pending_audio_file_id")).isNull();
        scenario.processQueue();
        assertThat(status()).isEqualTo("AGUARDANDO_CONFERENCIA");
        assertThat(String.join("\n", sentTexts()))
                .contains("Respostas incorporadas ao relatório. Entendi:");
    }

    @Test
    void textDuringProcessingReportsStep() throws Exception {
        sendText(USER_CLARA, "Estudou bem.");
        API_BOTS.resetRequests();

        sendText(USER_CLARA, "mais uma coisa");

        assertThat(lastMessage().get("text").asString())
                .isEqualTo(
                        "Este relatório ainda está sendo processado (Incorporando suas respostas). Aguarde a conclusão.");
    }

    @Test
    void changeByTextGeneratesNewVersionWithUnderstoodText() throws Exception {
        reachReview();

        sendText(USER_CLARA, "Nas observações, diga que a aula foi ótima.");
        assertThat(lastMessage().get("text").asString()).isEqualTo("<i>Aplicando a alteração…</i>");
        assertThat(
                        jdbc.queryForObject(
                                "select parameters->>'baseVersion' from processing_job where type = 'ALTERACAO'",
                                String.class))
                .isEqualTo("1");
        scenario.processQueue();

        String review =
                sentTexts().stream()
                        .filter(t -> t.contains("Confira o relatório"))
                        .findFirst()
                        .orElseThrow();
        assertThat(review)
                .startsWith(
                        "Alteração aplicada. Confira a versão 2.\nEntendi: “Nas observações, diga que a aula foi ótima.”")
                .contains("Versão 2");
        assertThat(buttons(lastMessage())).contains("v1:aprov:2");
        assertThat(conversation().get("presented_version")).isEqualTo(2);
    }

    @Test
    void changeByAudioShowsWhatWasUnderstood() throws Exception {
        reachReview();
        prepareTelegramFile("pedido", AudioNormalizationTest.AUDIO_WITH_SPEECH);

        sendVoice(USER_CLARA, "pedido", 1000);
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("<i>Transcrevendo o pedido…</i>");
        scenario.processQueue();

        assertThat(String.join("\n", sentTexts()))
                .contains("Alteração aplicada. Confira a versão 2.")
                .contains("Entendi: “Hoje trabalhamos o estudo número cinco de Czerny");
    }

    @Test
    void exactNameOfOtherStudentAsksWhetherToSwitch() throws Exception {
        reachReview();

        sendText(USER_CLARA, "Helena Prado");

        JsonNode message = lastMessage();
        assertThat(message.get("text").asString())
                .isEqualTo(
                        "Você quer trocar para Helena Prado? O relatório de Matteo Ferreira continua em aberto.");
        assertThat(buttonLabels(message)).containsExactly("Confirmar Helena Prado", "Voltar");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from processing_job where type = 'ALTERACAO'",
                                Integer.class))
                .isZero();

        sendCallback(USER_CLARA, "v1:aluno:0");
        assertThat(lastMessage().get("text").asString())
                .startsWith("Aula de hoje, 21/09/2026, com Helena Prado.");
        assertThat(conversation().get("focused_report_id")).isNull();
        assertThat(status()).isEqualTo("AGUARDANDO_CONFERENCIA");
    }

    @Test
    void approveByKeywordAsksConfirmationCitingVersion() throws Exception {
        reachReview();

        sendText(USER_CLARA, "aprovar");
        JsonNode confirmation = lastMessage();
        assertThat(confirmation.get("text").asString())
                .isEqualTo(
                        "Aprovar a versão 1? O relatório entrará no histórico do aluno e não poderá mais ser alterado.");
        assertThat(buttons(confirmation)).containsExactly("v1:aprovok:1", "v1:inicio:");
        assertThat(buttonLabels(confirmation).getFirst()).isEqualTo("Sim, aprovar a versão 1");

        sendText(USER_CLARA, "sim");

        JsonNode approved = lastMessage();
        assertThat(approved.get("text").asString())
                .isEqualTo(
                        "Relatório aprovado em 21/09/2026 às 10:00. Ele já consta no histórico do aluno.");
        assertThat(buttonLabels(approved)).containsExactly("Registrar outra aula", "Histórico");
        assertThat(status()).isEqualTo("APROVADO");
        Map<String, Object> state = conversation();
        assertThat(state.get("focused_report_id")).isNull();
        assertThat(state.get("selected_student_id")).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from pdf_document where type = 'OFICIAL'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select approval_channel from lesson_report", String.class))
                .isEqualTo("TELEGRAM");
    }

    @Test
    void approveByButtonWithOldVersionShowsCurrent() throws Exception {
        reachReview();
        sendText(USER_CLARA, "Nas observações, diga que a aula foi ótima.");
        scenario.processQueue();
        API_BOTS.resetRequests();

        sendCallback(USER_CLARA, "v1:aprov:1");

        assertThat(sentTexts().getFirst())
                .isEqualTo(
                        "Esta versão está desatualizada: o relatório foi alterado e agora está na versão 2. Confira a versão atual antes de continuar.");
        assertThat(buttons(lastMessage())).contains("v1:aprov:2");

        sendCallback(USER_CLARA, "v1:aprov:2");
        sendCallback(USER_CLARA, "v1:aprovok:2");
        assertThat(status()).isEqualTo("APROVADO");
        assertThat(jdbc.queryForObject("select approved_version from lesson_report", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void confirmationButtonWithoutPendingConfirmationExpired() throws Exception {
        reachReview();

        sendCallback(USER_CLARA, "v1:aprovok:1");

        assertThat(sentTexts().getFirst())
                .isEqualTo("Esta opção não vale mais. Veja abaixo a etapa atual.");
        assertThat(status()).isEqualTo("AGUARDANDO_CONFERENCIA");
    }

    @Test
    void cancelByButtonWithConfirmationClearsFocusAndShowsHome() throws Exception {
        reachReview();

        sendCallback(USER_CLARA, "v1:canc:");
        assertThat(lastMessage().get("text").asString())
                .isEqualTo(
                        "Cancelar este relatório? Ele deixará de aparecer para você e não poderá ser recuperado.");
        sendCallback(USER_CLARA, "v1:cancok:");

        assertThat(sentTexts()).contains("Relatório cancelado.");
        assertThat(lastMessage().get("text").asString()).contains("Olá, Clara");
        assertThat(status()).isEqualTo("CANCELADO");
        assertThat(conversation().get("focused_report_id")).isNull();
    }

    @Test
    void otherTextGivesUpConfirmationAndWillBeAChange() throws Exception {
        reachReview();
        sendText(USER_CLARA, "cancelar");

        sendText(USER_CLARA, "Acrescente que o aluno chegou atrasado.");

        assertThat(lastMessage().get("text").asString()).isEqualTo("<i>Aplicando a alteração…</i>");
        assertThat(status()).isEqualTo("AGUARDANDO_CONFERENCIA");
    }

    @Test
    void pdfButtonResendsDocument() throws Exception {
        reachReview();

        sendCallback(USER_CLARA, "v1:pdf:");

        API_BOTS.verify(1, postRequestedFor(urlPathMatching(".*/sendDocument")));
    }

    @Test
    void processingFailureOffersRetry() throws Exception {
        jdbc.update(
                "update lesson_report set status = 'FALHA_PROCESSAMENTO', processing_step = null, failure_reason = 'TESTE'");

        sendText(USER_CLARA, "e agora?");
        assertThat(lastMessage().get("text").asString())
                .startsWith("Não foi possível concluir o relatório.");
        assertThat(buttons(lastMessage())).containsExactly("v1:tent:", "v1:canc:");

        sendCallback(USER_CLARA, "v1:tent:");

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from processing_job where type = 'NOVA_TENTATIVA'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(editedTexts()).contains("<i>Estruturando o relatório…</i>");
    }

    @Test
    void registerLessonClearsFocusWithoutCancelling() throws Exception {
        reachReview();

        sendText(USER_CLARA, "registrar aula");

        assertThat(lastMessage().get("text").asString()).isEqualTo("Para qual aluno?");
        assertThat(conversation().get("focused_report_id")).isNull();
        assertThat(status()).isEqualTo("AGUARDANDO_CONFERENCIA");
    }

    @Test
    void continueInChatPutsReportInFocus() throws Exception {
        reachReview();
        sendText(USER_CLARA, "início");
        assertThat(conversation().get("focused_report_id")).isNull();
        assertThat(buttons(lastMessage())).contains("v1:foco:a" + reportId);
        API_BOTS.resetRequests();

        sendCallback(USER_CLARA, "v1:foco:a" + reportId);

        assertThat(String.join("\n", sentTexts())).contains("Confira o relatório antes de aprovar");
        assertThat(conversation().get("focused_report_id")).isEqualTo(reportId);
    }
}
