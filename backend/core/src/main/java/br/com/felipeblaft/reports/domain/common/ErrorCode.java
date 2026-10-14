package br.com.felipeblaft.reports.domain.common;

/**
 * Códigos estáveis de erro devolvidos pelo núcleo (R-18). O núcleo nunca devolve texto: cada canal
 * busca a mensagem no catálogo (contracts/mensagens.md) pela {@link #messageKey()}.
 */
public enum ErrorCode {
    // Acesso
    INVALID_CODE,
    EXPIRED_CODE,
    TEMPORARILY_BLOCKED,
    TELEGRAM_ALREADY_LINKED,
    TEACHER_ALREADY_LINKED,
    SESSION_EXPIRED,
    NOT_AUTHENTICATED,

    // Autorização
    STUDENT_NOT_ASSIGNED,
    RESOURCE_NOT_FOUND,

    // Registro de aula e processamento
    PROCESSING_IN_PROGRESS,
    AUDIO_TOO_LARGE,
    UNSUPPORTED_FORMAT,
    AUDIO_NOT_PROCESSABLE,

    // Perguntas, conferência e alteração
    EMPTY_ANSWER,
    EMPTY_CHANGE,
    STALE_VERSION,

    // Aprovação, cancelamento e retomada
    REPORT_ALREADY_APPROVED,
    REPORT_CANCELLED("REPORT_CANCELLED_ERROR"),
    ACTION_NOT_ALLOWED_AT_STEP,
    REPORT_ALREADY_OPEN,

    // Semestral
    NO_APPROVED_LESSONS_IN_PERIOD,
    OPEN_REPORTS_EXIST,
    SEMESTER_REPORT_ALREADY_APPROVED,

    // Carga inicial (contracts/carga-inicial.md)
    INVALID_EMAIL,
    DUPLICATE_EMAIL_IN_FILE,
    NAME_REQUIRED,
    INVALID_CPF,
    DUPLICATE_CPF_IN_FILE,
    INVALID_BIRTH_DATE,
    GUARDIAN_REQUIRED,
    TEACHER_NOT_FOUND,

    // Genéricos
    INVALID_DATA,
    INTERNAL_ERROR,
    SERVICE_UNAVAILABLE;

    private final String messageKey;

    ErrorCode() {
        this.messageKey = null;
    }

    ErrorCode(String messageKey) {
        this.messageKey = messageKey;
    }

    /** Chave do texto no catálogo de mensagens; normalmente igual ao próprio código. */
    public String messageKey() {
        return messageKey != null ? messageKey : name();
    }
}
