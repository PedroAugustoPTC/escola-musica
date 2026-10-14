package br.com.felipeblaft.reports.outbound.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.support.OrchestratorDeliveryBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase;
import br.com.felipeblaft.reports.usecase.channel.SaveChannelActionsUseCase.NewAction;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * V7, C10, FR-007b, FR-017: o backend decide a compensação quando uma edição falha por mensagem não
 * editável; "message is not modified" é sucesso; falha de edição de etapa regrava o id da mensagem
 * pela compensação; código desconhecido vira {@code ERRO_TELEGRAM} sem derrubar as demais
 * confirmações.
 */
class CompensationConfirmationIT extends OrchestratorDeliveryBase {

    @Autowired SaveChannelActionsUseCase save;

    private void stubTelegram(String method, int status, String body) {
        API_BOTS.stubFor(
                post(urlPathMatching(".*/" + method))
                        .willReturn(
                                aResponse()
                                        .withStatus(status)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(body)));
    }

    private MvcResult confirm(String resultsJson) throws Exception {
        return mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/internal/v1/channel/telegram/confirmations")
                                .header("X-N8N-Service-Token", TOKEN_SERVICE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":\"v1\",\"results\":[" + resultsJson + "]}"))
                .andReturn();
    }

    /** Edição proativa de uma mensagem do professor, com a alternativa de mensagem nova. */
    private String publishEdit() {
        save.run(
                "p:edit:1",
                ChannelActionOrigin.PROACTIVE,
                null,
                List.of(
                        new NewAction(
                                "p:edit:1:0",
                                ChannelActionType.EDIT,
                                USER_CLARA,
                                clara.id(),
                                null,
                                "{\"type\":\"EDIT\",\"chatId\":"
                                        + USER_CLARA
                                        + ",\"messageId\":900,\"text\":\"Texto da edição\","
                                        + "\"keyboard\":{\"rows\":[]}}",
                                "{\"type\":\"MESSAGE\",\"chatId\":"
                                        + USER_CLARA
                                        + ",\"text\":\"Texto da edição\"}")));
        return "p:edit:1:0";
    }

    @Test
    void nonEditableEditGeneratesCompensationWithSameContentDeliveredOnceWithoutRepeatingEdit()
            throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        stubTelegram(
                "editMessageText",
                400,
                "{\"ok\":false,\"description\":\"Bad Request: message to edit not found\"}");
        String edit = publishEdit();

        drain(6);

        assertThat(state(edit)).isEqualTo("FAILED");
        assertThat(
                        jdbc.queryForObject(
                                "select reason_code from channel_action where id = ?",
                                String.class,
                                edit))
                .isEqualTo("MESSAGE_NOT_EDITABLE");
        assertThat(state(edit + "-c")).isEqualTo("EXECUTED");
        assertThat(
                        jdbc.queryForObject(
                                "select batch_id from channel_action where id = ?",
                                String.class,
                                edit + "-c"))
                .isEqualTo("c:" + edit);
        assertThat(calls("editMessageText")).hasSize(1);
        assertThat(sentTexts()).containsExactly("Texto da edição");
        // a compensação foi entregue uma só vez, como lote próprio
        assertThat(deliveries()).hasSize(2);
        assertThat(body(deliveries().get(1)).path("origin").asString()).isEqualTo("COMPENSATION");
        assertThat(body(deliveries().get(1)).get("actions").get(0).path("actionId").asString())
                .isEqualTo(edit + "-c");
    }

    /** PDF proativo de um documento que não existe mais, com o aviso e o botão como alternativa. */
    private String publishUnavailableDocument() {
        save.run(
                "p:pdf:1",
                ChannelActionOrigin.PROACTIVE,
                null,
                List.of(
                        new NewAction(
                                "p:pdf:1:0",
                                ChannelActionType.DOCUMENT,
                                USER_CLARA,
                                clara.id(),
                                null,
                                "{\"type\":\"DOCUMENT\",\"chatId\":"
                                        + USER_CLARA
                                        + ",\"documentId\":\""
                                        + "Z".repeat(43)
                                        + "\",\"fileName\":\"report.pdf\","
                                        + "\"contentType\":\"application/pdf\"}",
                                "{\"type\":\"MESSAGE\",\"chatId\":"
                                        + USER_CLARA
                                        + ",\"text\":\"Não consegui enviar o PDF agora.\","
                                        + "\"keyboard\":{\"rows\":[[{\"label\":\"Ver PDF\","
                                        + "\"data\":\"v1:pdf:\"}]]}}")));
        return "p:pdf:1:0";
    }

    @Test
    void unavailablePdfDeliversNoticeWithButtonOnlyOnceWithoutResendingDocument() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        String document = publishUnavailableDocument();

        drain(6);

        assertThat(state(document)).isEqualTo("FAILED");
        assertThat(
                        jdbc.queryForObject(
                                "select reason_code from channel_action where id = ?",
                                String.class,
                                document))
                .isEqualTo("DOCUMENT_UNAVAILABLE");
        assertThat(state(document + "-c")).isEqualTo("EXECUTED");
        assertThat(sentTexts()).containsExactly("Não consegui enviar o PDF agora.");
        assertThat(buttons(lastMessage())).containsExactly("v1:pdf:");
        assertThat(calls("sendDocument")).isEmpty();
        // a compensação é um lote próprio, entregue uma só vez e sem o documento
        assertThat(deliveries()).hasSize(2);
        assertThat(body(deliveries().get(1)).path("origin").asString()).isEqualTo("COMPENSATION");
        assertThat(body(deliveries().get(1)).get("actions").get(0).path("actionId").asString())
                .isEqualTo(document + "-c");
        assertThat(body(deliveries().get(1)).toString()).doesNotContain("documentId");
    }

    @Test
    void pdfCompensationThatFailsDoesNotGenerateAnother() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        stubTelegram(
                "sendMessage",
                403,
                "{\"ok\":false,\"description\":\"Forbidden: bot was blocked by the user\"}");
        String document = publishUnavailableDocument();

        drain(8);

        assertThat(state(document + "-c")).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void unmodifiedMessageIsExecutedWithoutCompensation() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        stubTelegram(
                "editMessageText",
                400,
                "{\"ok\":false,\"description\":\"Bad Request: message is not modified\"}");
        String edit = publishEdit();

        drain(6);

        assertThat(state(edit)).isEqualTo("EXECUTED");
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isEqualTo(1);
        assertThat(sentTexts()).isEmpty();
    }

    @Test
    void compensationThatFailsDoesNotGenerateAnother() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        stubTelegram(
                "editMessageText",
                400,
                "{\"ok\":false,\"description\":\"Bad Request: message to edit not found\"}");
        stubTelegram(
                "sendMessage",
                403,
                "{\"ok\":false,\"description\":\"Forbidden: bot was blocked by the user\"}");
        String edit = publishEdit();

        drain(8);

        assertThat(state(edit + "-c")).isEqualTo("FAILED");
        assertThat(
                        jdbc.queryForObject(
                                "select reason_code from channel_action where id = ?",
                                String.class,
                                edit + "-c"))
                .isEqualTo("CHAT_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from channel_action", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void stepEditFailureSendsNewMessageAndRewritesIdInJob() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        clara.id(),
                        matteo.id(),
                        null,
                        null,
                        Channel.TELEGRAM,
                        Medium.AUDIO,
                        JobStep.UPLOADING,
                        clock.now());
        job.defineParameter(ProcessingJob.STEP_MESSAGE, "55");
        queue.enqueue(job);
        job.advance(JobStep.TRANSCRIBING);
        // a mensagem de etapa 55 foi apagada pelo professor: a edição vai falhar
        stubTelegram(
                "editMessageText",
                400,
                "{\"ok\":false,\"description\":\"Bad Request: message to edit not found\"}");
        stubTelegram("sendMessage", 200, "{\"ok\":true,\"result\":{\"message_id\":777}}");

        notification.notifyStep(job);
        drain(6);

        String step = actionIds().get(0);
        assertThat(step).startsWith("p:step:" + job.id() + ":TRANSCRIBING:");
        assertThat(state(step)).isEqualTo("FAILED");
        assertThat(body(deliveries().get(0)).get("actions").get(0).path("editMessageId").asLong())
                .isEqualTo(55L);
        assertThat(state(step + "-c")).isEqualTo("EXECUTED");
        assertThat(stepMessage(job.id())).isEqualTo("777");

        // a etapa seguinte edita a mensagem NOVA, sem abrir outra
        stubTelegram("editMessageText", 200, "{\"ok\":true,\"result\":true}");
        API_BOTS.resetRequests();
        job.advance(JobStep.STRUCTURING);
        notification.notifyStep(job);
        drain(6);

        JsonNode next = body(deliveries().get(deliveries().size() - 1));
        assertThat(next.get("actions").get(0).path("editMessageId").asLong()).isEqualTo(777L);
        assertThat(calls("sendMessage")).isEmpty();
        assertThat(calls("editMessageText")).hasSize(1);
        assertThat(calls("editMessageText").get(0).path("message_id").asLong()).isEqualTo(777L);
    }

    private String stepMessage(UUID job) {
        return jdbc.queryForObject(
                "select parameters->>'stepMessage' from processing_job where id = ?",
                String.class,
                job);
    }

    @Test
    void executedStepWithoutMessageIdIs400AndDoesNotFinalizeButAppliesSiblings() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        clara.id(),
                        matteo.id(),
                        null,
                        null,
                        Channel.TELEGRAM,
                        Medium.AUDIO,
                        JobStep.TRANSCRIBING,
                        clock.now());
        generateNoticeOf(helena);
        queue.enqueue(job); // depois do processamento da fila: este trabalho não tem áudio
        notification.notifyStep(job);
        task.dispatch();
        task.dispatch();
        List<String> ids = actionIds();
        String step = ids.stream().filter(i -> i.startsWith("p:step:")).findFirst().orElseThrow();
        String notice =
                ids.stream().filter(i -> i.startsWith("p:result:")).findFirst().orElseThrow();

        MvcResult result =
                confirm(
                        "{\"actionId\":\""
                                + step
                                + "\",\"outcome\":\"EXECUTED\"},"
                                + "{\"actionId\":\""
                                + notice
                                + "\",\"outcome\":\"EXECUTED\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(state(step)).isNotEqualTo("EXECUTED");
        assertThat(state(notice)).isEqualTo("EXECUTED");
    }

    @Test
    void unknownCodeBecomesTelegramErrorWithoutLosingSiblingConfirmations() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        generateNoticeOf(helena);
        task.dispatch();
        String first = actionIds().get(0);

        MvcResult result =
                confirm(
                        "{\"actionId\":\""
                                + first
                                + "\",\"outcome\":\"FAILED\",\"reasonCode\":\"NEW_CODE_FROM_N8N\"},"
                                + "{\"actionId\":\"u1-9\",\"outcome\":\"EXECUTED\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("accepted").asInt()).isEqualTo(1);
        assertThat(body.path("ignored")).hasSize(1);
        assertThat(
                        jdbc.queryForObject(
                                "select reason_code from channel_action where id = ?",
                                String.class,
                                first))
                .isEqualTo("TELEGRAM_ERROR");
    }

    @Test
    void repeatedDivergentOrUnknownConfirmationGoesToIgnored() throws Exception {
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        generateNoticeOf(matteo);
        task.dispatch();
        String action = actionIds().get(0);

        String ok = "{\"actionId\":\"" + action + "\",\"outcome\":\"EXECUTED\"}";
        assertThat(
                        JSON.readTree(confirm(ok).getResponse().getContentAsString())
                                .path("accepted")
                                .asInt())
                .isEqualTo(1);
        // repetida idêntica: sem efeito; divergente e desconhecida: ignoradas
        JsonNode second =
                JSON.readTree(
                        confirm(
                                        ok
                                                + ",{\"actionId\":\""
                                                + action
                                                + "\",\"outcome\":\"FAILED\",\"reasonCode\":\"TELEGRAM_ERROR\"},"
                                                + "{\"actionId\":\"p:missing:none:0\",\"outcome\":\"EXECUTED\"}")
                                .getResponse()
                                .getContentAsString());
        assertThat(second.path("accepted").asInt()).isEqualTo(1);
        assertThat(second.path("ignored")).hasSize(2);
        assertThat(state(action)).isEqualTo("EXECUTED");
        assertThat(API_BOTS.findAll(postRequestedFor(urlPathMatching(".*/sendMessage")))).isEmpty();
    }
}
