package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.ActivationCode;
import br.com.felipeblaft.reports.domain.registry.RegistryLimits;
import br.com.felipeblaft.reports.port.ActivationCodeGeneratorPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import java.time.Instant;
import java.util.Objects;

/**
 * Gera o código de ativação do administrador (12 caracteres, 7 dias) e o seu HMAC, espelho do
 * {@link AccessCodeIssuanceService}. O código em claro só existe no valor devolvido; o que vai ao
 * banco é o HMAC. Não há busca por código (a ativação é por e-mail), então não há teste de
 * unicidade: o espaço de 60 bits e o índice único parcial bastam.
 */
public class ActivationCodeIssuanceService {

    /** Código em claro, o seu HMAC (o que vai ao banco) e a validade. */
    public record Issued(ActivationCode code, String hash, Instant expiresAt) {

        @Override
        public String toString() {
            return "Issued[expiresAt=" + expiresAt + "]";
        }
    }

    private final ActivationCodeGeneratorPort generator;
    private final DataProtectionPort protection;

    public ActivationCodeIssuanceService(
            ActivationCodeGeneratorPort generator, DataProtectionPort protection) {
        this.generator = Objects.requireNonNull(generator);
        this.protection = Objects.requireNonNull(protection);
    }

    /** Gera um código novo, válido por 7 dias a partir de {@code now}. */
    public Issued issue(Instant now) {
        ActivationCode code = generator.generate();
        return new Issued(
                code,
                protection.hashActivationCode(code),
                now.plus(RegistryLimits.ACTIVATION_CODE_VALIDITY));
    }
}
