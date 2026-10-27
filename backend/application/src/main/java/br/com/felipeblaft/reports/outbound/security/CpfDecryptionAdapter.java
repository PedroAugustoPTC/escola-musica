package br.com.felipeblaft.reports.outbound.security;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.port.CpfDecryptionPort;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Decifra o CPF do cadastro de aluno em andamento no Telegram (AES-256-GCM, IV de 12 bytes
 * prefixado, o mesmo formato do {@code DataProtectionAdapter}), só na confirmação, para entregá-lo
 * ao caso de uso que grava o aluno. O valor nunca é registrado nem devolvido a interface alguma.
 */
@Component
class CpfDecryptionAdapter implements CpfDecryptionPort {

    private static final int SIZE_IV = 12;
    private static final int SIZE_TAG_BITS = 128;

    private final SecretKeySpec key;

    CpfDecryptionAdapter(ApplicationProperties properties) {
        String base64 = properties.security().cpfEncryptionKey();
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("CPF_ENCRYPTION_KEY não configurada");
        }
        byte[] bytes = Base64.getDecoder().decode(base64.strip());
        if (bytes.length != 32) {
            throw new IllegalStateException("CPF_ENCRYPTION_KEY deve ter 32 bytes em Base64");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    @Override
    public Cpf decrypt(byte[] encrypted) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(SIZE_TAG_BITS, encrypted, 0, SIZE_IV));
            String digits =
                    new String(
                            cipher.doFinal(encrypted, SIZE_IV, encrypted.length - SIZE_IV),
                            StandardCharsets.UTF_8);
            return Cpf.of(digits);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("falha ao decifrar", error);
        }
    }
}
