package br.com.felipeblaft.relatorios.saida.seguranca;

import br.com.felipeblaft.relatorios.porta.ProtecaoDadosPorta;

/** Expõe aos testes a decifragem do CPF, que o sistema nunca usa em produção. */
public final class AcessoDecifragem {

    private AcessoDecifragem() {}

    public static String decifrar(ProtecaoDadosPorta protecao, byte[] dados) {
        return ((ProtecaoDadosAdaptador) protecao).decifrarCpf(dados);
    }
}
