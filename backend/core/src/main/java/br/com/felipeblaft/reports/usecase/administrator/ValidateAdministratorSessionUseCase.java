package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import java.time.Instant;

/**
 * Valida a sessão do administrador em toda requisição, numa só consulta, sem cache de autorização
 * (FR-047): válida somente se {@code now < expiresAt}, não revogada e com a conta ativa. Devolve o
 * ator, com a marca de senha provisória. Esqueleto da Fundação; a regra é do fluxo W-A.
 */
public class ValidateAdministratorSessionUseCase {

    public record Result(Actor actor, Instant expiresAt) {}

    public ValidateAdministratorSessionUseCase(
            AdministratorSessionRepository sessions,
            DataProtectionPort protection,
            ClockPort clock) {}

    /** Recusa com {@code NOT_AUTHENTICATED}, {@code SESSION_EXPIRED} ou {@code ACCESS_ENDED}. */
    public Result run(Secret sessionToken) {
        throw new UnsupportedOperationException("ValidateAdministratorSessionUseCase: fluxo W-A");
    }
}
