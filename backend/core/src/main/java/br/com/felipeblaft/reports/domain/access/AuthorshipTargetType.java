package br.com.felipeblaft.reports.domain.access;

/** Tipo do alvo de uma operação no registro de autoria; o alvo nunca tem chave estrangeira. */
public enum AuthorshipTargetType {
    TEACHER,
    ADMINISTRATOR,
    STUDENT,
    ASSIGNMENT
}
