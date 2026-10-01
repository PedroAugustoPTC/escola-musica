package br.com.felipeblaft.relatorios.porta;

/** Saída da IA fora do schema mesmo após a chamada de correção (FR-022, R-09). */
public class SaidaIaInvalidaExcecao extends RuntimeException {

    public SaidaIaInvalidaExcecao(String detalhe) {
        super(detalhe, null, false, false);
    }
}
