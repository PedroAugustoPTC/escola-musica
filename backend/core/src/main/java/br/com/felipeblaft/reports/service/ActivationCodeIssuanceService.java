package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.ActivationCode;
import br.com.felipeblaft.reports.port.ActivationCodeGeneratorPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import java.time.Instant;

/**
 * Gera o código de ativação do administrador (12 caracteres, 7 dias) e o seu HMAC, espelho do
 * {@link AccessCodeIssuanceService}. O código em claro só existe no valor devolvido. Esqueleto da
 * Fundação; a regra é do fluxo W-A.
 */
public class ActivationCodeIssuanceService {

    /** Código em claro, o seu HMAC (o que vai ao banco) e a validade. */
    public record Issued(ActivationCode code, String hash, Instant expiresAt) {

        @Override
        public String toString() {
            return "Issued[expiresAt=" + expiresAt + "]";
        }
    }

    public ActivationCodeIssuanceService(
            ActivationCodeGeneratorPort generator, DataProtectionPort protection) {}

    public Issued issue(Instant now) {
        throw new UnsupportedOperationException("ActivationCodeIssuanceService: fluxo W-A");
    }
}
