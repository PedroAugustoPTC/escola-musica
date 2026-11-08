package br.com.felipeblaft.reports.outbound.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import org.junit.jupiter.api.Test;

/** A confirmação do Telegram decifra o que o {@code DataProtectionAdapter} cifrou. */
class CpfDecryptionAdapterTest {

    private static final String HMAC_SECRET = "hmac-secret-for-tests-with-over-32-bytes";
    private static final String AUTH_HMAC_SECRET = "auth-hmac-secret-for-tests-with-over-32-bytes";
    private static final String CPF_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static ApplicationProperties properties(String cpfKey) {
        return new ApplicationProperties(
                null,
                null,
                null,
                null,
                null,
                new ApplicationProperties.Security(cpfKey, HMAC_SECRET, AUTH_HMAC_SECRET),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void decryptsWhatTheProtectionAdapterEncrypted() {
        DataProtectionAdapter protection = new DataProtectionAdapter(properties(CPF_KEY));
        CpfDecryptionAdapter decryption = new CpfDecryptionAdapter(properties(CPF_KEY));
        Cpf cpf = Cpf.of("529.982.247-25");

        assertThat(decryption.decrypt(protection.encryptCpf(cpf))).isEqualTo(cpf);
    }

    @Test
    void aTamperedValueFailsWithoutRevealingAnything() {
        DataProtectionAdapter protection = new DataProtectionAdapter(properties(CPF_KEY));
        CpfDecryptionAdapter decryption = new CpfDecryptionAdapter(properties(CPF_KEY));
        byte[] encrypted = protection.encryptCpf(Cpf.of("529.982.247-25"));
        encrypted[encrypted.length - 1] ^= 1;

        assertThatThrownBy(() -> decryption.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("529");
    }

    @Test
    void refusesToStartWithoutAValidKey() {
        assertThatThrownBy(() -> new CpfDecryptionAdapter(properties(null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CpfDecryptionAdapter(properties(" ")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CpfDecryptionAdapter(properties("c2hvcnQ=")))
                .isInstanceOf(IllegalStateException.class);
    }
}
