package br.com.felipeblaft.reports.port;

import java.time.Instant;

/** Expurgo dos alertas operacionais já normalizados (FR-014). */
public interface AlertHistoryRetentionPort {

    /** Remove os alertas normalizados antes do corte; devolve quantos saíram. */
    int purgeNormalizedBefore(Instant cutoff);
}
