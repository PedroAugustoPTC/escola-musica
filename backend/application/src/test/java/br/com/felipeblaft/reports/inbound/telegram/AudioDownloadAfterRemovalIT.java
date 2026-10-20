package br.com.felipeblaft.reports.inbound.telegram;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Áudio preservado depois da remoção do caminho antigo (feature 004, US7 cenário 4, FR-030): o
 * download continua sendo do backend, pelo servidor local da API de bots ({@code getFile} e volume
 * compartilhado), com o mesmo limite e as mesmas regras de descarte. Só o transporte do update e
 * das mensagens é do n8n; o arquivo nunca passa por ele.
 */
class AudioDownloadAfterRemovalIT extends TelegramConversationBase {

    private static final long LIMIT = 50L * 1024 * 1024;

    @BeforeEach
    void prepare() throws Exception {
        Teacher clara = scenario.clara();
        scenario.student(clara, "Matteo Ferreira", "Piano");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        sendText(USER_CLARA, "Matteo");
        API_BOTS.resetRequests();
    }

    @Test
    void audioIsDownloadedByBackendFromLocalBotsServerAndDiscardedAfterProcessing()
            throws Exception {
        Path file = prepareTelegramFile("voice-ok", AudioNormalizationTest.AUDIO_WITH_SPEECH);
        // o diretório temporário é compartilhado com os outros testes (uploads da Web ainda na fila
        // deixam arquivos lá);
        // o que este fluxo precisa provar é que ELE não deixa nada para trás
        List<Path> alreadyThere = temporaryFiles();
        sendVoice(USER_CLARA, "voice-ok", 1000);
        // receber o áudio não baixa nada: só guarda a referência até a confirmação
        API_BOTS.verify(exactly(0), postRequestedFor(urlPathMatching(".*/getFile")));
        sendCallback(USER_CLARA, "v1:confirm-student:");

        scenario.processQueue();

        API_BOTS.verify(exactly(1), postRequestedFor(urlPathMatching(".*/getFile")));
        assertThat(file).as("original no volume do bot é removido").doesNotExist();
        assertThat(temporaryFiles())
                .as("o fluxo do Telegram não deixa nada no diretório temporário")
                .isSubsetOf(alreadyThere);
        assertThat(jdbc.queryForObject("select count(*) from lesson_report", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void audioAboveLimitIsRefusedWithoutDownloading() throws Exception {
        sendVoice(USER_CLARA, "large", LIMIT + 1);

        assertThat(lastMessage().get("text").asString())
                .isEqualTo(
                        "O áudio enviado é maior que o limite permitido. Grave um trecho mais curto.");
        API_BOTS.verify(exactly(0), postRequestedFor(urlPathMatching(".*/getFile")));
        assertThat(
                        jdbc.queryForObject(
                                "select pending_audio_file_id from telegram_link", String.class))
                .isNull();
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
