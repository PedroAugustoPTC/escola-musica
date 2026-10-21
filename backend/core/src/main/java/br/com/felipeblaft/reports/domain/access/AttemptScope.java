package br.com.felipeblaft.reports.domain.access;

/**
 * Escopo do controle de tentativas (R-05, R-06): a contagem é separada por escopo, de modo que as
 * falhas de um papel ou de uma operação nunca bloqueiam outro.
 */
public enum AttemptScope {
    /** Código de acesso do professor (Web e Telegram); comportamento da 001. */
    TEACHER_CODE,
    /** Entrada do administrador com e-mail e senha. */
    ADMIN_SIGN_IN,
    /** Ativação da conta do administrador com o código de ativação. */
    ADMIN_ACTIVATION,
    /** Recusas por CPF de aluno alheio (sondagem), por professor. */
    TEACHER_CPF_PROBE,
    /** Toda consulta de duplicidade disparada por CPF digitado pelo professor. */
    TEACHER_CPF_LOOKUP
}
