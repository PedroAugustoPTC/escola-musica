package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.time.Instant;
import java.util.UUID;

/**
 * Dados da sessão corrente do administrador ({@code ADMIN_GET_SESSION}): nome, validade fixa e se a
 * troca de senha ainda é obrigatória. Esqueleto da Fundação; a regra é do fluxo W-A.
 */
public class GetAdministratorSessionUseCase {

    public record Command(Secret sessionToken) {}

    /** O {@code toString} não revela o nome. */
    public record Result(
            UUID administratorId, String name, Instant expiresAt, boolean mustChangePassword) {

        @Override
        public String toString() {
            return "GetAdministratorSessionUseCase.Result[administratorId=" + administratorId + "]";
        }
    }

    public GetAdministratorSessionUseCase(
            ActorAuthorizationService authorization,
            AdministratorSessionRepository sessions,
            AdministratorRepository administrators,
            DataProtectionPort protection) {}

    public Result run(Actor actor, Command command) {
        throw new UnsupportedOperationException("GetAdministratorSessionUseCase: fluxo W-A");
    }
}
