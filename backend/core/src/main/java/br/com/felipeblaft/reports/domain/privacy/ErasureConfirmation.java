package br.com.felipeblaft.reports.domain.privacy;

import java.time.Duration;
import java.time.Instant;

/**
 * Confirmação pendente de uma eliminação: resumo (HMAC) do token e validade de 10 minutos (FR-021).
 * O token em claro nunca é guardado.
 */
public record ErasureConfirmation(String tokenHash, Instant expiresAt) {

    public static final Duration VALIDITY = Duration.ofMinutes(10);
    public static final String CONFIRMATION_WORD = "ELIMINAR";

    public static ErasureConfirmation issue(String tokenHash, Instant now) {
        return new ErasureConfirmation(tokenHash, now.plus(VALIDITY));
    }

    /** Verdadeiro quando o resumo confere e a validade ainda não terminou. */
    public boolean matches(String candidateHash, Instant now) {
        return candidateHash != null && tokenHash.equals(candidateHash) && now.isBefore(expiresAt);
    }

    /** A palavra de confirmação é exatamente {@code ELIMINAR}, sem espaço nem variação de caixa. */
    public static boolean isConfirmationWord(String word) {
        return CONFIRMATION_WORD.equals(word);
    }

    @Override
    public String toString() {
        return "ErasureConfirmation[expiresAt=" + expiresAt + "]";
    }
}
