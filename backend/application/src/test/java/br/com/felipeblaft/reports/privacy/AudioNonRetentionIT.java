package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-018 e quickstart V12: depois de sucesso e de falha, nos dois canais, não sobra áudio no
 * diretório temporário nem no volume do servidor local da API de bots, e o banco não guarda bytes
 * de áudio em nenhuma coluna.
 */
class AudioNonRetentionIT extends TelegramConversationBase {

    private static final String BRAND = "AUDIO-DE-AULA-FICTICIO";

    private Cookie session;
    private Student matteo;
    private Student olavo;

    @BeforeEach
    void prepare() throws Exception {
        // isola de arquivos que outros testes deixaram de propósito (ex.: áudio nunca baixado)
        for (Path directory :
                List.of(properties.audio().tempDir(), properties.telegram().filesDir())) {
            for (Path file : files(directory)) {
                Files.deleteIfExists(file);
            }
        }
        Teacher clara = scenario.clara();
        matteo = scenario.student(clara, "Matteo Mafra", "Piano");
        olavo = scenario.student(clara, "Olavo Torrente", "Violão");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        MvcResult input =
                mvc.perform(
                                post("/api/v1/sessao")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"codigo\":\"" + ScenarioIT.CODE_CLARA + "\"}"))
                        .andReturn();
        Cookie cookie = input.getResponse().getCookie("sessao");
        session = new Cookie(cookie.getName(), cookie.getValue());
    }

    private void sendThroughWeb(Student student, byte[] content) throws Exception {
        mvc.perform(
                multipart("/api/v1/alunos/{id}/relatorios-aula", student.id())
                        .file(new MockMultipartFile("audio", "aula.webm", "audio/webm", content))
                        .cookie(session)
                        .with(csrf().asHeader()));
        scenario.processQueue();
    }

    private void sendThroughBot(Student student, String fileId, byte[] content) throws Exception {
        sendText(USER_CLARA, "registrar aula");
        sendText(USER_CLARA, student.name());
        prepareTelegramFile(fileId, content);
        sendVoice(USER_CLARA, fileId, content.length);
        sendCallback(USER_CLARA, "v1:confal:");
        scenario.processQueue();
    }

    private static List<Path> files(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    private void withoutRetainedAudio() throws Exception {
        assertThat(files(properties.audio().tempDir())).as("diretório temporário").isEmpty();
        assertThat(files(properties.telegram().filesDir()))
                .as("volume do telegram-bot-api")
                .isEmpty();
    }

    @Test
    void successInBothChannelsLeavesNoAudio() throws Exception {
        sendThroughWeb(matteo, AudioNormalizationTest.AUDIO_WITH_SPEECH);
        sendThroughBot(olavo, "voz-sucesso", AudioNormalizationTest.AUDIO_WITH_SPEECH);

        assertThat(
                        jdbc.queryForList(
                                "select status from lesson_report order by created_at",
                                String.class))
                .containsExactly("AGUARDANDO_RESPOSTAS", "AGUARDANDO_RESPOSTAS");
        withoutRetainedAudio();
    }

    @Test
    void failureInBothChannelsLeavesNoAudio() throws Exception {
        sendThroughWeb(matteo, AudioNormalizationTest.SILENCE.getBytes(StandardCharsets.UTF_8));
        sendThroughWeb(olavo, AudioNormalizationTest.INVALID.getBytes(StandardCharsets.UTF_8));
        sendThroughBot(
                matteo,
                "voz-silencio",
                AudioNormalizationTest.SILENCE.getBytes(StandardCharsets.UTF_8));
        sendThroughBot(
                olavo,
                "voz-invalida",
                AudioNormalizationTest.INVALID.getBytes(StandardCharsets.UTF_8));

        assertThat(jdbc.queryForObject("select count(*) from lesson", Integer.class)).isZero();
        withoutRetainedAudio();
    }

    @Test
    void databaseDoesNotKeepAudioBytes() throws Exception {
        sendThroughWeb(matteo, AudioNormalizationTest.AUDIO_WITH_SPEECH);
        sendThroughBot(olavo, "voz-banco", AudioNormalizationTest.AUDIO_WITH_SPEECH);

        List<Map<String, Object>> binary =
                jdbc.queryForList(
                        "select table_name, column_name from information_schema.columns"
                                + " where table_schema = 'public' and data_type = 'bytea'");
        assertThat(binary)
                .extracting(c -> c.get("table_name") + "." + c.get("column_name"))
                .containsExactly("student.cpf_encrypted");

        List<String> withBrand = new ArrayList<>();
        for (Map<String, Object> column :
                jdbc.queryForList(
                        "select table_name, column_name from information_schema.columns"
                                + " where table_schema = 'public'"
                                + " and data_type in ('text', 'character varying', 'jsonb')"
                                + " and table_name <> 'flyway_schema_history'")) {
            String table = (String) column.get("table_name");
            String name = (String) column.get("column_name");
            Integer quantity =
                    jdbc.queryForObject(
                            "select count(*) from \""
                                    + table
                                    + "\" where \""
                                    + name
                                    + "\"::text like ?",
                            Integer.class,
                            "%" + BRAND + "%");
            if (quantity != null && quantity > 0) {
                withBrand.add(table + "." + name);
            }
        }
        assertThat(withBrand).as("colunas com o conteúdo do áudio").isEmpty();
    }
}
