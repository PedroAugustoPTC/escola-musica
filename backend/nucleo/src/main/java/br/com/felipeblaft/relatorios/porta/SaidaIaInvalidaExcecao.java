package br.com.felipeblaft.relatorios.porta;

import java.math.BigDecimal;

/**
 * Saída da IA fora do schema mesmo após a chamada de correção (FR-022, R-09). Leva o consumo das
 * chamadas feitas ao modelo, original e de correção, para que a falha também conte no custo
 * (FR-056); {@code null} quando o consumo não é conhecido.
 */
public class SaidaIaInvalidaExcecao extends RuntimeException {

    private final transient Integer tokensEntrada;
    private final transient Integer tokensSaida;
    private final transient BigDecimal custoEstimado;

    public SaidaIaInvalidaExcecao(String detalhe) {
        this(detalhe, null, null, null);
    }

    public SaidaIaInvalidaExcecao(
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
