package br.com.felipeblaft.reports.port;

import java.math.BigDecimal;

/**
 * Provedor externo fora do ar, com timeout ou limite de requisições (429, 5xx, rede). Quando a
 * queda vem depois de uma chamada ao modelo que já consumiu (a de correção, após uma saída
 * inválida), leva o consumo já gasto para que o custo por relatório não o perca (FR-056); {@code
 * null} quando nada foi consumido.
 */
public class ProviderUnavailableException extends RuntimeException {

    private final transient Integer inputTokens;
    private final transient Integer outputTokens;
    private final transient BigDecimal estimatedCost;

    public ProviderUnavailableException(String detail) {
        this(detail, null, null, null);
    }

    public ProviderUnavailableException(
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
