package br.com.felipeblaft.reports.domain.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceTableTest {

    private static PriceEntry entry(String provider, PriceUnit unit, String value, String from) {
        return new PriceEntry(provider, unit, new BigDecimal(value), LocalDate.parse(from));
    }

    @Test
    @DisplayName("o preço vigente é o da última entrada já iniciada; a fronteira é inclusiva")
    void effectivePriceByDate() {
        PriceTable table =
                PriceTable.from(
                        List.of(
                                entry("p", PriceUnit.AUDIO_HOUR, "0.36", "2026-01-01"),
                                entry("p", PriceUnit.AUDIO_HOUR, "0.50", "2026-11-01")),
                        List.of(),
                        BigDecimal.ZERO,
                        null);

        assertThat(table.priceOn("p", PriceUnit.AUDIO_HOUR, LocalDate.parse("2026-10-31")))
                .contains(new BigDecimal("0.36"));
        assertThat(table.priceOn("p", PriceUnit.AUDIO_HOUR, LocalDate.parse("2026-11-01")))
                .contains(new BigDecimal("0.50"));
        assertThat(table.priceOn("p", PriceUnit.AUDIO_HOUR, LocalDate.parse("2025-12-31")))
                .isEmpty();
        assertThat(table.priceOn("other", PriceUnit.AUDIO_HOUR, LocalDate.parse("2026-11-01")))
                .isEmpty();
    }

    @Test
    @DisplayName("lista vazia cai nas propriedades antigas, com vigência 1970-01-01")
    void emptyListFallsBackToLegacy() {
        PriceTable table =
                PriceTable.from(
                        List.of(),
                        List.of(entry("legacy", PriceUnit.AUDIO_HOUR, "0.40", "2000-05-05")),
                        BigDecimal.ZERO,
                        null);

        assertThat(table.priceOn("legacy", PriceUnit.AUDIO_HOUR, LocalDate.parse("2026-01-01")))
                .contains(new BigDecimal("0.40"));
        assertThat(table.entries())
                .extracting(PriceEntry::effectiveFrom)
                .containsOnly(LocalDate.EPOCH);
    }

    @Test
    @DisplayName("lista configurada tem prioridade sobre o legado")
    void configuredWinsOverLegacy() {
        PriceTable table =
                PriceTable.from(
                        List.of(entry("p", PriceUnit.AUDIO_HOUR, "1.00", "2026-01-01")),
                        List.of(entry("legacy", PriceUnit.AUDIO_HOUR, "0.40", "1970-01-01")),
                        BigDecimal.ZERO,
                        null);

        assertThat(table.entries()).hasSize(1);
    }

    @Test
    @DisplayName("sem nada configurado a tabela é vazia, sem erro")
    void emptyTable() {
        assertThat(PriceTable.from(List.of(), List.of(), BigDecimal.ZERO, null).entries())
                .isEmpty();
    }

    @Test
    @DisplayName("preço zero ou negativo é ignorado com aviso que cita o provedor, nunca o valor")
    void invalidPriceIgnored() {
        PriceTable table =
                PriceTable.from(
                        List.of(
                                entry("prov", PriceUnit.AUDIO_HOUR, "0", "2026-01-01"),
                                entry("prov", PriceUnit.INPUT_MILLION_TOKENS, "-1", "2026-01-01"),
                                entry(
                                        "prov",
                                        PriceUnit.OUTPUT_MILLION_TOKENS,
                                        "2.5",
                                        "2026-01-01")),
                        List.of(),
                        BigDecimal.ZERO,
                        null);

        assertThat(table.entries()).hasSize(1);
        assertThat(table.warnings()).hasSize(2);
        assertThat(table.warnings())
                .allSatisfy(w -> assertThat(w).contains("prov").doesNotContain("-1"));
    }

    @Test
    @DisplayName("o custo fixo mensal vale a partir da vigência e é zero antes dela")
    void monthlyInfrastructure() {
        PriceTable table =
                PriceTable.from(
                        List.of(),
                        List.of(),
                        new BigDecimal("12.50"),
                        LocalDate.parse("2026-06-01"));

        assertThat(table.infrastructureMonthlyUsdOn(LocalDate.parse("2026-05-31")))
                .isEqualByComparingTo("0");
        assertThat(table.infrastructureMonthlyUsdOn(LocalDate.parse("2026-06-01")))
                .isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("custo fixo negativo é ignorado com aviso")
    void negativeInfrastructure() {
        PriceTable table =
                PriceTable.from(
                        List.of(), List.of(), new BigDecimal("-3"), LocalDate.parse("2026-06-01"));

        assertThat(table.infrastructureMonthlyUsdOn(LocalDate.parse("2026-07-01")))
                .isEqualByComparingTo("0");
        assertThat(table.warnings()).hasSize(1);
    }
}
