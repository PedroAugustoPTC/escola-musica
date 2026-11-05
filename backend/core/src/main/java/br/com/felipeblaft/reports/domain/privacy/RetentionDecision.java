package br.com.felipeblaft.reports.domain.privacy;

import java.time.Duration;
import java.time.Instant;

/** O que a rotina de retenção deve fazer com um relatório ou PDF (FR-010 a FR-013). */
public enum RetentionDecision {
    KEEP,
    REMOVE_SOURCE,
    REMOVE_UNAPPROVED_PDFS,
    ANONYMIZE;

    /**
     * Verdadeiro quando o prazo, contado a partir de {@code reference}, já se esgotou em {@code
     * now}.
     */
    public static boolean expired(Instant reference, int days, Instant now) {
        return reference != null && !now.isBefore(reference.plus(Duration.ofDays(days)));
    }
}
