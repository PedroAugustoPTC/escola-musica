package br.com.felipeblaft.reports.outbound.security;

import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import org.springframework.stereotype.Component;

/**
 * Hash de senha com Argon2id. Stub da Fundação (feature 005): o algoritmo, os parâmetros das
 * propriedades e o hash de isca chegam com o fluxo W-E.
 */
@Component
class Argon2PasswordHashAdapter implements PasswordHashPort {

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
