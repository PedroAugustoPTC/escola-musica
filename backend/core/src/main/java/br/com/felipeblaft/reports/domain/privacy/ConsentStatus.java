package br.com.felipeblaft.reports.domain.privacy;

/** Situação derivada do consentimento de um aluno (nunca gravada). */
public enum ConsentStatus {
    VALID,
    REVOKED,
    SUPERSEDED,
    MISSING
}
