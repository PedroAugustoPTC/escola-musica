package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.util.EnumMap;
import java.util.HashMap;
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
 * Traduz códigos do núcleo para HTTP (contracts/mensagens.md) com o texto do catálogo. Exceções
 * inesperadas viram {@code ERRO_INTERNO}, sem detalhe interno (FR-054).
 */
@RestControllerAdvice
public class ErrorHandlerController {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandlerController.class);

    private static final Map<ErrorCode, HttpStatus> STATUS = new EnumMap<>(ErrorCode.class);

    static {
        for (ErrorCode code : ErrorCode.values()) {
            STATUS.put(code, HttpStatus.BAD_REQUEST);
        }
        STATUS.put(ErrorCode.INVALID_CODE, HttpStatus.UNAUTHORIZED);
        STATUS.put(ErrorCode.EXPIRED_CODE, HttpStatus.UNAUTHORIZED);
        STATUS.put(ErrorCode.SESSION_EXPIRED, HttpStatus.UNAUTHORIZED);
        STATUS.put(ErrorCode.NOT_AUTHENTICATED, HttpStatus.UNAUTHORIZED);
        STATUS.put(ErrorCode.TEMPORARILY_BLOCKED, HttpStatus.TOO_MANY_REQUESTS);
        STATUS.put(ErrorCode.TELEGRAM_ALREADY_LINKED, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.TEACHER_ALREADY_LINKED, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.STUDENT_NOT_ASSIGNED, HttpStatus.FORBIDDEN);
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

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> domain(DomainException error) {
        return response(error.code(), error.details());
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
        LOG.error("unexpected-error type={} local={}", error.getClass().getName(), localOf(error));
        return response(ErrorCode.INTERNAL_ERROR, Map.of());
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
        HttpStatus status = statusOf(code);
        Map<String, Object> textParameters =
                new HashMap<>(parameters.errorParameters(code, details));
        Integer retryAfter = null;
        ResponseEntity.BodyBuilder body = ResponseEntity.status(status);
        if (code == ErrorCode.TEMPORARILY_BLOCKED
                && details.get("remainingSeconds") instanceof Number seconds) {
            retryAfter = seconds.intValue();
            body.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        }
        String message = catalog.text(code, textParameters);
        return body.body(
                new ErrorResponse(
                        code.name(), message, retryAfter, details.isEmpty() ? null : details));
    }
}
