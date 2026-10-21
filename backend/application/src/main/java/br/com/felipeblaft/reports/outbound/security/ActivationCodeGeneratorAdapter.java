package br.com.felipeblaft.reports.outbound.security;

import br.com.felipeblaft.reports.domain.access.ActivationCode;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.ActivationCodeGeneratorPort;
import org.springframework.stereotype.Component;

/**
 * Gerador do código de ativação do administrador. Stub da Fundação (feature 005): a geração real,
 * com {@code SecureRandom}, chega com o fluxo W-E.
 */
@Component
class ActivationCodeGeneratorAdapter implements ActivationCodeGeneratorPort {

    @Override
    public ActivationCode generate() {
        throw PendingImplementation.of("W-E");
    }
}
