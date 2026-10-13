package br.com.felipeblaft.reports.saida.seguranca;

import br.com.felipeblaft.reports.port.DataProtectionPort;

/** Expõe aos testes a decifragem do CPF, que o sistema nunca usa em produção. */
public final class AcessoDecifragem {

    private AcessoDecifragem() {}

    public static String decifrar(DataProtectionPort protecao, byte[] dados) {
        return ((ProtecaoDadosAdaptador) protecao).decifrarCpf(dados);
    }
}
