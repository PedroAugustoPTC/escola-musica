package br.com.felipeblaft.reports.outbound.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.access.ActivationCode;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Feature 005, R-09 e R-12: o hash da origem por escopo e o hash do código de ativação usam
 * subchaves HKDF-SHA-256 (rótulos {@code origin} e {@code activation}) do {@code AUTH_HMAC_SECRET},
 * nunca o {@code HMAC_SECRET} do CPF; o endereço de rede nunca é guardado; o segredo ausente, curto
 * ou igual ao do CPF impede a subida.
 */
class DataProtectionAdapterOriginHashTest {

    private static final String CPF_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String HMAC_SECRET = "hmac-secret-for-tests-with-over-32-bytes";
    private static final String AUTH_SECRET = "auth-hmac-secret-for-tests-with-over-32-bytes";
    private static final String NETWORK_ADDRESS = "198.51.100.77";

    private static DataProtectionAdapter adapter(String hmacSecret, String authSecret) {
        return new DataProtectionAdapter(
                new ApplicationProperties(
                        null,
                        null,
                        null,
                        null,
                        null,
                        new ApplicationProperties.Security(CPF_KEY, hmacSecret, authSecret),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    private final DataProtectionAdapter adapter = adapter(HMAC_SECRET, AUTH_SECRET);

    // ---- cálculo independente --------------------------------------------------------------

    private static byte[] hmacBytes(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    /** HKDF-SHA-256 (RFC 5869), sem sal, com um único bloco de saída (32 bytes). */
    private static byte[] subKey(String secret, String label) throws Exception {
        byte[] extract = hmacBytes(new byte[32], secret.getBytes(StandardCharsets.UTF_8));
        byte[] info = label.getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[info.length + 1];
        System.arraycopy(info, 0, input, 0, info.length);
        input[info.length] = 1;
        return hmacBytes(extract, input);
    }

    private static String hmacHex(byte[] key, String text) throws Exception {
        return HexFormat.of().formatHex(hmacBytes(key, text.getBytes(StandardCharsets.UTF_8)));
    }

    private static ActivationCode activation(String value) {
        ActivationCode code = mock(ActivationCode.class);
        when(code.value()).thenReturn(value);
        return code;
    }

    // ---- origem por escopo -----------------------------------------------------------------

    @Test
    void originHashByScopeUsesTheOriginSubKeyOfTheAuthSecret() throws Exception {
        String expected =
                hmacHex(subKey(AUTH_SECRET, "origin"), "origin:ADMIN_SIGN_IN:" + NETWORK_ADDRESS);

        assertThat(adapter.originHash(AttemptScope.ADMIN_SIGN_IN, NETWORK_ADDRESS))
                .isEqualTo(expected)
                .hasSize(64);
    }

    @Test
    void originHashIsStablePerScopeAndKeyAndDiffersAcrossThem() {
        String signIn = adapter.originHash(AttemptScope.ADMIN_SIGN_IN, NETWORK_ADDRESS);

        assertThat(adapter.originHash(AttemptScope.ADMIN_SIGN_IN, NETWORK_ADDRESS))
                .isEqualTo(signIn);
        assertThat(adapter.originHash(AttemptScope.ADMIN_ACTIVATION, NETWORK_ADDRESS))
                .isNotEqualTo(signIn);
        assertThat(adapter.originHash(AttemptScope.TEACHER_CPF_PROBE, NETWORK_ADDRESS))
                .isNotEqualTo(signIn);
        assertThat(adapter.originHash(AttemptScope.ADMIN_SIGN_IN, "198.51.100.78"))
                .isNotEqualTo(signIn);
    }

    @Test
    void originHashNeverContainsTheNetworkAddressNorTheSecrets() {
        String hash = adapter.originHash(AttemptScope.ADMIN_SIGN_IN, NETWORK_ADDRESS);

        assertThat(hash).matches("[0-9a-f]{64}").doesNotContain("198").doesNotContain("51.100");
    }

    @Test
    void originHashDoesNotDependOnTheCpfSecret() {
        String withOtherCpfSecret =
                adapter("another-hmac-secret-with-over-32-bytes-xx", AUTH_SECRET)
                        .originHash(AttemptScope.ADMIN_SIGN_IN, NETWORK_ADDRESS);

        assertThat(withOtherCpfSecret)
                .isEqualTo(adapter.originHash(AttemptScope.ADMIN_SIGN_IN, NETWORK_ADDRESS));
    }

    @Test
    void originHashChangesWhenTheAuthSecretChanges() {
        String withOtherAuthSecret =
                adapter(HMAC_SECRET, "another-auth-secret-with-over-32-bytes-xx")
                        .originHash(AttemptScope.ADMIN_SIGN_IN, NETWORK_ADDRESS);

        assertThat(withOtherAuthSecret)
                .isNotEqualTo(adapter.originHash(AttemptScope.ADMIN_SIGN_IN, NETWORK_ADDRESS));
    }

    @Test
    void originHashByScopeDiffersFromTheChannelOriginHashOfThe001() {
        // o hash por canal (001) continua com o HMAC_SECRET e já está gravado: não muda
        assertThat(adapter.originHash(AttemptScope.TEACHER_CODE, "7001"))
                .isNotEqualTo(adapter.originHash(Channel.TELEGRAM, "7001"));
    }

    // ---- código de ativação ----------------------------------------------------------------

    @Test
    void activationHashUsesTheActivationSubKeyAndContextPrefix() throws Exception {
        String expected = hmacHex(subKey(AUTH_SECRET, "activation"), "activation:K7QM2XPA9B3D");

        assertThat(adapter.hashActivationCode(activation("K7QM2XPA9B3D")))
                .isEqualTo(expected)
                .hasSize(64);
    }

    @Test
    void activationHashIsKeyedNotAPlainDigest() throws Exception {
        String plain =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                "activation:K7QM2XPA9B3D"
                                                        .getBytes(StandardCharsets.UTF_8)));

        assertThat(adapter.hashActivationCode(activation("K7QM2XPA9B3D"))).isNotEqualTo(plain);
    }

    @Test
    void rotatingTheCpfSecretDoesNotInvalidateTheActivationCode() {
        String before = adapter.hashActivationCode(activation("K7QM2XPA9B3D"));
        String afterRotation =
                adapter("rotated-hmac-secret-with-over-32-bytes-xx", AUTH_SECRET)
                        .hashActivationCode(activation("K7QM2XPA9B3D"));

        assertThat(afterRotation).isEqualTo(before);
    }

    @Test
    void activationHashDiffersFromAccessCodeHashAndFromOriginHashOfTheSameText() {
        String text = "K7QM2XPA";
        ActivationCode activationCode = activation(text);

        assertThat(adapter.hashActivationCode(activationCode))
                .isNotEqualTo(adapter.hashAccessCode(AccessCode.of(text)))
                .isNotEqualTo(adapter.originHash(AttemptScope.ADMIN_ACTIVATION, text));
    }

    @Test
    void differentActivationCodesGetDifferentHashes() {
        assertThat(adapter.hashActivationCode(activation("K7QM2XPA9B3D")))
                .isNotEqualTo(adapter.hashActivationCode(activation("K7QM2XPA9B3E")));
    }

    // ---- segredo na subida -----------------------------------------------------------------

    @Test
    void startupFailsWhenTheAuthSecretIsMissing() {
        assertThatThrownBy(() -> adapter(HMAC_SECRET, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_HMAC_SECRET");
        assertThatThrownBy(() -> adapter(HMAC_SECRET, "   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_HMAC_SECRET");
        assertThatThrownBy(() -> adapter(HMAC_SECRET, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_HMAC_SECRET");
    }

    @Test
    void startupFailsWhenTheAuthSecretHasFewerThan32Bytes() {
        assertThatThrownBy(() -> adapter(HMAC_SECRET, "x".repeat(31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_HMAC_SECRET");
        assertThat(adapter(HMAC_SECRET, "x".repeat(32))).isNotNull();
    }

    @Test
    void startupFailsWhenTheAuthSecretIsEqualToTheCpfSecret() {
        assertThatThrownBy(() -> adapter(HMAC_SECRET, HMAC_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_HMAC_SECRET")
                .hasMessageContaining("HMAC_SECRET");
    }

    @Test
    void startupFailureMessageNeverRevealsTheSecrets() {
        assertThatThrownBy(() -> adapter(HMAC_SECRET, HMAC_SECRET))
                .hasMessageNotContaining(HMAC_SECRET);
        assertThatThrownBy(() -> adapter(HMAC_SECRET, "short"))
                .hasMessageNotContaining("short")
                .hasMessageNotContaining(HMAC_SECRET);
    }
}
