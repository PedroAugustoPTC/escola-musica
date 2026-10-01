package br.com.felipeblaft.relatorios.porta;

/** Provedor externo fora do ar, com timeout ou limite de requisições (429, 5xx, rede). */
public class ProvedorIndisponivelExcecao extends RuntimeException {

    public ProvedorIndisponivelExcecao(String detalhe) {
        super(detalhe, null, false, false);
    }
}
