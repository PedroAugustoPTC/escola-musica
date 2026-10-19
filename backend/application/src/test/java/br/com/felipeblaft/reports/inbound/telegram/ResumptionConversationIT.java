package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ConversationState;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RequestLessonReportChangeUseCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Retomada entre canais pelo bot (sequência 5.3, US3): relatório iniciado na Web. */
class ResumptionConversationIT extends TelegramConversationBase {

    @Autowired private RegisterLessonByAudioUseCase register;
    @Autowired private AnswerQuestionsUseCase answer;
    @Autowired private RequestLessonReportChangeUseCase change;

    private Teacher clara;
    private Student matteo;

    @BeforeEach
    void prepare() throws Exception {
        clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
    }

    private TemporaryFile webAudio() throws Exception {
        Path directory = properties.audio().tempDir();
        Files.createDirectories(directory);
        Path file = directory.resolve("web-" + UUID.randomUUID() + ".webm");
        Files.write(file, AudioNormalizationTest.AUDIO_WITH_SPEECH);
        return new TemporaryFile(file, Files.size(file), "audio/webm");
    }

    private UUID reportId() {
        return jdbc.queryForObject(
                "select id from lesson_report where status <> 'CANCELLED'", UUID.class);
    }

    /** Relatório iniciado na Web e deixado na conferência (versão 1). */
    private UUID reportInReviewThroughWeb() throws Exception {
        register.run(clara.id(), matteo.id(), webAudio(), Channel.WEB);
        scenario.processQueue();
        answer.answerText(clara.id(), reportId(), "Estudou bem.", Channel.WEB);
        scenario.processQueue();
        API_BOTS.resetRequests();
        return reportId();
    }

    @Test
    void choosingStudentWarnsOfOpenReportWithChannelAndDate() throws Exception {
        reportInReviewThroughWeb();

        sendText(USER_CLARA, "registrar aula");
        sendText(USER_CLARA, "Matteo");

        assertThat(lastMessage().get("text").asString())
                .isEqualTo(
                        "Você tem um relatório em aberto. Este aluno já tem um relatório iniciado pelo portal e ainda não concluído (última alteração em 21/09/2026 às 10:00).");
        assertThat(buttons(lastMessage())).containsExactly("v1:continue:", "v1:discard:");
    }

    @Test
    @ConversationState({4})
    void continueRepresentsReviewInCurrentVersion() throws Exception {
        UUID id = reportInReviewThroughWeb();
        change.changeByText(clara.id(), id, "Diga que a aula foi ótima.", 1, Channel.WEB);
        scenario.processQueue();
        sendText(USER_CLARA, "registrar aula");
        sendText(USER_CLARA, "Matteo");
        API_BOTS.resetRequests();

        sendCallback(USER_CLARA, "v1:continue:");

        assertThat(String.join("\n", sentTexts()))
                .contains("Confira o relatório antes de aprovar")
                .contains("Versão 2");
        assertThat(buttons(lastMessage())).contains("v1:approve:2");
        assertThat(jdbc.queryForObject("select focused_report_id from telegram_link", UUID.class))
                .isEqualTo(id);
        assertThat(
                        jdbc.queryForObject(
                                "select presented_version from telegram_link", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @ConversationState({3})
    void continueWithPendingQuestionsShowsQuestions() throws Exception {
        register.run(clara.id(), matteo.id(), webAudio(), Channel.WEB);
        scenario.processQueue();
        sendText(USER_CLARA, "registrar aula");
        sendText(USER_CLARA, "Matteo");
        API_BOTS.resetRequests();

        sendText(USER_CLARA, "continuar");

        assertThat(lastMessage().get("text").asString()).startsWith("Faltam algumas informações.");
    }

    @Test
    @ConversationState({5})
    void registrationStillInTranscriptionReportsStep() throws Exception {
        register.run(clara.id(), matteo.id(), webAudio(), Channel.WEB);
        sendText(USER_CLARA, "registrar aula");
        sendText(USER_CLARA, "Matteo");
        API_BOTS.resetRequests();

        sendCallback(USER_CLARA, "v1:continue:");

        assertThat(lastMessage().get("text").asString())
                .startsWith("Este relatório ainda está sendo processado");
        scenario.processQueue();
    }

    @Test
    @ConversationState({2})
    void discardAsksConfirmationCancelsAndAsksForNewAudio() throws Exception {
        reportInReviewThroughWeb();
        sendText(USER_CLARA, "registrar aula");
        sendText(USER_CLARA, "Matteo");

        sendCallback(USER_CLARA, "v1:discard:");
        assertThat(lastMessage().get("text").asString())
                .isEqualTo(
                        "Descartar o relatório em aberto? Ele deixará de aparecer para você e não poderá ser recuperado.");
        assertThat(buttons(lastMessage())).containsExactly("v1:discard-ok:", "v1:home:");
        API_BOTS.resetRequests();

        sendCallback(USER_CLARA, "v1:discard-ok:");

        assertThat(sentTexts()).contains("Relatório cancelado.");
        assertThat(lastMessage().get("text").asString())
                .startsWith("Aula de hoje, 21/09/2026, com Matteo Ferreira.");
        assertThat(jdbc.queryForObject("select status from lesson_report", String.class))
                .isEqualTo("CANCELLED");
    }

    @Test
    @ConversationState({7})
    void discardWithValidPendingAudioReusesAudio() throws Exception {
        register.run(clara.id(), matteo.id(), webAudio(), Channel.WEB);
        scenario.processQueue();
        prepareTelegramFile("reused", AudioNormalizationTest.AUDIO_WITH_SPEECH);
        sendVoice(USER_CLARA, "reused", 1000);
        sendCallback(USER_CLARA, "v1:student:0");
        assertThat(lastMessage().get("text").asString())
                .startsWith("Você tem um relatório em aberto.");

        sendCallback(USER_CLARA, "v1:discard:");
        sendText(USER_CLARA, "sim");

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from processing_job where type = 'LESSON_REGISTRATION' and status = 'PENDING'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select parameters->>'channelFile' from processing_job where status = 'PENDING'",
                                String.class))
                .isEqualTo("reused");
        scenario.processQueue();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from lesson_report where status = 'AWAITING_ANSWERS'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void homeOffersContinuingOpenReport() throws Exception {
        UUID id = reportInReviewThroughWeb();

        sendText(USER_CLARA, "início");

        assertThat(buttons(lastMessage())).contains("v1:focus:a" + id);
        assertThat(buttonLabels(lastMessage())).contains("Continuar: Matteo Ferreira (21/09/2026)");
    }
}
