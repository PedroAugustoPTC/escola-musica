package br.com.felipeblaft.reports.outbound.security;

import br.com.felipeblaft.reports.port.DataProtectionPort;

/** Expõe aos testes a decifragem do CPF, que o sistema nunca usa em produção. */
public final class AccessDecryption {

    private AccessDecryption() {}

    public static String decrypt(DataProtectionPort protection, byte[] data) {
        return ((DataProtectionAdapter) protection).decryptCpf(data);
    }
}
