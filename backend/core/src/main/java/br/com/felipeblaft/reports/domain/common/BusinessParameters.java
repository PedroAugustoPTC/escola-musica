package br.com.felipeblaft.reports.domain.common;

import java.time.Duration;
import java.util.Objects;

/**
 * Limites e prazos configuráveis das regras de negócio (spec: FR-005, FR-007, FR-008, FR-015,
 * FR-040). O núcleo não lê configuração; a aplicação monta este objeto a partir do ambiente.
 */
public record BusinessParameters(
        Duration codeValidity,
        int maxAccessAttempts,
        Duration lockDuration,
        Duration webSessionInactivity,
        Duration pendingAudioExpiry,
        long audioLimitBytes) {

    public static final Duration CODE_RENEWAL_INTERVAL = Duration.ofHours(1);
    public static final Duration SESSION_RENEWAL_INTERVAL = Duration.ofMinutes(1);

    public BusinessParameters {
        Objects.requireNonNull(codeValidity);
        Objects.requireNonNull(lockDuration);
        Objects.requireNonNull(webSessionInactivity);
        Objects.requireNonNull(pendingAudioExpiry);
        if (maxAccessAttempts < 1 || audioLimitBytes < 1) {
            throw new IllegalArgumentException("parâmetros de negócio inválidos");
        }
    }

    /** Padrões da spec: 7 dias, 5 tentativas, 15 min, 12 h, 30 min e 50 MB. */
    public static BusinessParameters defaults() {
        return new BusinessParameters(
                Duration.ofDays(7),
                5,
                Duration.ofMinutes(15),
                Duration.ofHours(12),
                Duration.ofMinutes(30),
                50L * 1024 * 1024);
    }

    public int audioLimitMb() {
        return (int) (audioLimitBytes / (1024 * 1024));
    }
}
