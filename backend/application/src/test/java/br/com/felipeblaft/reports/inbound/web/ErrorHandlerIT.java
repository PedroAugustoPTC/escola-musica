package br.com.felipeblaft.reports.inbound.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.felipeblaft.reports.domain.common.ErrorCode;
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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

        MvcResult result = mvc.perform(get("/api/v1/home").cookie(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        String body = result.getResponse().getContentAsString();
        JsonNode json = JSON_READER.readTree(body);
        assertThat(json.path("code").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(json.path("message").asString())
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

        mvc.perform(get("/api/v1/home").cookie(session)).andReturn();

        String logs = logText();
        assertThat(logs).contains("unexpected-error").contains("IllegalStateException");
        assertThat(logs).doesNotContain("Matteo", "SELECT", "Repository.java", "/app/");
    }

    @Autowired ErrorHandlerController handler;

    /** Tabela de contracts/messages.md, seção 1: código → status HTTP. */
    private static final Map<ErrorCode, HttpStatus> CONTRACT_STATUS =
            Map.ofEntries(
                    Map.entry(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST),
                    Map.entry(ErrorCode.INVALID_NAME, HttpStatus.BAD_REQUEST),
                    Map.entry(ErrorCode.INVALID_EMAIL, HttpStatus.BAD_REQUEST),
                    Map.entry(ErrorCode.INVALID_CPF, HttpStatus.BAD_REQUEST),
                    Map.entry(ErrorCode.INVALID_BIRTH_DATE, HttpStatus.BAD_REQUEST),
                    Map.entry(ErrorCode.INVALID_PHONE, HttpStatus.BAD_REQUEST),
                    Map.entry(ErrorCode.INVALID_INSTRUMENT, HttpStatus.BAD_REQUEST),
                    Map.entry(ErrorCode.INVALID_TEXT, HttpStatus.BAD_REQUEST),
                    Map.entry(ErrorCode.GUARDIAN_REQUIRED, HttpStatus.BAD_REQUEST),
                    Map.entry(ErrorCode.PASSWORD_LENGTH, HttpStatus.BAD_REQUEST),
                    Map.entry(ErrorCode.PASSWORD_EQUALS_IDENTITY, HttpStatus.BAD_REQUEST),
                    Map.entry(ErrorCode.PASSWORD_SAME_AS_PROVISIONAL, HttpStatus.BAD_REQUEST),
                    Map.entry(ErrorCode.NOT_AUTHENTICATED, HttpStatus.UNAUTHORIZED),
                    Map.entry(ErrorCode.SESSION_EXPIRED, HttpStatus.UNAUTHORIZED),
                    Map.entry(ErrorCode.ACCESS_ENDED, HttpStatus.UNAUTHORIZED),
                    Map.entry(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED),
                    Map.entry(ErrorCode.INVALID_ACTIVATION, HttpStatus.UNAUTHORIZED),
                    Map.entry(ErrorCode.INVALID_CODE, HttpStatus.UNAUTHORIZED),
                    Map.entry(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND),
                    Map.entry(ErrorCode.PASSWORD_CHANGE_REQUIRED, HttpStatus.FORBIDDEN),
                    Map.entry(ErrorCode.SHARED_STUDENT_ACTION_DENIED, HttpStatus.FORBIDDEN),
                    Map.entry(ErrorCode.DUPLICATE_EMAIL, HttpStatus.CONFLICT),
                    Map.entry(ErrorCode.DUPLICATE_CPF, HttpStatus.CONFLICT),
                    Map.entry(ErrorCode.CPF_UNAVAILABLE, HttpStatus.CONFLICT),
                    Map.entry(ErrorCode.STUDENT_ALREADY_YOURS, HttpStatus.CONFLICT),
                    Map.entry(ErrorCode.LAST_ADMINISTRATOR, HttpStatus.CONFLICT),
                    Map.entry(ErrorCode.TEACHER_INACTIVE, HttpStatus.CONFLICT),
                    Map.entry(ErrorCode.STUDENT_INACTIVE, HttpStatus.CONFLICT),
                    Map.entry(ErrorCode.PASSWORD_CHANGE_NOT_REQUIRED, HttpStatus.CONFLICT),
                    Map.entry(ErrorCode.CONFIRMATION_EXPIRED, HttpStatus.CONFLICT),
                    Map.entry(ErrorCode.CONFIRMATION_ALREADY_DONE, HttpStatus.CONFLICT),
                    Map.entry(ErrorCode.CALLBACK_EXPIRED, HttpStatus.CONFLICT),
                    Map.entry(ErrorCode.TEMPORARILY_BLOCKED, HttpStatus.TOO_MANY_REQUESTS),
                    Map.entry(
                            ErrorCode.OPERATION_TEMPORARILY_BLOCKED, HttpStatus.TOO_MANY_REQUESTS),
                    Map.entry(ErrorCode.OPERATION_NOT_SAVED, HttpStatus.INTERNAL_SERVER_ERROR),
                    Map.entry(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR));

    @Test
    void statusMapFollowsTheContractForTheFeatureCodes() {
        CONTRACT_STATUS.forEach(
                (code, status) ->
                        assertThat(ErrorHandlerController.statusOf(code))
                                .as("status de %s", code)
                                .isEqualTo(status));
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(ErrorHandlerController.statusOf(code))
                    .as("sem status: %s", code)
                    .isNotNull();
        }
    }

    @Test
    void probingBlockCarriesRetryAfterAndMinutesInText() {
        var response =
                handler.response(
                        ErrorCode.OPERATION_TEMPORARILY_BLOCKED, Map.of("remainingSeconds", 150));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("150");
        assertThat(response.getBody().message())
                .isEqualTo(
                        "Esta operação está indisponível no momento. Tente novamente em 3 minuto(s).");
    }

    @Test
    void notAssignedStudentAnswersLikeMissingStudent() {
        var response = handler.response(ErrorCode.RESOURCE_NOT_FOUND, Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Não encontramos o que você procurou.");
    }

    @Test
    void duplicateAndValidationDetailsReachTheBody() {
        var duplicate = handler.response(ErrorCode.DUPLICATE_CPF, Map.of("name", "Matteo Mafra"));
        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(duplicate.getBody().message())
                .isEqualTo("Este CPF já pertence ao aluno Matteo Mafra.");

        var validation =
                handler.response(
                        ErrorCode.VALIDATION_FAILED,
                        Map.of("fields", List.of(Map.of("field", "cpf", "code", "INVALID_CPF"))));
        assertThat(validation.getStatusCode().value()).isEqualTo(400);
        assertThat(validation.getBody().details()).containsKey("fields");
    }

    private static final tools.jackson.databind.json.JsonMapper JSON_READER =
            tools.jackson.databind.json.JsonMapper.builder().build();
}
