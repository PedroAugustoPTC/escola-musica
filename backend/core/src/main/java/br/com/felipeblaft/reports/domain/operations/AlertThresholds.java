package br.com.felipeblaft.reports.domain.operations;

import br.com.felipeblaft.reports.domain.privacy.PolicyInput;
import br.com.felipeblaft.reports.domain.privacy.PolicyValue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Limites dos alertas operacionais (FR-055), com os mesmos critérios de valor inválido da retenção.
 */
public record AlertThresholds(
        PolicyValue providerConsecutiveFailures,
        PolicyValue providerFailureRatePercent,
        PolicyValue providerWindowMinutes,
        PolicyValue queueMaxAgeMinutes,
        PolicyValue queueMaxItems,
        PolicyValue storageQuietMinutes,
        PolicyValue evaluationSeconds,
        List<String> warnings) {

    public AlertThresholds {
        warnings = List.copyOf(warnings);
    }

    public static AlertThresholds from(Map<String, String> source) {
        List<String> warnings = new ArrayList<>();
        return new AlertThresholds(
                PolicyInput.read(source, "ALERT_PROVIDER_CONSECUTIVE_FAILURES", 3, 1000, warnings),
                PolicyInput.read(source, "ALERT_PROVIDER_FAILURE_RATE_PERCENT", 20, 100, warnings),
                PolicyInput.read(source, "ALERT_PROVIDER_WINDOW_MINUTES", 15, 10_080, warnings),
                PolicyInput.read(source, "ALERT_QUEUE_MAX_AGE_MINUTES", 5, 10_080, warnings),
                PolicyInput.read(source, "ALERT_QUEUE_MAX_ITEMS", 50, 1_000_000, warnings),
                PolicyInput.read(source, "ALERT_STORAGE_QUIET_MINUTES", 15, 10_080, warnings),
                PolicyInput.read(source, "ALERT_EVALUATION_SECONDS", 60, 86_400, warnings),
                warnings);
    }

    public Duration providerWindow() {
        return Duration.ofMinutes(providerWindowMinutes.value());
    }

    public Duration queueMaxAge() {
        return Duration.ofMinutes(queueMaxAgeMinutes.value());
    }

    public Duration storageQuiet() {
        return Duration.ofMinutes(storageQuietMinutes.value());
    }

    public Duration evaluationInterval() {
        return Duration.ofSeconds(evaluationSeconds.value());
    }
}
