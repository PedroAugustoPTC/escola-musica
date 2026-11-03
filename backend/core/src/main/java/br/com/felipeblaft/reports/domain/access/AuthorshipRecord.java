package br.com.felipeblaft.reports.domain.access;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Registro de autoria (FR-040, data-model.md §3.4): quem fez o quê, quando, por qual canal e com
 * qual resultado. Somente acréscimo. Nunca carrega CPF, dado do aluno ou do responsável, conteúdo
 * de relatório, e-mail digitado de conta inexistente, senha nem código; só identificadores, códigos
 * e o resumo da origem (nas autenticações).
 *
 * @param authorId id do administrador ou do professor; {@code null} para sistema e para e-mail ou
 *     código desconhecido
 * @param authorRole papel do autor; {@code null} para sistema e desconhecido
 * @param operation nome de {@link Operation} ou de um dos eventos de autenticação abaixo
 * @param readDetail filtros, página ou versão do PDF das leituras da visão da escola (feature 006);
 *     só nas operações de {@link #READ_DETAIL_OPERATIONS}, nulo nas demais
 * @param errorCode ex.: {@code CPF_UNAVAILABLE}, {@code UNKNOWN_EMAIL}
 * @param originHash resumo com chave da origem; só em eventos de autenticação e bloqueio
 * @param correlationId id da requisição (Web, UUID) ou {@code update_id} (Telegram, 1 a 10
 *     dígitos); aceita ainda um sufixo curto em minúsculas após {@code req-}, {@code load-} ou
 *     {@code startup-}
 *     <p>Os formatos são estritos e validados aqui, porque a porta recebe o record direto: {@code
 *     operation} e {@code errorCode} são códigos em maiúsculas; {@code originHash} é o resumo
 *     hexadecimal de 64 caracteres; {@code correlationId} nunca tem 11 ou mais dígitos seguidos
 *     (CPF, telefone), máscara, espaço, arroba nem forma de código de acesso ou de ativação.
 */
public record AuthorshipRecord(
        UUID id,
        Instant occurredAt,
        UUID authorId,
        Role authorRole,
        ActionOrigin origin,
        String operation,
        AuthorshipTargetType targetType,
        UUID targetId,
        AuthorshipResult result,
        String errorCode,
        String originHash,
        String correlationId,
        AuthorshipReadDetail readDetail) {

    /** Eventos que não são {@link Operation} (data-model.md §6). */
    public static final String TEACHER_LINK_TELEGRAM = "TEACHER_LINK_TELEGRAM";

    public static final String ATTEMPT_BLOCKED = "ATTEMPT_BLOCKED";
    public static final String AUTHORIZATION_DENIED = "AUTHORIZATION_DENIED";
    public static final String CPF_REFUSED_OTHER_STUDENT = "CPF_REFUSED_OTHER_STUDENT";

    /**
     * Operações que podem levar o {@link AuthorshipReadDetail}; espelha, e o teste compara, o CHECK
     * da V16.
     */
    public static final Set<String> READ_DETAIL_OPERATIONS =
            Set.of(
                    Operation.SCHOOL_HISTORY_QUERY.name(),
                    Operation.SCHOOL_REPORT_DOCUMENT_OPEN.name(),
                    Operation.SCHOOL_REPORT_DOCUMENT_DOWNLOAD.name());

    /**
     * Operações e eventos que podem levar o resumo da origem; espelha, e o teste compara, o CHECK
     * {@code ck_authorship_record_origin_hash_event} (V10).
     */
    public static final Set<String> ORIGIN_HASH_EVENTS =
            Set.of(
                    Operation.ADMIN_SIGN_IN.name(),
                    Operation.ADMIN_ACTIVATE.name(),
                    Operation.ADMIN_SIGN_OUT.name(),
                    Operation.ADMIN_CHANGE_PASSWORD.name(),
                    Operation.TEACHER_SIGN_IN.name(),
                    TEACHER_LINK_TELEGRAM,
                    ATTEMPT_BLOCKED,
                    AUTHORIZATION_DENIED);

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,47}");
    private static final Pattern ORIGIN_HASH = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern UUID_FORM =
            Pattern.compile(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_ID = Pattern.compile("[0-9]{1,10}");
    private static final Pattern PREFIXED =
            Pattern.compile("(req|load|startup)-(?![a-z0-9]*[0-9]{11})[a-z0-9]{1,16}");

    public AuthorshipRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(result);
        Objects.requireNonNull(correlationId);
        if (authorId != null && authorRole == null) {
            throw new IllegalArgumentException("Autor com id exige o papel");
        }
        // as mensagens nunca repetem o valor recebido
        if (!CODE.matcher(operation).matches()) {
            throw new IllegalArgumentException("operação de autoria fora do formato");
        }
        if (errorCode != null && !CODE.matcher(errorCode).matches()) {
            throw new IllegalArgumentException("código de erro de autoria fora do formato");
        }
        if (originHash != null && !ORIGIN_HASH.matcher(originHash).matches()) {
            throw new IllegalArgumentException("resumo de origem de autoria fora do formato");
        }
        if (originHash != null && !ORIGIN_HASH_EVENTS.contains(operation)) {
            throw new IllegalArgumentException("operação não pode levar resumo de origem");
        }
        if (!validCorrelation(correlationId)) {
            throw new IllegalArgumentException("identificador de correlação fora do formato");
        }
        if (readDetail != null && !READ_DETAIL_OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException("operação não pode levar detalhe de leitura");
        }
    }

    /** Registro sem detalhe de leitura (todas as operações anteriores à feature 006). */
    public AuthorshipRecord(
            UUID id,
            Instant occurredAt,
            UUID authorId,
            Role authorRole,
            ActionOrigin origin,
            String operation,
            AuthorshipTargetType targetType,
            UUID targetId,
            AuthorshipResult result,
            String errorCode,
            String originHash,
            String correlationId) {
        this(
                id,
                occurredAt,
                authorId,
                authorRole,
                origin,
                operation,
                targetType,
                targetId,
                result,
                errorCode,
                originHash,
                correlationId,
                null);
    }

    private static boolean validCorrelation(String value) {
        return UUID_FORM.matcher(value).matches()
                || UPDATE_ID.matcher(value).matches()
                || PREFIXED.matcher(value).matches();
    }
}
