package br.com.felipeblaft.reports.domain.access;

/**
 * Resultado registrado na autoria: sucesso, falha, negado pela matriz ou bloqueado por tentativas.
 */
public enum AuthorshipResult {
    SUCCESS,
    FAILURE,
    DENIED,
    BLOCKED
}
