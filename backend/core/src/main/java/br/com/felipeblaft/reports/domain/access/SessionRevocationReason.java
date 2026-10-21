package br.com.felipeblaft.reports.domain.access;

/**
 * Motivo da revogação de uma sessão (R-08): a sessão é marcada, nunca removida, para que a próxima
 * requisição receba a resposta certa (acesso encerrado × sessão expirada).
 */
public enum SessionRevocationReason {
    /** Conta inativada: o usuário vê "acesso encerrado". */
    ACCOUNT_INACTIVATED,
    /** Código do professor reemitido: o usuário vê "sessão expirada". */
    CODE_REISSUED,
    /** Senha do administrador trocada. */
    PASSWORD_CHANGED
}
