package br.com.felipeblaft.reports.domain.operations;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.privacy.PolicyOrigin;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AlertThresholdsTest {

    @Test
    @DisplayName("os padrões são os de FR-055")
    void defaults() {
        AlertThresholds thresholds = AlertThresholds.from(Map.of());

        assertThat(thresholds.providerConsecutiveFailures().value()).isEqualTo(3);
        assertThat(thresholds.providerFailureRatePercent().value()).isEqualTo(20);
        assertThat(thresholds.providerWindowMinutes().value()).isEqualTo(15);
        assertThat(thresholds.queueMaxAgeMinutes().value()).isEqualTo(5);
        assertThat(thresholds.queueMaxItems().value()).isEqualTo(50);
        assertThat(thresholds.storageQuietMinutes().value()).isEqualTo(15);
        assertThat(thresholds.evaluationSeconds().value()).isEqualTo(60);
        assertThat(thresholds.providerWindow()).isEqualTo(Duration.ofMinutes(15));
        assertThat(thresholds.queueMaxAge()).isEqualTo(Duration.ofMinutes(5));
        assertThat(thresholds.warnings()).isEmpty();
    }

    @Test
    @DisplayName("valor válido é configurado")
    void configured() {
        AlertThresholds thresholds =
                AlertThresholds.from(
                        Map.of("ALERT_QUEUE_MAX_ITEMS", "80", "ALERT_EVALUATION_SECONDS", "30"));

        assertThat(thresholds.queueMaxItems().value()).isEqualTo(80);
        assertThat(thresholds.queueMaxItems().origin()).isEqualTo(PolicyOrigin.CONFIGURED);
        assertThat(thresholds.evaluationSeconds().value()).isEqualTo(30);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "x", "101"})
    @DisplayName(
            "taxa inválida (zero, negativa, não numérica ou acima de 100) volta ao padrão com aviso")
    void invalidRate(String raw) {
        AlertThresholds thresholds =
                AlertThresholds.from(Map.of("ALERT_PROVIDER_FAILURE_RATE_PERCENT", raw));

        assertThat(thresholds.providerFailureRatePercent().value()).isEqualTo(20);
        assertThat(thresholds.warnings()).hasSize(1);
        assertThat(thresholds.warnings().get(0)).contains("ALERT_PROVIDER_FAILURE_RATE_PERCENT");
    }

    @Test
    @DisplayName("contagem inválida volta ao padrão e o aviso cita a variável sem o valor")
    void invalidCount() {
        AlertThresholds thresholds = AlertThresholds.from(Map.of("ALERT_QUEUE_MAX_ITEMS", "-7"));

        assertThat(thresholds.queueMaxItems().value()).isEqualTo(50);
        assertThat(thresholds.warnings().get(0))
                .contains("ALERT_QUEUE_MAX_ITEMS")
                .doesNotContain("-7");
    }
}
