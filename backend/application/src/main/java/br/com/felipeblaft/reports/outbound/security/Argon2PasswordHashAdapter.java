package br.com.felipeblaft.reports.outbound.security;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Hash de senha com Argon2id (feature 005, R-04, FR-004). Memória e iterações vêm das propriedades
 * ({@code reports.administrator.password.*}); paralelismo, sal (16 bytes, único por senha) e saída
 * (32 bytes) são fixos. O formato codificado guarda os parâmetros, então mudar o custo não invalida
 * hashes antigos: {@link #needsRehash} indica quando regravar no próximo acesso bem-sucedido. A
 * senha é normalizada em NFC antes do hash, de modo que as formas Unicode equivalentes coincidam.
 */
@Component
class Argon2PasswordHashAdapter implements PasswordHashPort {

    private static final int SIZE_SALT = 16;
    private static final int SIZE_HASH = 32;
    private static final int PARALLELISM = 1;
    private static final int MIN_MEMORY_KIB = 8 * PARALLELISM;
    private static final Pattern ENCODED =
            Pattern.compile(
                    "^\\$argon2id\\$v=(\\d+)\\$m=(\\d+),t=(\\d+),p=(\\d+)"
                            + "\\$[A-Za-z0-9+/]+\\$[A-Za-z0-9+/]+$");
    private static final int ARGON2_VERSION = 19;

    private final int memoryKib;
    private final int iterations;
    private final Argon2PasswordEncoder encoder;
    private final String decoyHash;

    @Autowired
    Argon2PasswordHashAdapter(ApplicationProperties properties) {
        this(
                properties.administrator().password().memoryKib(),
                properties.administrator().password().iterations());
    }

    private Argon2PasswordHashAdapter(int memoryKib, int iterations) {
        if (memoryKib < MIN_MEMORY_KIB) {
            throw new IllegalStateException(
                    "A memória do Argon2id deve ser de pelo menos " + MIN_MEMORY_KIB + " KiB");
        }
        if (iterations < 1) {
            throw new IllegalStateException("As iterações do Argon2id devem ser pelo menos 1");
        }
        this.memoryKib = memoryKib;
        this.iterations = iterations;
        this.encoder =
                new Argon2PasswordEncoder(SIZE_SALT, SIZE_HASH, PARALLELISM, memoryKib, iterations);
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        this.decoyHash = encoder.encode(Base64.getEncoder().encodeToString(random));
    }

    static Argon2PasswordHashAdapter withParameters(int memoryKib, int iterations) {
        return new Argon2PasswordHashAdapter(memoryKib, iterations);
    }

    @Override
    public String hash(Secret password) {
        return encoder.encode(normalized(password));
    }

    @Override
    public boolean matches(Secret password, String encodedHash) {
        if (encodedHash == null || !ENCODED.matcher(encodedHash).matches()) {
            return false;
        }
        try {
            return encoder.matches(normalized(password), encodedHash);
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    @Override
    public boolean needsRehash(String encodedHash) {
        if (encodedHash == null) {
            return true;
        }
        Matcher parts = ENCODED.matcher(encodedHash);
        if (!parts.matches()) {
            return true;
        }
        try {
            return Integer.parseInt(parts.group(1)) != ARGON2_VERSION
                    || Integer.parseInt(parts.group(2)) < memoryKib
                    || Integer.parseInt(parts.group(3)) < iterations
                    || Integer.parseInt(parts.group(4)) < PARALLELISM;
        } catch (NumberFormatException overflow) {
            return true;
        }
    }

    @Override
    public void verifyAgainstDecoy(Secret password) {
        // o resultado é descartado: só importa gastar o mesmo custo de uma verificação real
        encoder.matches(normalized(password), decoyHash);
    }

    private static String normalized(Secret password) {
        return Normalizer.normalize(password.value(), Normalizer.Form.NFC);
    }
}
