package br.com.felipeblaft.reports.outbound.security;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * CPF com AES-256-GCM (IV aleatório de 12 bytes prefixado) e hashes HMAC-SHA-256 com o segredo do
 * servidor (Princípio IV, R-12, R-14). Cada uso do HMAC leva um prefixo de contexto próprio, para
 * que um hash de uma finalidade nunca coincida com o de outra.
 */
@Component
public class DataProtectionAdapter implements DataProtectionPort {

    private static final int SIZE_IV = 12;
    private static final int SIZE_TAG_BITS = 128;
    private static final int SIZE_TOKEN = 32;

    private final SecretKeySpec keyCpf;
    private final SecretKeySpec keyHmac;
    private final SecureRandom random = new SecureRandom();

    public DataProtectionAdapter(ApplicationProperties properties) {
        byte[] key = decodeKey(properties.security().cpfEncryptionKey());
        byte[] secret = hmacSecret(properties.security().hmacSecret());
        this.keyCpf = new SecretKeySpec(key, "AES");
        this.keyHmac = new SecretKeySpec(secret, "HmacSHA256");
    }

    private static byte[] decodeKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("CPF_ENCRYPTION_KEY não configurada");
        }
        byte[] key = Base64.getDecoder().decode(base64.strip());
        if (key.length != 32) {
            throw new IllegalStateException("CPF_ENCRYPTION_KEY deve ter 32 bytes em Base64");
        }
        return key;
    }

    private static byte[] hmacSecret(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("HMAC_SECRET deve ter pelo menos 32 bytes");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] encryptCpf(Cpf cpf) {
        try {
            byte[] iv = new byte[SIZE_IV];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keyCpf, new GCMParameterSpec(SIZE_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(cpf.digits().getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("falha ao cifrar", error);
        }
    }

    /** Uso restrito a testes e auditoria: o sistema nunca exibe o CPF. */
    String decryptCpf(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    keyCpf,
                    new GCMParameterSpec(SIZE_TAG_BITS, data, 0, SIZE_IV));
            return new String(
                    cipher.doFinal(data, SIZE_IV, data.length - SIZE_IV), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("falha ao decifrar", error);
        }
    }

    @Override
    public String hashCpf(Cpf cpf) {
        return hmac("cpf:" + cpf.digits());
    }

    @Override
    public String hashAccessCode(AccessCode code) {
        return hmac("codigo:" + code.value());
    }

    @Override
    public String originHash(Channel channel, String origin) {
        return hmac("origem:" + channel.name() + ":" + origin);
    }

    @Override
    public String generateSessionToken() {
        byte[] token = new byte[SIZE_TOKEN];
        random.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    @Override
    public String sessionTokenHash(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keyHmac);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }
}
