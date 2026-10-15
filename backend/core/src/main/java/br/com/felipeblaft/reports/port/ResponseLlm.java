package br.com.felipeblaft.reports.port;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Saída já validada contra o schema, com a versão do prompt usada, tokens e custo estimado
 * (FR-056). {@code provedorModelo} é opaco para o núcleo.
 */
public record ResponseLlm<T>(
        T output,
        String versionPrompt,
        int inputTokens,
        int outputTokens,
        BigDecimal estimatedCost,
        String modelProvider,
        long durationMs) {

    public ResponseLlm {
        Objects.requireNonNull(output);
        Objects.requireNonNull(versionPrompt);
    }
}
