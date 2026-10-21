package br.com.felipeblaft.reports.outbound.security;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import org.springframework.stereotype.Component;

/** Esqueleto temporário da fase vermelha (testes primeiro). */
@Component
class Argon2PasswordHashAdapter implements PasswordHashPort {

    Argon2PasswordHashAdapter(ApplicationProperties properties) {}

    static Argon2PasswordHashAdapter withParameters(int memoryKib, int iterations) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public String hash(Secret password) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public boolean matches(Secret password, String encodedHash) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public boolean needsRehash(String encodedHash) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public void verifyAgainstDecoy(Secret password) {
        throw PendingImplementation.of("W-E");
    }
}
