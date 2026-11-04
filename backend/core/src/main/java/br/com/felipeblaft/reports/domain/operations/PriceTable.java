package br.com.felipeblaft.reports.domain.operations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Tabela de preços com vigência (FR-052): o preço de uma chamada é o da última entrada do mesmo
 * provedor e unidade cuja vigência já começou na data da chamada. Preço zero ou negativo é ignorado
 * com aviso. Lista vazia cai nas propriedades antigas, que valem desde 1970-01-01.
 */
public final class PriceTable {

    private final List<PriceEntry> entries;
    private final BigDecimal infrastructureMonthlyUsd;
    private final LocalDate infrastructureEffectiveFrom;
    private final List<String> warnings;

    private PriceTable(
            List<PriceEntry> entries,
            BigDecimal infrastructureMonthlyUsd,
            LocalDate infrastructureEffectiveFrom,
            List<String> warnings) {
        this.entries = List.copyOf(entries);
        this.infrastructureMonthlyUsd = infrastructureMonthlyUsd;
        this.infrastructureEffectiveFrom = infrastructureEffectiveFrom;
        this.warnings = List.copyOf(warnings);
    }

    public static PriceTable from(
            List<PriceEntry> configured,
            List<PriceEntry> legacy,
            BigDecimal infrastructureMonthlyUsd,
            LocalDate infrastructureEffectiveFrom) {
        List<String> warnings = new ArrayList<>();
        List<PriceEntry> valid = new ArrayList<>();
        for (PriceEntry entry : configured) {
            if (entry.valueUsd().signum() > 0) {
                valid.add(entry);
            } else {
                warnings.add(
                        "Preço do provedor "
                                + entry.provider()
                                + " ("
                                + entry.unit()
                                + ") ignorado: o valor precisa ser maior que zero.");
            }
        }
        if (configured.isEmpty()) {
            for (PriceEntry entry : legacy) {
                if (entry.valueUsd().signum() > 0) {
                    valid.add(entry.fromEpoch());
                }
            }
        }
        BigDecimal infrastructure =
                infrastructureMonthlyUsd == null ? BigDecimal.ZERO : infrastructureMonthlyUsd;
        if (infrastructure.signum() < 0) {
            warnings.add(
                    "Custo fixo mensal de infraestrutura ignorado: o valor não pode ser negativo.");
            infrastructure = BigDecimal.ZERO;
        }
        return new PriceTable(valid, infrastructure, infrastructureEffectiveFrom, warnings);
    }

    public Optional<BigDecimal> priceOn(String provider, PriceUnit unit, LocalDate date) {
        return entries.stream()
                .filter(e -> e.provider().equals(provider) && e.unit() == unit)
                .filter(e -> !e.effectiveFrom().isAfter(date))
                .max(Comparator.comparing(PriceEntry::effectiveFrom))
                .map(PriceEntry::valueUsd);
    }

    public BigDecimal infrastructureMonthlyUsdOn(LocalDate date) {
        LocalDate from =
                infrastructureEffectiveFrom == null ? LocalDate.EPOCH : infrastructureEffectiveFrom;
        return date.isBefore(from) ? BigDecimal.ZERO : infrastructureMonthlyUsd;
    }

    public List<PriceEntry> entries() {
        return entries;
    }

    public List<String> warnings() {
        return warnings;
    }
}
