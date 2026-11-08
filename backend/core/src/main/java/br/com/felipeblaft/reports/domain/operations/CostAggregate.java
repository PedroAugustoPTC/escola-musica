package br.com.felipeblaft.reports.domain.operations;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Agregado de custo e tempo (FR-053): quantidade, totais e médias (zero quando a contagem é zero).
 */
public record CostAggregate(
        int count, BigDecimal totalCostUsd, long totalDurationMs, int failedCalls) {

    public static final CostAggregate EMPTY = new CostAggregate(0, BigDecimal.ZERO, 0, 0);

    public CostAggregate {
        if (count < 0 || failedCalls < 0 || totalDurationMs < 0) {
            throw new IllegalArgumentException("agregado de custo negativo");
        }
    }

    public BigDecimal averageCostUsd() {
        return count == 0
                ? BigDecimal.ZERO
                : totalCostUsd.divide(BigDecimal.valueOf(count), 10, RoundingMode.HALF_EVEN);
    }

    public long averageDurationMs() {
        return count == 0 ? 0 : totalDurationMs / count;
    }

    public CostAggregate plus(BigDecimal costUsd, long durationMs, boolean failed) {
        return new CostAggregate(
                count + 1,
                totalCostUsd.add(costUsd),
                totalDurationMs + durationMs,
                failedCalls + (failed ? 1 : 0));
    }

    public CostAggregate combine(CostAggregate other) {
        return new CostAggregate(
                count + other.count,
                totalCostUsd.add(other.totalCostUsd),
                totalDurationMs + other.totalDurationMs,
                failedCalls + other.failedCalls);
    }

    /** O mesmo agregado com a contagem trocada (a unidade de contagem varia por visão). */
    public CostAggregate withCount(int newCount) {
        return new CostAggregate(newCount, totalCostUsd, totalDurationMs, failedCalls);
    }
}
