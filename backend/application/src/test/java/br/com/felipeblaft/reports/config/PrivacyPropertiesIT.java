package br.com.felipeblaft.reports.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.operations.AlertThresholds;
import br.com.felipeblaft.reports.domain.operations.PriceTable;
import br.com.felipeblaft.reports.domain.operations.PriceUnit;
import br.com.felipeblaft.reports.domain.privacy.PolicyOrigin;
import br.com.felipeblaft.reports.domain.privacy.PrivacyOfficer;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Feature 007: as propriedades {@code reports.retention.*}, {@code reports.alerts.*}, {@code
 * reports.costs.*} e {@code reports.privacy.*} chegam brutas ao núcleo pelo contexto real; valor
 * inválido não impede a subida (o contexto sobe) e vale o padrão.
 */
@TestPropertySource(
        properties = {
            "reports.retention.transcription-days=45",
            "reports.retention.cancelled-report-days=abc",
            "reports.alerts.queue-max-items=-3",
            "reports.alerts.evaluation-seconds=30",
            "reports.costs.prices[0].provider=prov-a",
            "reports.costs.prices[0].unit=AUDIO_HOUR",
            "reports.costs.prices[0].value-usd=0.36",
            "reports.costs.prices[0].effective-from=2026-01-01",
            "reports.privacy.officer-name=Encarregada Exemplo",
            "reports.privacy.officer-contact=lgpd@example.test"
        })
class PrivacyPropertiesIT extends IntegrationBase {

    @Autowired RetentionPolicy retention;
    @Autowired AlertThresholds alerts;
    @Autowired PriceTable prices;
    @Autowired PrivacyOfficer officer;

    @Test
    void validValuesAreConfiguredAndInvalidOnesFallBackToTheDefault() {
        assertThat(retention.transcriptionDays().value()).isEqualTo(45);
        assertThat(retention.transcriptionDays().origin()).isEqualTo(PolicyOrigin.CONFIGURED);
        assertThat(retention.cancelledReportDays().value()).isEqualTo(90);
        assertThat(retention.cancelledReportDays().origin()).isEqualTo(PolicyOrigin.DEFAULT);
        assertThat(retention.unapprovedPdfDays().value()).isEqualTo(30);
        assertThat(retention.warnings()).hasSize(1);
        assertThat(alerts.queueMaxItems().value()).isEqualTo(50);
        assertThat(alerts.evaluationSeconds().value()).isEqualTo(30);
    }

    @Test
    void indexedPricesArriveAsATable() {
        assertThat(prices.priceOn("prov-a", PriceUnit.AUDIO_HOUR, LocalDate.parse("2026-02-01")))
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("0.36"));
    }

    @Test
    void privacyOfficerComesFromTheEnvironment() {
        assertThat(officer.defined()).isTrue();
        assertThat(officer.name()).isEqualTo("Encarregada Exemplo");
    }
}
