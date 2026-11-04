package br.com.felipeblaft.reports.domain.operations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Preço (USD) de uma unidade de um provedor, válido a partir de uma data. */
public record PriceEntry(
        String provider, PriceUnit unit, BigDecimal valueUsd, LocalDate effectiveFrom) {

    public PriceEntry {
        Objects.requireNonNull(provider);
        Objects.requireNonNull(unit);
        Objects.requireNonNull(valueUsd);
        Objects.requireNonNull(effectiveFrom);
    }

    PriceEntry fromEpoch() {
        return new PriceEntry(provider, unit, valueUsd, LocalDate.EPOCH);
    }
}
