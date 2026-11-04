package br.com.felipeblaft.reports.domain.audit;

import java.time.Instant;

/** Resultado de uma verificação da cadeia, como lido do último evento de verificação. */
public record AuditVerification(Instant verifiedAt, boolean intact, long checkedEvents) {}
