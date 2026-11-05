package br.com.felipeblaft.reports.domain.common;

/**
 * Códigos estáveis de erro devolvidos pelo núcleo (R-18). O núcleo nunca devolve texto: cada canal
 * busca a mensagem no catálogo (contracts/mensagens.md) pela {@link #messageKey()}.
 */
public enum ErrorCode {
    // Acesso
    INVALID_CODE,
    TEMPORARILY_BLOCKED,
    TELEGRAM_ALREADY_LINKED,
    TEACHER_ALREADY_LINKED,
    SESSION_EXPIRED,
    NOT_AUTHENTICATED,

    // Autorização (aluno não associado equivale a aluno inexistente, FR-050)
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

    // Acesso do administrador (feature 005)
    INVALID_CREDENTIALS,
    INVALID_ACTIVATION,
    ACCESS_ENDED,
    PASSWORD_CHANGE_REQUIRED,
    PASSWORD_CHANGE_NOT_REQUIRED,
    PASSWORD_LENGTH,
    PASSWORD_EQUALS_IDENTITY,
    PASSWORD_SAME_AS_PROVISIONAL,
    LAST_ADMINISTRATOR,

    // Validação de cadastro (campo + motivo)
    VALIDATION_FAILED,
    INVALID_NAME,
    INVALID_PHONE,
    INVALID_INSTRUMENT,
    INVALID_TEXT,

    // Duplicidade, escopo e situação
    DUPLICATE_EMAIL,
    DUPLICATE_CPF,
    CPF_UNAVAILABLE,
    STUDENT_ALREADY_YOURS,
    SHARED_STUDENT_ACTION_DENIED,
    TEACHER_INACTIVE,
    STUDENT_INACTIVE,

    // Bloqueio da sondagem de CPF, confirmação do Telegram e falha parcial
    OPERATION_TEMPORARILY_BLOCKED,
    CONFIRMATION_EXPIRED,
    CONFIRMATION_ALREADY_DONE,
    CALLBACK_EXPIRED,
    OPERATION_NOT_SAVED,

    // Consentimento do responsável (feature 007)
    CONSENT_REQUIRED,
    CONSENT_ALREADY_REVOKED,

    // Eliminação a pedido (feature 007)
    ERASURE_ALREADY_OPEN,
    STUDENT_ALREADY_ERASED,
    ERASURE_ALREADY_COMPLETED,

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
