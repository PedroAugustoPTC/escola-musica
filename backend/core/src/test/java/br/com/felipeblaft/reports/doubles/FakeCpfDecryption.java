package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.port.CpfDecryptionPort;
import java.nio.charset.StandardCharsets;

/**
 * Decifra o que o {@link FakeDataProtection} "cifrou" (prefixo mais os dígitos invertidos), sem
 * criptografia real. Conta as chamadas: só a confirmação pode decifrar.
 */
public class FakeCpfDecryption implements CpfDecryptionPort {

    private static final String PREFIX = "encrypted:";

    private int decryptions;

    @Override
    public Cpf decrypt(byte[] encrypted) {
        decryptions++;
        String text = new String(encrypted, StandardCharsets.UTF_8);
        if (!text.startsWith(PREFIX)) {
            throw new IllegalArgumentException("conteúdo que não foi cifrado pelo dublê");
        }
        return Cpf.of(new StringBuilder(text.substring(PREFIX.length())).reverse().toString());
    }

    public int decryptions() {
        return decryptions;
    }
}
