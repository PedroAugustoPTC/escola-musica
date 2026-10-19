package br.com.felipeblaft.reports.outbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.outbound.queue.DelayNoticeTask;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RequestLessonReportChangeUseCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Avisos do bot (FR-021, contracts/telegram-conversa.md §6) para trabalhos pedidos em cada canal.
 */
class TelegramNotificationAdapterIT extends TelegramConversationBase {

    @Autowired private RegisterLessonByAudioUseCase register;
    @Autowired private AnswerQuestionsUseCase answer;
    @Autowired private RequestLessonReportChangeUseCase change;
    @Autowired private DelayNoticeTask delayNotice;

    private Teacher clara;
    private Student matteo;

    @BeforeEach
    void prepare() {
        clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
    }

    private TemporaryFile webAudio() throws Exception {
        Path directory = properties.audio().tempDir();
        Files.createDirectories(directory);
        Path file = directory.resolve("web-" + UUID.randomUUID() + ".webm");
        Files.write(file, AudioNormalizationTest.AUDIO_WITH_SPEECH);
        return new TemporaryFile(file, Files.size(file), "audio/webm");
    }

    private UUID reportId() {
        return jdbc.queryForObject("select id from lesson_report", UUID.class);
    }

    @Test
    void webJobWithLinkGeneratesShortNoticeWithoutChangingFocus() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);

        register.run(clara.id(), matteo.id(), webAudio(), Channel.WEB);
        scenario.processQueue();

        JsonNode notice = lastMessage();
        assertThat(notice.get("text").asString())
                .isEqualTo("O relatório de Matteo Ferreira tem perguntas pendentes.");
        assertThat(buttonLabels(notice)).containsExactly("Continuar no chat");
        assertThat(buttons(notice)).containsExactly("v1:focus:a" + reportId());
        assertThat(editedTexts()).isEmpty();
        assertThat(jdbc.queryForObject("select focused_report_id from telegram_link", UUID.class))
                .isNull();

        API_BOTS.resetRequests();
        answer.answerText(clara.id(), reportId(), "Estudou bem.", Channel.WEB);
        scenario.processQueue();
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("O relatório de Matteo Ferreira está pronto para conferência.");

        API_BOTS.resetRequests();
        change.changeByText(clara.id(), reportId(), "Diga que a aula foi ótima.", 1, Channel.WEB);
        scenario.processQueue();
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("O relatório de Matteo Ferreira tem uma nova versão (2).");
        assertThat(jdbc.queryForObject("select focused_report_id from telegram_link", UUID.class))
                .isNull();
    }

    @Test
    void webJobWithoutLinkSendsNothing() throws Exception {
        register.run(clara.id(), matteo.id(), webAudio(), Channel.WEB);

        scenario.processQueue();

        assertThat(calls("sendMessage")).isEmpty();
        assertThat(jdbc.queryForObject("select status from lesson_report", String.class))
                .isEqualTo("AWAITING_ANSWERS");
    }

    @Test
    void telegramJobWarnsItIsTakingLongOnceAt3Minutes() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        sendText(USER_CLARA, "Matteo");
        sendVoice(USER_CLARA, "slow", 1000);
        sendCallback(USER_CLARA, "v1:confirm-student:");
        API_BOTS.resetRequests();

        clock.advance(Duration.ofMinutes(2));
        delayNotice.verify();
        assertThat(calls("sendMessage")).isEmpty();

        clock.advance(Duration.ofMinutes(1));
        delayNotice.verify();
        delayNotice.verify();

        assertThat(sentTexts())
                .containsExactly(
                        "Está demorando mais que o normal. O resultado será entregue assim que ficar pronto; você pode sair e voltar depois.");
        assertThat(
                        jdbc.queryForObject(
                                "select delay_notice_sent from processing_job", Boolean.class))
                .isTrue();
    }

    @Test
    void slowWebJobDoesNotSendNoticeThroughBot() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        register.run(clara.id(), matteo.id(), webAudio(), Channel.WEB);

        clock.advance(Duration.ofMinutes(4));
        delayNotice.verify();

        assertThat(calls("sendMessage")).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select delay_notice_sent from processing_job", Boolean.class))
                .isTrue();
        scenario.processQueue();
    }
}
