package br.com.felipeblaft.reports.outbound.security;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.port.CpfKeyCollisionException;
import br.com.felipeblaft.reports.port.CpfKeyRotationPort;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Rotação das chaves do CPF (FR-020, contracts/initial-load.md §5). As chaves chegam por variáveis
 * de ambiente (nunca por argumento, para não irem ao histórico do shell): {@code
 * ROTATE_OLD_CPF_ENCRYPTION_KEY}, {@code ROTATE_NEW_CPF_ENCRYPTION_KEY}, {@code
 * ROTATE_OLD_HMAC_SECRET} e {@code ROTATE_NEW_HMAC_SECRET}; a chave antiga omitida vale a
 * configurada no servidor. Cada CPF é decifrado em memória, recifrado com a chave nova e tem o
 * resumo recalculado com o segredo novo; os resumos novos são conferidos contra colisão antes de
 * qualquer gravação. Os rascunhos de cadastro do Telegram (cifrados com a chave antiga) são
 * descartados na mesma transação. Nada é registrado, impresso ou gravado em claro: só contagens.
 * Roda na transação do chamador (o caso de uso a desfaz em {@code --simulate} ou em colisão).
 */
@Component
class CpfKeyRotationAdapter implements CpfKeyRotationPort {

    static final String OLD_ENCRYPTION_KEY = "ROTATE_OLD_CPF_ENCRYPTION_KEY";
    static final String NEW_ENCRYPTION_KEY = "ROTATE_NEW_CPF_ENCRYPTION_KEY";
    static final String OLD_HMAC_SECRET = "ROTATE_OLD_HMAC_SECRET";
    static final String NEW_HMAC_SECRET = "ROTATE_NEW_HMAC_SECRET";

    private static final int BATCH = 500;
    private static final int SIZE_IV = 12;
    private static final int SIZE_TAG_BITS = 128;

    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final ApplicationProperties properties;
    private final SecureRandom random = new SecureRandom();

    CpfKeyRotationAdapter(
            JdbcTemplate jdbc, Environment environment, ApplicationProperties properties) {
        this.jdbc = jdbc;
        this.environment = environment;
        this.properties = properties;
    }

    /** Linha lida do banco. */
    private record Row(UUID id, byte[] encrypted, String hash) {}

    @Override
    public Report rotate(Options options) {
        SecretKeySpec oldKey =
                encryptionKey(OLD_ENCRYPTION_KEY, properties.security().cpfEncryptionKey());
        SecretKeySpec newKey =
                options.rotateEncryptionKey() ? encryptionKey(NEW_ENCRYPTION_KEY, null) : oldKey;
        SecretKeySpec oldSecret = hmacKey(OLD_HMAC_SECRET, properties.security().hmacSecret());
        SecretKeySpec newSecret =
                options.rotateDigestSecret() ? hmacKey(NEW_HMAC_SECRET, null) : oldSecret;

        List<Row> rotated = recalculate(options, oldKey, newKey, newSecret);
        int collisions = collisions(rotated);
        if (collisions > 0) {
            throw new CpfKeyCollisionException(collisions);
        }
        write(rotated);
        int invalidated = options.rotateDigestSecret() ? invalidateAccessCodes() : 0;
        int discarded = discardDrafts();
        return new Report(rotated.size(), invalidated, discarded);
    }

    // ---- Leitura e recálculo

    private List<Row> recalculate(
            Options options, SecretKeySpec oldKey, SecretKeySpec newKey, SecretKeySpec newSecret) {
        List<Row> result = new ArrayList<>();
        UUID last = new UUID(0L, 0L);
        while (true) {
            List<Row> batch =
                    jdbc.query(
                            "select id, cpf_encrypted, cpf_hash from student"
                                    + " where id > ? order by id limit ?",
                            (rs, i) ->
                                    new Row(
                                            rs.getObject("id", UUID.class),
                                            rs.getBytes("cpf_encrypted"),
                                            rs.getString("cpf_hash")),
                            last,
                            BATCH);
            if (batch.isEmpty()) {
                return result;
            }
            for (Row row : batch) {
                result.add(recalculate(row, options, oldKey, newKey, newSecret));
            }
            last = batch.getLast().id();
        }
    }

    private Row recalculate(
            Row row,
            Options options,
            SecretKeySpec oldKey,
            SecretKeySpec newKey,
            SecretKeySpec newSecret) {
        String digits = decrypt(oldKey, row.encrypted());
        byte[] encrypted =
                options.rotateEncryptionKey() ? encrypt(newKey, digits) : row.encrypted();
        String hash = options.rotateDigestSecret() ? hmac(newSecret, "cpf:" + digits) : row.hash();
        return new Row(row.id(), encrypted, hash);
    }

    private static int collisions(List<Row> rotated) {
        Set<String> seen = new HashSet<>();
        int collisions = 0;
        for (Row row : rotated) {
            if (!seen.add(row.hash())) {
                collisions++;
            }
        }
        return collisions;
    }

    // ---- Gravação

    private void write(List<Row> rotated) {
        for (int from = 0; from < rotated.size(); from += BATCH) {
            List<Row> batch = rotated.subList(from, Math.min(from + BATCH, rotated.size()));
            jdbc.batchUpdate(
                    "update student set cpf_encrypted = ?, cpf_hash = ? where id = ?",
                    batch,
                    BATCH,
                    (statement, row) -> {
                        statement.setBytes(1, row.encrypted());
                        statement.setString(2, row.hash());
                        statement.setObject(3, row.id());
                    });
        }
    }

    /**
     * O segredo do resumo também protege o código de acesso do professor: os vigentes deixam de
     * valer (o administrador os reemite). Devolve quantos eram vigentes.
     */
    private int invalidateAccessCodes() {
        return jdbc.update(
                "update teacher set access_code_hash = null, access_code_expires_at = null,"
                        + " access_code_renewed_at = null where access_code_hash is not null");
    }

    /**
     * Os rascunhos de cadastro do Telegram (30 min) guardam o CPF cifrado com a chave antiga e o
     * resumo com o segredo antigo: depois da rotação não servem mais e são descartados na mesma
     * transação. O professor recomeça o cadastro. Devolve só a contagem.
     */
    private int discardDrafts() {
        return jdbc.update("delete from student_registration_draft");
    }

    // ---- Chaves e criptografia

    private SecretKeySpec encryptionKey(String variable, String fallback) {
        String base64 = environment.getProperty(variable);
        if (base64 == null || base64.isBlank()) {
            base64 = fallback;
        }
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("Variável " + variable + " não informada");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Variável " + variable + " não está em Base64");
        }
        if (key.length != 32) {
            throw new IllegalStateException(
                    "Variável " + variable + " deve ter 32 bytes em Base64");
        }
        return new SecretKeySpec(key, "AES");
    }

    private SecretKeySpec hmacKey(String variable, String fallback) {
        String secret = environment.getProperty(variable);
        if (secret == null || secret.isBlank()) {
            secret = fallback;
        }
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Variável " + variable + " deve ter pelo menos 32 bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private byte[] encrypt(SecretKeySpec key, String digits) {
        try {
            byte[] iv = new byte[SIZE_IV];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(SIZE_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(digits.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("falha ao cifrar");
        }
    }

    private static String decrypt(SecretKeySpec key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(SIZE_TAG_BITS, data, 0, SIZE_IV));
            return new String(
                    cipher.doFinal(data, SIZE_IV, data.length - SIZE_IV), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(
                    "A chave antiga não decifra o CPF de um aluno; confira " + OLD_ENCRYPTION_KEY);
        }
    }

    private static String hmac(SecretKeySpec key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("falha ao calcular o resumo");
        }
    }
}
