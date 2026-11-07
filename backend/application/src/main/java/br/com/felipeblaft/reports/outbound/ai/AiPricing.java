package br.com.felipeblaft.reports.outbound.ai;

import br.com.felipeblaft.reports.domain.operations.PriceTable;
import br.com.felipeblaft.reports.domain.operations.PriceUnit;
import br.com.felipeblaft.reports.port.ClockPort;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Preço unitário vigente na data da chamada (FR-052): consulta a {@link PriceTable} do provedor e
 * da unidade, na data de hoje no fuso da escola. Sem entrada vigente, vale o preço de reserva (as
 * propriedades antigas). O custo já gravado nunca é recalculado.
 */
final class AiPricing {

    private final PriceTable table;
    private final ClockPort clock;
    private final String provider;

    AiPricing(PriceTable table, ClockPort clock, String provider) {
        this.table = Objects.requireNonNull(table);
        this.clock = Objects.requireNonNull(clock);
        this.provider = Objects.requireNonNull(provider);
    }

    BigDecimal price(PriceUnit unit, BigDecimal fallback) {
        return table.priceOn(provider, unit, clock.todayInSchoolTimezone())
                .orElse(Objects.requireNonNullElse(fallback, BigDecimal.ZERO));
    }
}
