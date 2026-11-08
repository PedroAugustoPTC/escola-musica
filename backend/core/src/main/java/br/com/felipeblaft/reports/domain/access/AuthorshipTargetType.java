package br.com.felipeblaft.reports.domain.access;

/** Tipo do alvo de uma operação no registro de autoria; o alvo nunca tem chave estrangeira. */
public enum AuthorshipTargetType {
    TEACHER,
    ADMINISTRATOR,
    STUDENT,
    ASSIGNMENT,
    LESSON_REPORT,
    SEMESTER_REPORT,
    CONSENT,
    CONSENT_TERM,
    ERASURE_REQUEST,
    RETENTION_RUN,
    AUDIT_TRAIL,
    ALERT
}
