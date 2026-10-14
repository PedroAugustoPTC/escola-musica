package br.com.felipeblaft.reports.port;

import java.math.BigDecimal;

/** Texto transcrito com duração, custo estimado e identificação opaca do provedor/modelo. */
public record TranscriptionResult(
        String text, double durationSeconds, BigDecimal estimatedCost, String modelProvider) {

    @Override
    public String toString() {
        return "TranscriptionResult[duration=" + durationSeconds + "]";
    }
}
