package br.com.felipeblaft.relatorios.porta;

import java.math.BigDecimal;

/**
 * Provedor externo fora do ar, com timeout ou limite de requisições (429, 5xx, rede). Quando a
 * queda vem depois de uma chamada ao modelo que já consumiu (a de correção, após uma saída
 * inválida), leva o consumo já gasto para que o custo por relatório não o perca (FR-056); {@code
 * null} quando nada foi consumido.
 */
public class ProvedorIndisponivelExcecao extends RuntimeException {

    private final transient Integer tokensEntrada;
    private final transient Integer tokensSaida;
    private final transient BigDecimal custoEstimado;

    public ProvedorIndisponivelExcecao(String detalhe) {
        this(detalhe, null, null, null);
    }

    public ProvedorIndisponivelExcecao(
            String detalhe, Integer tokensEntrada, Integer tokensSaida, BigDecimal custoEstimado) {
        super(detalhe, null, false, false);
        this.tokensEntrada = tokensEntrada;
        this.tokensSaida = tokensSaida;
        this.custoEstimado = custoEstimado;
    }

    public Integer tokensEntrada() {
        return tokensEntrada;
    }

    public Integer tokensSaida() {
        return tokensSaida;
    }

    public BigDecimal custoEstimado() {
        return custoEstimado;
    }
}
