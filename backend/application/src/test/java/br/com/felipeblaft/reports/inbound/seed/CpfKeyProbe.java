package br.com.felipeblaft.reports.inbound.seed;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auxiliar dos testes da rotação das chaves do CPF: confere no banco, com as chaves dadas, se o CPF
 * decifra e se o resumo bate, sem nunca mostrar o valor.
 */
public final class CpfKeyProbe {

    /** Chave de cifra configurada nos testes (application-test). */
    public static final String CURRENT_ENCRYPTION_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    /** Segredo do resumo configurado nos testes. */
    public static final String CURRENT_HMAC_SECRET = "test-hmac-secret-with-more-than-32-bytes";

    /** Chave nova de cifra usada nos testes de rotação. */
    public static final String NEW_ENCRYPTION_KEY = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

    /** Segredo novo do resumo usado nos testes de rotação. */
    public static final String NEW_HMAC_SECRET = "novo-segredo-hmac-de-teste-com-mais-de-32-bytes";

    private CpfKeyProbe() {}

    /** O CPF (só dígitos) que a linha guarda cifrado, decifrado com a chave dada. */
    public static String decrypt(JdbcTemplate jdbc, UUID studentId, String base64Key) {
        byte[] data =
                jdbc.queryForObject(
                        "select cpf_encrypted from student where id = ?", byte[].class, studentId);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES"),
                    new GCMParameterSpec(128, data, 0, 12));
            return new String(cipher.doFinal(data, 12, data.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException error) {
            return null;
        }
    }

    /** O resumo que o segredo dado produz para o CPF (mesmo cálculo do adaptador de proteção). */
    public static String hash(String digits, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of()
                    .formatHex(mac.doFinal(("cpf:" + digits).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    public static String storedHash(JdbcTemplate jdbc, UUID studentId) {
        return jdbc.queryForObject(
                "select cpf_hash from student where id = ?", String.class, studentId);
    }
}
