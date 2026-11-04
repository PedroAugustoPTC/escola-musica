package br.com.felipeblaft.reports.port;

import java.time.Instant;

/**
 * Expurgo da trilha (FR-043): remove o prefixo da cadeia anterior ao corte e deixa a âncora. Só o
 * {@code RunRetentionUseCase} depende desta porta (regra ArchUnit). O piso de 365 dias vale no
 * banco, qualquer que seja o prazo configurado.
 */
public interface AuditTrailRetentionPort {

    /** Remove os eventos anteriores ao corte e devolve quantos saíram. */
    int purgeOlderThan(Instant cutoff);
}
