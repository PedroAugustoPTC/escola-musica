package br.com.felipeblaft.relatorios.entrada.web;

import br.com.felipeblaft.relatorios.configuracao.CatalogoMensagens;
import br.com.felipeblaft.relatorios.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
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
public class TratadorErrosControlador {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorErrosControlador.class);

    private static final Map<CodigoErro, HttpStatus> STATUS = new EnumMap<>(CodigoErro.class);

    static {
        for (CodigoErro codigo : CodigoErro.values()) {
            STATUS.put(codigo, HttpStatus.BAD_REQUEST);
        }
        STATUS.put(CodigoErro.CODIGO_INVALIDO, HttpStatus.UNAUTHORIZED);
        STATUS.put(CodigoErro.CODIGO_EXPIRADO, HttpStatus.UNAUTHORIZED);
        STATUS.put(CodigoErro.SESSAO_EXPIRADA, HttpStatus.UNAUTHORIZED);
        STATUS.put(CodigoErro.NAO_AUTENTICADO, HttpStatus.UNAUTHORIZED);
        STATUS.put(CodigoErro.BLOQUEADO_TEMPORARIAMENTE, HttpStatus.TOO_MANY_REQUESTS);
        STATUS.put(CodigoErro.TELEGRAM_JA_VINCULADO, HttpStatus.CONFLICT);
        STATUS.put(CodigoErro.PROFESSOR_JA_VINCULADO, HttpStatus.CONFLICT);
        STATUS.put(CodigoErro.ALUNO_NAO_ASSOCIADO, HttpStatus.FORBIDDEN);
        STATUS.put(CodigoErro.RECURSO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND);
        STATUS.put(CodigoErro.PROFESSOR_INEXISTENTE, HttpStatus.NOT_FOUND);
        STATUS.put(CodigoErro.PROCESSAMENTO_EM_ANDAMENTO, HttpStatus.CONFLICT);
        STATUS.put(CodigoErro.AUDIO_MUITO_GRANDE, HttpStatus.CONTENT_TOO_LARGE);
        STATUS.put(CodigoErro.FORMATO_NAO_SUPORTADO, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        STATUS.put(CodigoErro.AUDIO_NAO_PROCESSAVEL, HttpStatus.UNPROCESSABLE_CONTENT);
        STATUS.put(CodigoErro.VERSAO_DESATUALIZADA, HttpStatus.CONFLICT);
        STATUS.put(CodigoErro.RELATORIO_JA_APROVADO, HttpStatus.CONFLICT);
        STATUS.put(CodigoErro.RELATORIO_CANCELADO, HttpStatus.CONFLICT);
        STATUS.put(CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA, HttpStatus.CONFLICT);
        STATUS.put(CodigoErro.RELATORIO_EM_ABERTO, HttpStatus.CONFLICT);
        STATUS.put(CodigoErro.SEM_AULAS_APROVADAS_NO_PERIODO, HttpStatus.CONFLICT);
        STATUS.put(CodigoErro.HA_RELATORIOS_EM_ABERTO, HttpStatus.CONFLICT);
        STATUS.put(CodigoErro.SEMESTRAL_JA_APROVADO, HttpStatus.CONFLICT);
        STATUS.put(CodigoErro.ERRO_INTERNO, HttpStatus.INTERNAL_SERVER_ERROR);
        STATUS.put(CodigoErro.SERVICO_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private final CatalogoMensagens catalogo;
    private final FormatacaoMensagens parametros;

    public TratadorErrosControlador(CatalogoMensagens catalogo, FormatacaoMensagens parametros) {
        this.catalogo = catalogo;
        this.parametros = parametros;
    }

    public static HttpStatus statusDe(CodigoErro codigo) {
        return STATUS.get(codigo);
    }

    @ExceptionHandler(ExcecaoDominio.class)
    public ResponseEntity<ErroResposta> dominio(ExcecaoDominio erro) {
        return resposta(erro.codigo(), erro.detalhes());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResposta> arquivoGrande(MaxUploadSizeExceededException erro) {
        return resposta(CodigoErro.AUDIO_MUITO_GRANDE, Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroResposta> tipoNaoSuportado(HttpMediaTypeNotSupportedException erro) {
        return resposta(CodigoErro.FORMATO_NAO_SUPORTADO, Map.of());
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
    public ResponseEntity<ErroResposta> dadosInvalidos(Exception erro) {
        return resposta(CodigoErro.DADOS_INVALIDOS, Map.of());
    }

    @ExceptionHandler({
        NoResourceFoundException.class,
        HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<ErroResposta> naoEncontrado(Exception erro) {
        return resposta(CodigoErro.RECURSO_NAO_ENCONTRADO, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> inesperado(Exception erro) {
        // sem a mensagem nem a causa: podem trazer SQL, caminhos ou dados de aluno (FR-054, FR-055)
        LOG.error("erro-inesperado tipo={} local={}", erro.getClass().getName(), localDe(erro));
        return resposta(CodigoErro.ERRO_INTERNO, Map.of());
    }

    /**
     * Primeiro ponto do código da aplicação na pilha, para depurar sem expor o conteúdo do erro.
     */
    private static String localDe(Throwable erro) {
        for (StackTraceElement ponto : erro.getStackTrace()) {
            if (ponto.getClassName().startsWith("br.com.felipeblaft")) {
                return ponto.getClassName()
                        + "."
                        + ponto.getMethodName()
                        + ":"
                        + ponto.getLineNumber();
            }
        }
        return "desconhecido";
    }

    ResponseEntity<ErroResposta> resposta(CodigoErro codigo, Map<String, Object> detalhes) {
        HttpStatus status = statusDe(codigo);
        Map<String, Object> parametrosTexto =
                new HashMap<>(parametros.parametrosErro(codigo, detalhes));
        Integer retryAfter = null;
        ResponseEntity.BodyBuilder corpo = ResponseEntity.status(status);
        if (codigo == CodigoErro.BLOQUEADO_TEMPORARIAMENTE
                && detalhes.get("segundosRestantes") instanceof Number segundos) {
            retryAfter = segundos.intValue();
            corpo.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        }
        String mensagem = catalogo.texto(codigo, parametrosTexto);
        return corpo.body(
                new ErroResposta(
                        codigo.name(), mensagem, retryAfter, detalhes.isEmpty() ? null : detalhes));
    }
}
