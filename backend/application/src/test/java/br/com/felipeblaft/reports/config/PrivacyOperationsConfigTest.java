package br.com.felipeblaft.reports.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.config.ApplicationProperties.Alerts;
import br.com.felipeblaft.reports.config.ApplicationProperties.Costs;
import br.com.felipeblaft.reports.config.ApplicationProperties.PriceProperty;
import br.com.felipeblaft.reports.config.ApplicationProperties.Privacy;
import br.com.felipeblaft.reports.config.ApplicationProperties.Retention;
import br.com.felipeblaft.reports.domain.operations.AlertThresholds;
import br.com.felipeblaft.reports.domain.operations.PriceTable;
import br.com.felipeblaft.reports.domain.operations.PriceUnit;
import br.com.felipeblaft.reports.domain.privacy.PolicyOrigin;
import br.com.felipeblaft.reports.domain.privacy.PrivacyOfficer;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import br.com.felipeblaft.reports.support.LogCapture;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Feature 007, FR-001/FR-002/FR-052/FR-055: as propriedades chegam brutas e o núcleo decide o
 * efetivo; valor inválido sobe a aplicação com {@code WARN} que cita a variável e nunca o valor.
 */
class PrivacyOperationsConfigTest {

    private static final Retention NO_RETENTION = new Retention("", "", "", "", "", "");
    private static final Alerts NO_ALERTS = new Alerts("", "", "", "", "", "", "");

    @Test
    @DisplayName("propriedades vazias dão os padrões, sem aviso")
    void emptyPropertiesUseDefaults() {
        try (LogCapture log = LogCapture.start()) {
            RetentionPolicy policy = PrivacyOperationsConfig.retentionPolicyOf(NO_RETENTION);
            AlertThresholds thresholds = PrivacyOperationsConfig.alertThresholdsOf(NO_ALERTS);

            assertThat(policy.transcriptionDays().value()).isEqualTo(30);
            assertThat(thresholds.queueMaxItems().value()).isEqualTo(50);
            assertThat(log.text()).doesNotContain("inválid");
        }
    }

    @Test
    @DisplayName("valor inválido de retenção avisa citando a variável e não o valor")
    void invalidRetentionWarnsWithTheVariableOnly() {
        try (LogCapture log = LogCapture.start()) {
            RetentionPolicy policy =
                    PrivacyOperationsConfig.retentionPolicyOf(
                            new Retention("45", "bad-value-xyz", "-9", "", "", ""));

            assertThat(policy.transcriptionDays().value()).isEqualTo(45);
            assertThat(policy.transcriptionDays().origin()).isEqualTo(PolicyOrigin.CONFIGURED);
            assertThat(policy.unapprovedPdfDays().value()).isEqualTo(30);
            assertThat(policy.cancelledReportDays().value()).isEqualTo(90);
            assertThat(log.text())
                    .contains("RETENTION_UNAPPROVED_PDF_DAYS")
                    .contains("RETENTION_CANCELLED_REPORT_DAYS")
                    .doesNotContain("bad-value-xyz")
                    .doesNotContain("-9");
        }
    }

    @Test
    @DisplayName("valor inválido de alerta avisa e usa o padrão")
    void invalidAlertWarns() {
        try (LogCapture log = LogCapture.start()) {
            AlertThresholds thresholds =
                    PrivacyOperationsConfig.alertThresholdsOf(
                            new Alerts("", "150", "", "", "", "", ""));

            assertThat(thresholds.providerFailureRatePercent().value()).isEqualTo(20);
            assertThat(log.text()).contains("ALERT_PROVIDER_FAILURE_RATE_PERCENT");
        }
    }

    @Test
    @DisplayName("preços configurados valem pela vigência e preço ruim é ignorado com aviso")
    void configuredPrices() {
        Costs costs =
                new Costs(
                        List.of(
                                new PriceProperty("p", "AUDIO_HOUR", "0.36", "2026-01-01"),
                                new PriceProperty("p", "AUDIO_HOUR", "0.50", "2026-11-01"),
                                new PriceProperty("p", "UNKNOWN_UNIT", "1", "2026-01-01"),
                                new PriceProperty("q", "AUDIO_HOUR", "zero", "2026-01-01")),
                        "12.5",
                        "2026-06-01");
        try (LogCapture log = LogCapture.start()) {
            PriceTable table = PrivacyOperationsConfig.priceTableOf(costs, List.of());

            assertThat(table.priceOn("p", PriceUnit.AUDIO_HOUR, LocalDate.parse("2026-11-02")))
                    .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.50"));
            assertThat(table.entries()).hasSize(2);
            assertThat(table.infrastructureMonthlyUsdOn(LocalDate.parse("2026-07-01")))
                    .isEqualByComparingTo("12.5");
            assertThat(log.text()).contains("COSTS_PRICES").doesNotContain("zero");
        }
    }

    @Test
    @DisplayName("sem lista configurada, valem as propriedades antigas desde 1970-01-01")
    void legacyPricesWhenNothingIsConfigured() {
        PriceTable table =
                PrivacyOperationsConfig.priceTableOf(
                        new Costs(null, "", ""),
                        List.of(
                                new br.com.felipeblaft.reports.domain.operations.PriceEntry(
                                        "model-a",
                                        PriceUnit.AUDIO_HOUR,
                                        new java.math.BigDecimal("0.04"),
                                        LocalDate.parse("2000-01-01"))));

        assertThat(table.priceOn("model-a", PriceUnit.AUDIO_HOUR, LocalDate.parse("1970-01-01")))
                .isPresent();
    }

    @Test
    @DisplayName("responsável pela LGPD ausente avisa; definido, não avisa")
    void officerWarning() {
        try (LogCapture log = LogCapture.start()) {
            PrivacyOfficer missing = PrivacyOperationsConfig.privacyOfficerOf(new Privacy("", ""));

            assertThat(missing.defined()).isFalse();
            assertThat(log.text()).contains("PRIVACY_OFFICER_NAME");
        }
        try (LogCapture log = LogCapture.start()) {
            PrivacyOfficer defined =
                    PrivacyOperationsConfig.privacyOfficerOf(
                            new Privacy("Encarregada Exemplo", "lgpd@example.test"));

            assertThat(defined.defined()).isTrue();
            assertThat(log.text()).doesNotContain("PRIVACY_OFFICER");
        }
    }
}
