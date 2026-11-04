package br.com.felipeblaft.reports.domain.audit;

import java.util.Objects;

/**
 * Âncora deixada pelo expurgo da trilha (FR-043): o último {@code sequence} removido e o hash desse
 * evento. A verificação da cadeia parte da âncora mais recente.
 */
public record AuditAnchor(long purgedUntilSequence, String lastRecordHash) {

    public AuditAnchor {
        Objects.requireNonNull(lastRecordHash);
    }
}
