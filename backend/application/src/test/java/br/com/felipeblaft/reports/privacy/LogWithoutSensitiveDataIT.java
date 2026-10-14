package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.outbound.queue.JobWorker;
import br.com.felipeblaft.reports.service.JobProcessorService;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.TelegramConversationBase;
import br.com.felipeblaft.reports.usecase.seed.LoadInitialRegistryUseCase;
import br.com.felipeblaft.reports.usecase.seed.SeedFile;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

/**
 * FR-055 e quickstart V12: com os logs da aplicação em DEBUG, nenhum evento (mensagem, MDC ou
 * exceção) traz transcrição, conteúdo do relatório, respostas, nome de aluno, CPF, código de
 * acesso, token de sessão nem o token do bot — nos fluxos de sucesso e de erro dos dois canais.
 */
class LogWithoutSensitiveDataIT extends TelegramConversationBase {

    private static final String RESPONSE = "Resposta sigilosa sobre a rotina de estudo";
    private static final String CHANGE = "Pedido sigiloso de alteração do texto";
    private static final String CPF = "52998224725";

    @Autowired private LoadInitialRegistryUseCase seed;
    @Autowired private JobWorker worker;

    @MockitoSpyBean private JobProcessorService processor;

    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Logger root;
    private Level rootLevel;
    private Logger application;
    private Level applicationLevel;

    @BeforeEach
    void captureLogs() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        application = (Logger) LoggerFactory.getLogger("br.com.felipeblaft");
        rootLevel = root.getLevel();
        applicationLevel = application.getLevel();
        root.setLevel(Level.INFO);
        application.setLevel(Level.DEBUG);
        events.start();
        root.addAppender(events);
    }

    @AfterEach
    void restoreLogs() {
        root.detachAppender(events);
        events.stop();
        root.setLevel(rootLevel);
        application.setLevel(applicationLevel);
    }

    private String logText() {
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : List.copyOf(events.list)) {
            text.append(event.getFormattedMessage()).append('\n');
            event.getMDCPropertyMap()
                    .forEach(
                            (key, value) ->
                                    text.append(key).append('=').append(value).append('\n'));
            if (event.getKeyValuePairs() != null) {
                event.getKeyValuePairs()
                        .forEach(
                                par ->
                                        text.append(par.key)
                                                .append('=')
                                                .append(par.value)
                                                .append('\n'));
            }
            if (event.getThrowableProxy() != null) {
                text.append(ThrowableProxyUtil.asString(event.getThrowableProxy())).append('\n');
            }
        }
        return text.toString();
    }

    private Cookie signIn(String code) throws Exception {
        MvcResult input =
                mvc.perform(
                                post("/api/v1/session")
                                        .with(csrf().asHeader())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"code\":\"" + code + "\"}"))
                        .andReturn();
        Cookie cookie = input.getResponse().getCookie("session");
        return cookie == null ? null : new Cookie(cookie.getName(), cookie.getValue());
    }

    private UUID report() {
        return jdbc.queryForObject(
                "select id from lesson_report order by created_at desc limit 1", UUID.class);
    }

    private void postJson(Cookie session, String path, String body) throws Exception {
        mvc.perform(
                post(path)
                        .cookie(session)
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    @Test
    void jobProcessingExceptionCarriesNoMessageCauseNorStackToLog() {
        doThrow(
                        new IllegalStateException(
                                "Matteo Ferreira CPF " + CPF + " transcrição: " + RESPONSE,
                                new IllegalArgumentException("causa com " + CHANGE)))
                .when(processor)
                .process(any());
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        null,
                        Channel.WEB,
                        Medium.AUDIO,
                        JobStep.UPLOADING,
                        clock.now());

        ReflectionTestUtils.invokeMethod(worker, "run", job);

        String logs = logText();
        assertThat(logs)
                .contains("job-failed type=LESSON_REGISTRATION")
                .contains("IllegalStateException");
        assertThat(logs).doesNotContain("Matteo", CPF, RESPONSE, CHANGE, "causa com", "\tat ");
        assertThat(
                        events.list.stream()
                                .filter(e -> e.getFormattedMessage().contains("job-failed"))
                                .allMatch(e -> e.getThrowableProxy() == null))
                .as("sem a exceção anexada ao evento")
                .isTrue();
    }

    @Test
    void flowsOfBothChannelsDoNotLogSensitiveData() throws Exception {
        Teacher clara = scenario.clara();
        Student matteo = scenario.student(clara, "Matteo Mafra", "Piano");
        seed.run(
                new SeedFile(
                        List.of(
                                new SeedFile.SeedTeacher(
                                        "clara@example.test", "Clara Souza", true)),
                        List.of(
                                new SeedFile.SeedStudent(
                                        "529.982.247-25",
                                        "Olavo Torrente",
                                        "1998-02-01",
                                        null,
                                        "Violão",
                                        List.of("clara@example.test")),
                                new SeedFile.SeedStudent(
                                        "111.111.111-11",
                                        "Aluno Com Cpf Invalido",
                                        "2010-01-01",
                                        null,
                                        "Piano",
                                        List.of("clara@example.test")))),
                false);

        // Web: código errado, código certo, registro, resposta, alteração e aprovação
        signIn("ZZZZ2222");
        Cookie session = signIn(ScenarioIT.CODE_CLARA);
        mvc.perform(
                multipart("/api/v1/students/{id}/lesson-reports", matteo.id())
                        .file(
                                new MockMultipartFile(
                                        "audio",
                                        "lesson.webm",
                                        "audio/webm",
                                        AudioNormalizationTest.AUDIO_WITH_SPEECH))
                        .cookie(session)
                        .with(csrf().asHeader()));
        scenario.processQueue();
        UUID id = report();
        postJson(
                session,
                "/api/v1/lesson-reports/" + id + "/answers",
                "{\"text\":\"" + RESPONSE + "\"}");
        scenario.processQueue();
        postJson(
                session,
                "/api/v1/lesson-reports/" + id + "/changes",
                "{\"text\":\"" + CHANGE + "\",\"baseVersion\":1}");
        scenario.processQueue();
        postJson(session, "/api/v1/lesson-reports/" + id + "/approval", "{\"confirmedVersion\":2}");

        // Telegram: código errado, vínculo, busca de aluno, áudio inválido e áudio com fala
        sendText(USER_BRUNO, "ZZZZ2222");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        sendText(USER_CLARA, "Aluno Que Nao Existe");
        sendText(USER_CLARA, "registrar aula");
        sendText(USER_CLARA, "Olavo");
        prepareTelegramFile("invalid-voice", AudioNormalizationTest.INVALID.getBytes());
        sendVoice(USER_CLARA, "invalid-voice", 10);
        sendCallback(USER_CLARA, "v1:confirm-student:");
        scenario.processQueue();
        prepareTelegramFile("good-voice", AudioNormalizationTest.AUDIO_WITH_SPEECH);
        sendVoice(USER_CLARA, "good-voice", 100);
        sendCallback(USER_CLARA, "v1:confirm-student:");
        scenario.processQueue();
        sendText(USER_CLARA, RESPONSE);
        scenario.processQueue();

        String transcription =
                Files.readString(Path.of("src/main/resources/ai/simulated/transcription.txt"))
                        .strip();
        String logs = logText();
        assertThat(events.list).as("a captura de logs funcionou").isNotEmpty();
        Map<String, String> forbidden =
                Map.ofEntries(
                        Map.entry("nome de aluno (Matteo)", "Matteo"),
                        Map.entry("nome de aluno (Olavo)", "Olavo"),
                        Map.entry("nome de aluno buscado", "Aluno Que Nao Existe"),
                        Map.entry("CPF", CPF),
                        Map.entry("CPF formatado", "529.982.247-25"),
                        Map.entry("CPF inválido da carga", "111.111.111-11"),
                        Map.entry("código de acesso", ScenarioIT.CODE_CLARA),
                        Map.entry("código errado", "ZZZZ2222"),
                        Map.entry("token do bot", TOKEN),
                        Map.entry("token de sessão", session.getValue()),
                        Map.entry("response", RESPONSE),
                        Map.entry("alteração", CHANGE),
                        Map.entry("transcrição", transcription.substring(0, 40)),
                        Map.entry("conteúdo do relatório", "Czerny"),
                        Map.entry("bytes do áudio", "FICTITIOUS-LESSON-AUDIO"));
        forbidden.forEach(
                (description, value) -> assertThat(logs).as(description).doesNotContain(value));
    }
}
