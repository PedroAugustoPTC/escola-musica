package br.com.felipeblaft.reports.inbound.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Registro de aula pelo Telegram (sequência 5.2, passos 1 a 5; estados #7 e #10). */
class LessonRegistrationConversationIT extends TelegramConversationBase {

    private static final long LIMIT = 50L * 1024 * 1024;

    private Student matteo;

    @BeforeEach
    void prepare() throws Exception {
        Teacher clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        scenario.student(clara, "Helena Prado", "Violino");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
    }

    private void chooseMatteo() throws Exception {
        sendText(USER_CLARA, "Matteo");
        API_BOTS.resetRequests();
    }

    /**
     * V15, FR-028 (US5.1): pela API interna o áudio nunca passa pelo orquestrador: a ação não traz
     * bytes, caminho de arquivo nem endereço de download, e ninguém pede o arquivo à API de bots no
     * recebimento. Só faz sentido no transporte interno.
     */
    @Test
    void audioInInternalTransportCarriesNoBytesPathNorAddress() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue("internal".equals(TRANSPORT));
        prepareTelegramFile("voz-ok", AudioNormalizationTest.AUDIO_WITH_SPEECH);
        chooseMatteo();

        sendVoice(USER_CLARA, "voz-ok", 1000);

        String actions =
                String.join(
                        "\n",
                        jdbc.queryForList(
                                "select payload::text from channel_action", String.class));
        assertThat(actions)
                .isNotBlank()
                .doesNotContain(properties.telegram().filesDir().toString())
                .doesNotContain(
                        "file_path", "file_id", ".oga", "http://", "https://", "AUDIO-DE-AULA");
        assertThat(lastMessage().get("text").asString()).startsWith("Áudio recebido");
        API_BOTS.verify(0, postRequestedFor(urlPathMatching(".*/getFile")));
    }

    private int jobs() {
        return jdbc.queryForObject("select count(*) from processing_job", Integer.class);
    }

    private Map<String, Object> conversation() {
        return jdbc.queryForMap("select * from telegram_link");
    }

    @Test
    void registerLessonAsksForStudentAndThenAudio() throws Exception {
        sendCallback(USER_CLARA, "v1:reg:");
        assertThat(lastMessage().get("text").asString()).isEqualTo("Para qual aluno?");
        assertThat(buttons(lastMessage())).contains("v1:aluno:0", "v1:aluno:1");

        sendCallback(USER_CLARA, "v1:aluno:1");

        assertThat(lastMessage().get("text").asString())
                .isEqualTo(
                        "Aula de hoje, 21/09/2026, com Matteo Ferreira. Envie o áudio da aula (mensagem de voz ou arquivo, até 50 MB).");
        assertThat(conversation().get("selected_student_id")).isEqualTo(matteo.id());
    }

    @Test
    void audioWithSelectedStudentAsksConfirmationWithoutDownloadingAnything() throws Exception {
        chooseMatteo();

        sendVoice(USER_CLARA, "voz-1", 120_000);

        JsonNode message = lastMessage();
        assertThat(message.get("text").asString())
                .isEqualTo("Áudio recebido. Este áudio é da aula de Matteo Ferreira?");
        assertThat(buttons(message)).containsExactly("v1:confal:", "v1:trocal:");
        assertThat(buttonLabels(message))
                .containsExactly("Confirmar Matteo Ferreira", "Trocar aluno");
        assertThat(conversation().get("pending_audio_file_id")).isEqualTo("voz-1");
        assertThat(jobs()).isZero();
        API_BOTS.verify(0, postRequestedFor(urlPathMatching(".*/getFile")));
    }

    @Test
    void acceptsAudioVideoVideoNoteAndAudioDocument() throws Exception {
        chooseMatteo();
        List<String[]> types =
                List.of(
                        new String[] {"audio", "audio/mpeg"},
                        new String[] {"video", "video/mp4"},
                        new String[] {"video_note", "video/mp4"},
                        new String[] {"document", "audio/x-m4a"});
        for (String[] type : types) {
            API_BOTS.resetRequests();
            sendFile(USER_CLARA, type[0], "arq-" + type[0], type[1], 1000);
            assertThat(lastMessage().get("text").asString()).startsWith("Áudio recebido.");
            assertThat(conversation().get("pending_audio_file_id")).isEqualTo("arq-" + type[0]);
        }

        API_BOTS.resetRequests();
        sendFile(USER_CLARA, "document", "planilha", "application/pdf", 1000);
        assertThat(lastMessage().get("text").asString())
                .startsWith("Não consigo usar este tipo de mensagem aqui.");
    }

    @Test
    void audioAboveLimitIsRefusedWithoutDownloading() throws Exception {
        chooseMatteo();

        sendVoice(USER_CLARA, "grande", LIMIT + 1);

        assertThat(lastMessage().get("text").asString())
                .isEqualTo(
                        "O áudio enviado é maior que o limite permitido. Grave um trecho mais curto.");
        assertThat(conversation().get("pending_audio_file_id")).isNull();
        API_BOTS.verify(0, postRequestedFor(urlPathMatching(".*/getFile")));
    }

    @Test
    void newAudioReplacesPreviousAndExpiresIn30Minutes() throws Exception {
        chooseMatteo();
        sendVoice(USER_CLARA, "primeiro", 1000);
        sendVoice(USER_CLARA, "segundo", 1000);
        assertThat(conversation().get("pending_audio_file_id")).isEqualTo("segundo");

        clock.advance(Duration.ofMinutes(31));
        API_BOTS.resetRequests();
        sendText(USER_CLARA, "Helena");

        assertThat(sentTexts().getFirst())
                .isEqualTo(
                        "O áudio enviado não foi confirmado a tempo e foi descartado. Envie o áudio de novo.");
        assertThat(conversation().get("pending_audio_file_id")).isNull();
    }

    @Test
    void withoutSelectedStudentAudioAsksForStudentAndChoiceConfirms() throws Exception {
        prepareTelegramFile("sem-aluno", AudioNormalizationTest.AUDIO_WITH_SPEECH);

        sendVoice(USER_CLARA, "sem-aluno", 1000);
        assertThat(lastMessage().get("text").asString())
                .isEqualTo("Áudio recebido. De qual aluno é esta aula?");

        sendCallback(USER_CLARA, "v1:aluno:1");

        assertThat(jobs()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select student_id from processing_job", java.util.UUID.class))
                .isEqualTo(matteo.id());
        assertThat(conversation().get("pending_audio_file_id")).isNull();
    }

    @Test
    void switchStudentShowsList() throws Exception {
        chooseMatteo();
        sendVoice(USER_CLARA, "voz-troca", 1000);

        sendCallback(USER_CLARA, "v1:trocal:");

        assertThat(lastMessage().get("text").asString())
                .isEqualTo("Áudio recebido. De qual aluno é esta aula?");
        assertThat(buttons(lastMessage())).contains("v1:aluno:0", "v1:aluno:1");
    }

    @Test
    void confirmProcessesEditingSameMessageUpToQuestions() throws Exception {
        Path file = prepareTelegramFile("voz-ok", AudioNormalizationTest.AUDIO_WITH_SPEECH);
        chooseMatteo();
        sendVoice(USER_CLARA, "voz-ok", 1000);
        API_BOTS.resetRequests();

        sendCallback(USER_CLARA, "v1:confal:");

        // a mensagem dos botões vira a mensagem de etapa, editada no lugar
        JsonNode edit = calls("editMessageText").getFirst();
        assertThat(edit.get("message_id").asLong()).isEqualTo(55);
        assertThat(edit.get("text").asString()).isEqualTo("<i>Enviando o áudio…</i>");
        assertThat(
                        jdbc.queryForObject(
                                "select parameters->>'stepMessage' from processing_job",
                                String.class))
                .isEqualTo("55");

        assertThat(scenario.processQueue()).isEqualTo(1);

        assertThat(editedTexts())
                .contains("<i>Transcrevendo…</i>", "<i>Estruturando o relatório…</i>");
        assertThat(calls("editMessageText"))
                .allSatisfy(e -> assertThat(e.get("message_id").asLong()).isEqualTo(55));
        String questions = lastMessage().get("text").asString();
        assertThat(questions)
                .startsWith(
                        "Faltam algumas informações. Responda numa única mensagem, digitando ou gravando:")
                .contains("\n1. O aluno estudou em casa durante a semana?")
                .contains("\n2. Que repertório o aluno deve preparar para a próxima aula?")
                .contains("Texto ou áudio enviado aqui será aplicado a este relatório.");
        assertThat(buttons(lastMessage())).containsExactly("v1:canc:", "v1:reg:");
        Map<String, Object> state = conversation();
        assertThat(state.get("focused_report_type")).isEqualTo("AULA");
        assertThat(state.get("focused_report_id"))
                .isEqualTo(
                        jdbc.queryForObject("select id from lesson_report", java.util.UUID.class));
        // FR-018: o áudio não fica nem no volume do Telegram nem no diretório temporário
        assertThat(file).doesNotExist();
        assertThat(temporaryFiles()).isEmpty();
    }

    @Test
    void audioWithoutSpeechDoesNotCreateReport() throws Exception {
        prepareTelegramFile(
                "silencio", AudioNormalizationTest.SILENCE.getBytes(StandardCharsets.UTF_8));
        chooseMatteo();
        sendVoice(USER_CLARA, "silencio", 1000);
        sendCallback(USER_CLARA, "v1:confal:");

        scenario.processQueue();

        assertThat(lastMessage().get("text").asString())
                .startsWith("Não conseguimos entender este áudio");
        assertThat(jdbc.queryForObject("select count(*) from lesson_report", Integer.class))
                .isZero();
    }

    @Test
    void studentWithOpenReportWarnsBeforeAskingForAudio() throws Exception {
        prepareTelegramFile("primeiro", AudioNormalizationTest.AUDIO_WITH_SPEECH);
        chooseMatteo();
        sendVoice(USER_CLARA, "primeiro", 1000);
        sendCallback(USER_CLARA, "v1:confal:");
        scenario.processQueue();
        sendText(USER_CLARA, "registrar aula");
        API_BOTS.resetRequests();

        sendText(USER_CLARA, "Matteo");

        assertThat(lastMessage().get("text").asString())
                .startsWith("Você tem um relatório em aberto.")
                .contains("pelo Telegram");
        assertThat(buttons(lastMessage())).containsExactly("v1:cont:", "v1:desc:");
        assertThat(jobs()).isEqualTo(1);
    }

    @Test
    void cancelWithoutFocusedReportDiscardsPendingAudio() throws Exception {
        chooseMatteo();
        sendVoice(USER_CLARA, "descartavel", 1000);

        sendText(USER_CLARA, "cancelar");

        assertThat(lastMessage().get("text").asString())
                .isEqualTo("O áudio enviado foi descartado.");
        assertThat(conversation().get("pending_audio_file_id")).isNull();
    }

    private List<Path> temporaryFiles() throws Exception {
        Path directory = properties.audio().tempDir();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files.toList();
        }
    }
}
