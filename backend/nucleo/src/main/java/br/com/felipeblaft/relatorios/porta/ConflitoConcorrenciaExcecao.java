package br.com.felipeblaft.relatorios.porta;

/**
 * Gravação recusada porque outro canal gravou antes (bloqueio otimista ou índice único parcial). O
 * caso de uso traduz para o erro de negócio correspondente.
 */
public class ConflitoConcorrenciaExcecao extends RuntimeException {

    public ConflitoConcorrenciaExcecao(String detalhe) {
        super(detalhe, null, false, false);
    }
}
