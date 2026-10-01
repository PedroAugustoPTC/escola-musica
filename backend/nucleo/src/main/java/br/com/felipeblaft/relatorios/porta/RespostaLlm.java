package br.com.felipeblaft.relatorios.porta;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Saída já validada contra o schema, com a versão do prompt usada, tokens e custo estimado
 * (FR-056). {@code provedorModelo} é opaco para o núcleo.
 */
public record RespostaLlm<T>(
        T saida,
        String versaoPrompt,
        int tokensEntrada,
        int tokensSaida,
        BigDecimal custoEstimado,
        String provedorModelo,
        long duracaoMs) {

    public RespostaLlm {
        Objects.requireNonNull(saida);
        Objects.requireNonNull(versaoPrompt);
    }
}
