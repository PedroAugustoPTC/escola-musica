package br.com.felipeblaft.reports.domain.access;

import java.time.Duration;
import java.util.Objects;

/**
 * Política de um escopo de tentativas: máximo de falhas, duração do bloqueio e janela opcional. Sem
 * janela ({@code null}) a contagem é de falhas consecutivas até um sucesso, como na 001; com
 * janela, a contagem recomeça quando a primeira falha tem mais que a janela.
 */
public record AttemptPolicy(int maxAttempts, Duration lockDuration, Duration window) {

    public AttemptPolicy {
        Objects.requireNonNull(lockDuration);
        if (maxAttempts < 1 || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("política de tentativas inválida");
        }
        if (window != null && (window.isNegative() || window.isZero())) {
            throw new IllegalArgumentException("janela de tentativas inválida");
        }
    }

    /** Falhas consecutivas, sem janela. */
    public static AttemptPolicy consecutive(int maxAttempts, Duration lockDuration) {
        return new AttemptPolicy(maxAttempts, lockDuration, null);
    }

    /** Falhas dentro de uma janela de tempo. */
    public static AttemptPolicy windowed(int maxAttempts, Duration lockDuration, Duration window) {
        return new AttemptPolicy(maxAttempts, lockDuration, Objects.requireNonNull(window));
    }

    public boolean hasWindow() {
        return window != null;
    }
}
