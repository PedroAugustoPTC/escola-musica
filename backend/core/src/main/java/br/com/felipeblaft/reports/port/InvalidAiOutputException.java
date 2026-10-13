package br.com.felipeblaft.reports.port;

import java.math.BigDecimal;

/**
 * Saída da IA fora do schema mesmo após a chamada de correção (FR-022, R-09). Leva o consumo das
 * chamadas feitas ao modelo, original e de correção, para que a falha também conte no custo
 * (FR-056); {@code null} quando o consumo não é conhecido.
 */
public class InvalidAiOutputException extends RuntimeException {

    private final transient Integer inputTokens;
    private final transient Integer outputTokens;
    private final transient BigDecimal estimatedCost;

    public InvalidAiOutputException(String detail) {
        this(detail, null, null, null);
    }

    public InvalidAiOutputException(
            String detail, Integer inputTokens, Integer outputTokens, BigDecimal estimatedCost) {
        super(detail, null, false, false);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estimatedCost = estimatedCost;
    }

    public Integer inputTokens() {
        return inputTokens;
    }

    public Integer outputTokens() {
        return outputTokens;
    }

    public BigDecimal estimatedCost() {
        return estimatedCost;
    }
}
