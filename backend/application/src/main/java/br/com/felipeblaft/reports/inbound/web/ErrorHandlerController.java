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
        STATUS.put(ErrorCode.CODIGO_INVALIDO, HttpStatus.UNAUTHORIZED);
        STATUS.put(ErrorCode.CODIGO_EXPIRADO, HttpStatus.UNAUTHORIZED);
        STATUS.put(ErrorCode.SESSAO_EXPIRADA, HttpStatus.UNAUTHORIZED);
        STATUS.put(ErrorCode.NAO_AUTENTICADO, HttpStatus.UNAUTHORIZED);
        STATUS.put(ErrorCode.BLOQUEADO_TEMPORARIAMENTE, HttpStatus.TOO_MANY_REQUESTS);
        STATUS.put(ErrorCode.TELEGRAM_JA_VINCULADO, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.PROFESSOR_JA_VINCULADO, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.ALUNO_NAO_ASSOCIADO, HttpStatus.FORBIDDEN);
        STATUS.put(ErrorCode.RECURSO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND);
        STATUS.put(ErrorCode.PROFESSOR_INEXISTENTE, HttpStatus.NOT_FOUND);
        STATUS.put(ErrorCode.PROCESSAMENTO_EM_ANDAMENTO, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.AUDIO_MUITO_GRANDE, HttpStatus.CONTENT_TOO_LARGE);
        STATUS.put(ErrorCode.FORMATO_NAO_SUPORTADO, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        STATUS.put(ErrorCode.AUDIO_NAO_PROCESSAVEL, HttpStatus.UNPROCESSABLE_CONTENT);
        STATUS.put(ErrorCode.VERSAO_DESATUALIZADA, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.RELATORIO_JA_APROVADO, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.RELATORIO_CANCELADO, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.ACAO_NAO_PERMITIDA_NA_ETAPA, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.RELATORIO_EM_ABERTO, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.SEM_AULAS_APROVADAS_NO_PERIODO, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.HA_RELATORIOS_EM_ABERTO, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.SEMESTRAL_JA_APROVADO, HttpStatus.CONFLICT);
        STATUS.put(ErrorCode.ERRO_INTERNO, HttpStatus.INTERNAL_SERVER_ERROR);
        STATUS.put(ErrorCode.SERVICO_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE);
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
        return response(ErrorCode.AUDIO_MUITO_GRANDE, Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> unsupportedType(HttpMediaTypeNotSupportedException error) {
        return response(ErrorCode.FORMATO_NAO_SUPORTADO, Map.of());
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
        return response(ErrorCode.DADOS_INVALIDOS, Map.of());
    }

    @ExceptionHandler({
        NoResourceFoundException.class,
        HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<ErrorResponse> notFound(Exception error) {
        return response(ErrorCode.RECURSO_NAO_ENCONTRADO, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception error) {
        // sem a mensagem nem a causa: podem trazer SQL, caminhos ou dados de aluno (FR-054, FR-055)
        LOG.error("erro-inesperado tipo={} local={}", error.getClass().getName(), localOf(error));
        return response(ErrorCode.ERRO_INTERNO, Map.of());
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
        return "desconhecido";
    }

    ResponseEntity<ErrorResponse> response(ErrorCode code, Map<String, Object> details) {
        HttpStatus status = statusOf(code);
        Map<String, Object> textParameters =
                new HashMap<>(parameters.errorParameters(code, details));
        Integer retryAfter = null;
        ResponseEntity.BodyBuilder body = ResponseEntity.status(status);
        if (code == ErrorCode.BLOQUEADO_TEMPORARIAMENTE
                && details.get("segundosRestantes") instanceof Number seconds) {
            retryAfter = seconds.intValue();
            body.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        }
        String message = catalog.text(code, textParameters);
        return body.body(
                new ErrorResponse(
                        code.name(), message, retryAfter, details.isEmpty() ? null : details));
    }
}
