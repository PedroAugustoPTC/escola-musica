package br.com.felipeblaft.reports.outbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.outbound.queue.DelayNoticeTask;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.OrchestratorDeliveryBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * V13, US3.1, US3.2, V12: com {@code entrega-proativa=ORQUESTRADOR} os avisos e resultados do
 * processamento saem pela outbox, com as mesmas mensagens e a mesma ordem do envio direto, o
 * relatório em foco quando pedido no Telegram e sem nenhuma chamada direta do backend à API de
 * bots. Os casos do modo {@code DIRETA} estão em {@link TelegramNotificationAdapterIT}, intactos.
 */
class TelegramNotificationOrchestratorIT extends OrchestratorDeliveryBase {

    @Autowired DelayNoticeTask delayNotice;
    @Autowired AnswerQuestionsUseCase answer;

    private UUID reportId() {
        return jdbc.queryForObject("select id from lesson_report", UUID.class);
    }

    private void withoutDirectBackendCall() {
        for (String method :
                List.of("sendMessage", "editMessageText", "sendDocument", "answerCallbackQuery")) {
            assertThat(calls(method)).as(method).isEmpty();
        }
    }

    private void reachQuestionsThroughTelegram() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        prepareTelegramFile("lesson", AudioNormalizationTest.AUDIO_WITH_SPEECH);
        sendText(USER_CLARA, "Matteo");
        sendVoice(USER_CLARA, "lesson", 1000);
        sendCallback(USER_CLARA, "v1:confirm-student:");
        API_BOTS.resetRequests();
        scenario.processQueue();
    }

    /** Métodos chamados à API de bots, na ordem cronológica. */
    private List<String> callOrder() {
        List<ServeEvent> events = new ArrayList<>(API_BOTS.getAllServeEvents());
        Collections.reverse(events);
        List<String> methods = new ArrayList<>();
        for (ServeEvent event : events) {
            String url = event.getRequest().getUrl();
            methods.add(url.substring(url.lastIndexOf('/') + 1));
        }
        return methods;
    }

    // ---- aviso curto de trabalho da Web (US3.2)

    @Test
    void webJobWithLinkGeneratesShortNoticeWithContinueInChatWithoutChangingFocus()
            throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);

        register.run(clara.id(), matteo.id(), webAudio(), Channel.WEB);
        scenario.processQueue();

        // o backend só gravou na outbox: nada foi enviado ao Telegram por ele
        withoutDirectBackendCall();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from channel_action where state = 'PENDING'",
                                Integer.class))
                .isEqualTo(1);

        drain(5);

        JsonNode notice = lastMessage();
        assertThat(notice.get("text").asString())
                .isEqualTo("O relatório de Matteo Ferreira tem perguntas pendentes.");
        assertThat(buttonLabels(notice)).containsExactly("Continuar no chat");
        assertThat(buttons(notice)).containsExactly("v1:focus:a" + reportId());
        assertThat(editedTexts()).isEmpty();
        assertThat(jdbc.queryForObject("select focused_report_id from telegram_link", UUID.class))
                .isNull();

        // resultado seguinte do mesmo relatório: "pronto para conferência"
        API_BOTS.resetRequests();
        answer.answerText(clara.id(), reportId(), "Estudou bem.", Channel.WEB);
        scenario.processQueue();
        drain(5);
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("O relatório de Matteo Ferreira está pronto para conferência.");
        assertThat(jdbc.queryForObject("select focused_report_id from telegram_link", UUID.class))
                .isNull();
    }

    @Test
    void webJobWithoutLinkPublishesNothing() throws Exception {
        register.run(clara.id(), matteo.id(), webAudio(), Channel.WEB);
        scenario.processQueue();
        drain(3);
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isZero();
        assertThat(deliveries()).isEmpty();
    }

    // ---- resultado de áudio do Telegram (US3.1)

    @Test
    void telegramQuestionsGoOutThroughOutboxWithSameStepEditsMessageAndFocus() throws Exception {
        reachQuestionsThroughTelegram();

        withoutDirectBackendCall();
        drain(10);

        assertThat(editedTexts())
                .contains("<i>Transcrevendo…</i>", "<i>Estruturando o relatório…</i>");
        assertThat(calls("editMessageText"))
                .allSatisfy(e -> assertThat(e.get("message_id").asLong()).isEqualTo(55));
        String questions = lastMessage().get("text").asString();
        assertThat(questions)
                .startsWith(
                        "Faltam algumas informações. Responda numa única mensagem, digitando ou gravando:")
                .contains("\n1. O aluno estudou em casa durante a semana?")
                .contains("Texto ou áudio enviado aqui será aplicado a este relatório.");
        assertThat(buttons(lastMessage())).containsExactly("v1:cancel:", "v1:register:");
        // relatório em foco, como no envio direto
        assertThat(jdbc.queryForObject("select focused_report_id from telegram_link", UUID.class))
                .isEqualTo(reportId());
        assertThat(
                        jdbc.queryForObject(
                                "select parameters->>'stepMessage' from processing_job",
                                String.class))
                .isEqualTo("55");
        // tudo foi confirmado pelo orquestrador
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from channel_action where state <> 'EXECUTED'",
                                Integer.class))
                .isZero();
    }

    @Test
    void reviewWithPdfGoesOutInTodaysOrderTextDocumentButtons() throws Exception {
        reachQuestionsThroughTelegram();
        drain(10);
        sendText(USER_CLARA, "Estudou bem; vai preparar a valsa.");
        API_BOTS.resetRequests();
        scenario.processQueue();

        withoutDirectBackendCall();
        drain(10);

        List<String> order = callOrder();
        int document = order.indexOf("sendDocument");
        assertThat(document).as("ordem %s", order).isGreaterThan(0);
        assertThat(order.stream().filter("sendDocument"::equals).count()).isEqualTo(1);
        // depois do documento só a mensagem com os botões
        assertThat(order.subList(document + 1, order.size())).containsExactly("sendMessage");
        JsonNode actions = lastMessage();
        assertThat(actions.get("text").asString())
                .contains("Texto ou áudio enviado aqui será aplicado a este relatório.");
        assertThat(buttons(actions))
                .containsExactly(
                        "v1:approve:1", "v1:change:", "v1:pdf:", "v1:cancel:", "v1:register:");
        assertThat(String.join("\n", sentTexts()))
                .contains("Respostas incorporadas ao relatório. Entendi:")
                .contains("Aluno: Matteo Ferreira");
        assertThat(jdbc.queryForObject("select status from lesson_report", String.class))
                .isEqualTo("AWAITING_REVIEW");
    }

    @Test
    void telegramProcessingFailureBringsRetryOptions() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        sendText(USER_CLARA, "Matteo");
        sendVoice(USER_CLARA, "slow", 1000); // sem arquivo na API: o áudio não é processável
        sendCallback(USER_CLARA, "v1:confirm-student:");
        API_BOTS.resetRequests();
        scenario.processQueue();

        withoutDirectBackendCall();
        drain(10);

        assertThat(lastMessage().get("text").asString()).isNotBlank();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from channel_action where state <> 'EXECUTED'",
                                Integer.class))
                .isZero();
    }

    // ---- demora

    @Test
    void telegramDelayNoticeGoesOutOnceThroughOutbox() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        sendText(USER_CLARA, "Matteo");
        sendVoice(USER_CLARA, "slow", 1000);
        sendCallback(USER_CLARA, "v1:confirm-student:");
        API_BOTS.resetRequests();

        clock.advance(Duration.ofMinutes(3));
        delayNotice.verify();
        delayNotice.verify();

        withoutDirectBackendCall();
        drain(5);
        assertThat(sentTexts())
                .containsExactly(
                        "Está demorando mais que o normal. O resultado será entregue assim que ficar pronto; você pode sair e voltar depois.");
    }
}
