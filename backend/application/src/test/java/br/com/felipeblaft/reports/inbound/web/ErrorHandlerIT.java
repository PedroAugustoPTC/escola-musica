package br.com.felipeblaft.reports.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.felipeblaft.reports.support.ContractApiBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * FR-054: uma exceção inesperada em um endpoint do portal vira 500 com {@code ERRO_INTERNO} e o
 * texto do catálogo, sem nome de classe, stack trace, SQL nem caminho de arquivo na resposta, e o
 * log registra o erro sem os dados que a exceção carregava.
 */
class ErrorHandlerIT extends ContractApiBase {

    private static final String INTERNAL_DETAIL =
            "SELECT * FROM student WHERE name = 'Matteo Mafra' failed in /app/src/Repositorio.java:42";

    @MockitoSpyBean GetTeacherHomeUseCase getHome;

    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Logger root;
    private Level rootLevel;

    @BeforeEach
    void captureLogs() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLevel = root.getLevel();
        root.setLevel(Level.INFO);
        events.start();
        root.addAppender(events);
    }

    @AfterEach
    void restoreLogs() {
        root.detachAppender(events);
        events.stop();
        root.setLevel(rootLevel);
    }

    private String logText() {
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : List.copyOf(events.list)) {
            text.append(event.getFormattedMessage()).append('\n');
            if (event.getThrowableProxy() != null) {
                text.append(ThrowableProxyUtil.asString(event.getThrowableProxy())).append('\n');
            }
        }
        return text.toString();
    }

    @Test
    void unexpectedExceptionReturns500WithCodeAndCatalogMessageWithoutInternalDetail()
            throws Exception {
        scenario.student(scenario.clara(), "Matteo Mafra", "Piano");
        Cookie session = signIn(ScenarioIT.CODE_CLARA);
        doThrow(new IllegalStateException(INTERNAL_DETAIL)).when(getHome).run(any());

        MvcResult result = mvc.perform(get("/api/v1/inicio").cookie(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        String body = result.getResponse().getContentAsString();
        JsonNode json = JSON_READER.readTree(body);
        assertThat(json.path("codigo").asString()).isEqualTo("ERRO_INTERNO");
        assertThat(json.path("mensagem").asString())
                .isEqualTo("Ocorreu um erro inesperado. Tente novamente mais tarde.");
        assertThat(body)
                .doesNotContain(
                        "IllegalStateException",
                        "java.lang",
                        "br.com.felipeblaft",
                        "\tat ",
                        "SELECT",
                        "Matteo",
                        ".java",
                        "/app/");
        assertThat(result.getResponse().getContentType()).contains("json");
    }

    @Test
    void logRecordsErrorWithoutDataTheExceptionCarried() throws Exception {
        scenario.student(scenario.clara(), "Matteo Mafra", "Piano");
        Cookie session = signIn(ScenarioIT.CODE_CLARA);
        doThrow(new IllegalStateException(INTERNAL_DETAIL)).when(getHome).run(any());

        mvc.perform(get("/api/v1/inicio").cookie(session)).andReturn();

        String logs = logText();
        assertThat(logs).contains("erro-inesperado").contains("IllegalStateException");
        assertThat(logs).doesNotContain("Matteo", "SELECT", "Repositorio.java", "/app/");
    }

    private static final tools.jackson.databind.json.JsonMapper JSON_READER =
            tools.jackson.databind.json.JsonMapper.builder().build();
}
