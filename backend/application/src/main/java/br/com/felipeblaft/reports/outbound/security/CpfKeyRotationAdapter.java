package br.com.felipeblaft.reports.outbound.security;

import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.CpfKeyRotationPort;
import org.springframework.stereotype.Component;

/**
 * Rotação das chaves de cifragem e de resumo do CPF. Stub da Fundação (feature 005): a rotação real
 * chega com o fluxo W-K.
 */
@Component
class CpfKeyRotationAdapter implements CpfKeyRotationPort {

    @Override
    public Report rotate(Options options) {
        throw PendingImplementation.of("W-K");
    }
}
