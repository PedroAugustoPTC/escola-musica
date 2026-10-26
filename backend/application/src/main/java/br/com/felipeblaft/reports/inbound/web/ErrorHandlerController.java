package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.config.log.ErrorReference;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduz códigos do núcleo para HTTP (contracts/messages.md) com o texto do catálogo. Exceções
 * inesperadas viram {@code INTERNAL_ERROR}, sem detalhe interno (FR-054).
 */
@RestControllerAdvice
public class ErrorHandlerController {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandlerController.class);
    private static final String ADMINISTRATIVE_PREFIX = "/api/v1/admin/";

    /** Texto neutro do e-mail duplicado fora da área administrativa (sem nome, sem CPF). */
    private static final String EMAIL_UNAVAILABLE = "EMAIL_UNAVAILABLE";

    private static final Map<ErrorCode, HttpStatus> STATUS = new EnumMap<>(ErrorCode.class);

    static {
        for (ErrorCode code : ErrorCode.values()) {
            STATUS.put(code, HttpStatus.BAD_REQUEST);
        }
        STATUS.put(ErrorCode.INVALID_CODE, HttpStatus.UNAUTHORIZED);
        STATUS.put(ErrorCode.SESSION_EXPIRED, HttpStatus.UNAUTHORIZED);
        STATUS.put(ErrorCode.NOT_AUTHENTICATED, HttpStatus.UNAUTHORIZED);
        STATUS.put(ErrorCode.TEMPORARILY_BLOCKED, HttpStatus.TOO_MANY_REQUESTS);
        STATUS.put(ErrorCode.TELEGRAM_ALREADY_LINKED, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.TEACHER_ALREADY_LINKED, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        STATUS.put(ErrorCode.TEACHER_NOT_FOUND, HttpStatus.NOT_FOUND);
        STATUS.put(ErrorCode.PROCESSING_IN_PROGRESS, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.AUDIO_TOO_LARGE, HttpStatus.CONTENT_TOO_LARGE);
        STATUS.put(ErrorCode.UNSUPPORTED_FORMAT, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        STATUS.put(ErrorCode.AUDIO_NOT_PROCESSABLE, HttpStatus.UNPROCESSABLE_CONTENT);
        STATUS.put(ErrorCode.STALE_VERSION, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.REPORT_ALREADY_APPROVED, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.REPORT_CANCELLED, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.ACTION_NOT_ALLOWED_AT_STEP, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.REPORT_ALREADY_OPEN, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.NO_APPROVED_LESSONS_IN_PERIOD, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.OPEN_REPORTS_EXIST, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.SEMESTER_REPORT_ALREADY_APPROVED, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        STATUS.put(ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        putFeature005Codes();
    }

    /**
     * Códigos da feature 005 (contracts/messages.md, seção 1). Os de validação ficam no padrão 400.
     * Os da confirmação do Telegram não circulam pela Web; ficam em 409 por serem conflito de
     * estado.
     */
    private static void putFeature005Codes() {
        for (ErrorCode code :
                List.of(
                        ErrorCode.ACCESS_ENDED,
                        ErrorCode.INVALID_CREDENTIALS,
                        ErrorCode.INVALID_ACTIVATION)) {
            STATUS.put(code, HttpStatus.UNAUTHORIZED);
        }
        for (ErrorCode code :
                List.of(
                        ErrorCode.PASSWORD_CHANGE_REQUIRED,
                        ErrorCode.SHARED_STUDENT_ACTION_DENIED)) {
            STATUS.put(code, HttpStatus.FORBIDDEN);
        }
        for (ErrorCode code :
                List.of(
                        ErrorCode.DUPLICATE_EMAIL,
                        ErrorCode.DUPLICATE_CPF,
                        ErrorCode.CPF_UNAVAILABLE,
                        ErrorCode.STUDENT_ALREADY_YOURS,
                        ErrorCode.LAST_ADMINISTRATOR,
                        ErrorCode.TEACHER_INACTIVE,
                        ErrorCode.STUDENT_INACTIVE,
                        ErrorCode.PASSWORD_CHANGE_NOT_REQUIRED,
                        ErrorCode.CONFIRMATION_EXPIRED,
                        ErrorCode.CONFIRMATION_ALREADY_DONE,
                        ErrorCode.CALLBACK_EXPIRED)) {
            STATUS.put(code, HttpStatus.CONFLICT);
        }
        STATUS.put(ErrorCode.OPERATION_TEMPORARILY_BLOCKED, HttpStatus.TOO_MANY_REQUESTS);
        STATUS.put(ErrorCode.OPERATION_NOT_SAVED, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private final MessageCatalog catalog;
    private final MessageFormatting parameters;

    public ErrorHandlerController(MessageCatalog catalog, MessageFormatting parameters) {
        this.catalog = catalog;
        this.parameters = parameters;
    }

    public static HttpStatus statusOf(ErrorCode code) {
        return STATUS.get(code);
    }

    /**
     * O núcleo já decide, pelo papel do ator, se a duplicidade traz o nome do registro existente
     * (só o administrador o recebe, R-13). A rota é apenas a rede de segurança: fora da área
     * administrativa a recusa é sempre a neutra, sem nome e sem detalhe. O CPF vira {@code
     * CPF_UNAVAILABLE}; o e-mail mantém o código e usa o texto neutro do e-mail, que não fala de
     * CPF.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> domain(DomainException error, HttpServletRequest request) {
        if (!isAdministrativeRoute(request)) {
            if (error.code() == ErrorCode.DUPLICATE_CPF) {
                return response(ErrorCode.CPF_UNAVAILABLE, Map.of());
            }
            if (error.code() == ErrorCode.DUPLICATE_EMAIL) {
                return response(ErrorCode.DUPLICATE_EMAIL, EMAIL_UNAVAILABLE, Map.of(), null);
            }
        }
        return response(error.code(), error.details());
    }

    private static boolean isAdministrativeRoute(HttpServletRequest request) {
        return request != null
                && request.getRequestURI() != null
                && request.getRequestURI().startsWith(ADMINISTRATIVE_PREFIX);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> largeFile(MaxUploadSizeExceededException error) {
        return response(ErrorCode.AUDIO_TOO_LARGE, Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> unsupportedType(HttpMediaTypeNotSupportedException error) {
        return response(ErrorCode.UNSUPPORTED_FORMAT, Map.of());
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class,
        MultipartException.class
    })
    public ResponseEntity<ErrorResponse> invalidData(Exception error) {
        return response(ErrorCode.INVALID_DATA, Map.of());
    }

    @ExceptionHandler({
        NoResourceFoundException.class,
        HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<ErrorResponse> notFound(Exception error) {
        return response(ErrorCode.RESOURCE_NOT_FOUND, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception error) {
        // sem a mensagem nem a causa: podem trazer SQL, caminhos ou dados de aluno (FR-054, FR-055)
        String reference = ErrorReference.newReference();
        LOG.error(
                "unexpected-error reference={} type={} local={}",
                reference,
                error.getClass().getName(),
                localOf(error));
        return response(ErrorCode.INTERNAL_ERROR, Map.of(), reference);
    }

    /**
     * Primeiro ponto do código da aplicação na pilha, para depurar sem expor o conteúdo do erro.
     */
    private static String localOf(Throwable error) {
        for (StackTraceElement point : error.getStackTrace()) {
            if (point.getClassName().startsWith("br.com.felipeblaft")) {
                return point.getClassName()
                        + "."
                        + point.getMethodName()
                        + ":"
                        + point.getLineNumber();
            }
        }
        return "unknown";
    }

    ResponseEntity<ErrorResponse> response(ErrorCode code, Map<String, Object> details) {
        return response(code, details, null);
    }

    private ResponseEntity<ErrorResponse> response(
            ErrorCode code, Map<String, Object> details, String reference) {
        return response(code, code.messageKey(), details, reference);
    }

    private ResponseEntity<ErrorResponse> response(
            ErrorCode code, String messageKey, Map<String, Object> details, String reference) {
        HttpStatus status = statusOf(code);
        Map<String, Object> textParameters =
                new HashMap<>(parameters.errorParameters(code, details));
        if (reference != null) {
            textParameters.put("reference", reference);
        }
        Integer retryAfter = null;
        ResponseEntity.BodyBuilder body = ResponseEntity.status(status);
        if ((code == ErrorCode.TEMPORARILY_BLOCKED
                        || code == ErrorCode.OPERATION_TEMPORARILY_BLOCKED)
                && details.get("remainingSeconds") instanceof Number seconds) {
            retryAfter = seconds.intValue();
            body.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        }
        String message = catalog.text(messageKey, textParameters);
        return body.body(
                new ErrorResponse(
                        code.name(),
                        message,
                        retryAfter,
                        details.isEmpty() ? null : details,
                        reference));
    }
}
