package br.com.felipeblaft.reports.outbound.security;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.port.AccessCodeGeneratorPort;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Códigos de 8 caracteres com {@link SecureRandom} (R-12). */
@Component
public class AccessCodeGeneratorAdapter implements AccessCodeGeneratorPort {

    private final SecureRandom random = new SecureRandom();

    @Override
    public AccessCode generate() {
        StringBuilder code = new StringBuilder(AccessCode.SIZE);
        for (int i = 0; i < AccessCode.SIZE; i++) {
            code.append(AccessCode.ALPHABET.charAt(random.nextInt(AccessCode.ALPHABET.length())));
        }
        return AccessCode.of(code.toString());
    }
}
