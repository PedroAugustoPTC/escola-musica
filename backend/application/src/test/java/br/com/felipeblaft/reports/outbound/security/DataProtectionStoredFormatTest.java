package br.com.felipeblaft.reports.outbound.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.Channel;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Feature 003: os hashes do código de acesso e da origem ficam gravados no banco. O prefixo que
 * entra no HMAC faz parte do formato gravado; trocá-lo (ainda que seja só um nome) invalidaria
 * todos os códigos e vínculos já emitidos. Este teste fixa o formato com um cálculo independente.
 */
class DataProtectionStoredFormatTest {

    private static final String HMAC_SECRET = "hmac-secret-for-tests-with-over-32-bytes";
    private static final String CPF_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final DataProtectionAdapter adapter =
            new DataProtectionAdapter(
                    new ApplicationProperties(
                            null,
                            null,
                            null,
                            null,
                            null,
                            new ApplicationProperties.Security(
                                    CPF_KEY, HMAC_SECRET, "auth-secret-for-tests-with-over-32-bytes"),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null));

    private static String hmac(String text) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void accessCodeHashKeepsTheStoredFormat() throws Exception {
        AccessCode code = AccessCode.retry("K7QM-2XPA").orElseThrow();

        assertThat(adapter.hashAccessCode(code)).isEqualTo(hmac("codigo:K7QM2XPA"));
    }

    @Test
    void originHashKeepsTheStoredFormat() throws Exception {
        assertThat(adapter.originHash(Channel.TELEGRAM, "7001"))
                .isEqualTo(hmac("origem:TELEGRAM:7001"));
    }
}
