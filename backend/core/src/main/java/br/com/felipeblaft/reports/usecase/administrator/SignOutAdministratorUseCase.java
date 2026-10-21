package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;

/**
 * "Sair" ({@code ADMIN_SIGN_OUT}): apaga só aquela sessão e grava {@code ADMIN_SIGN_OUT}; as outras
 * sessões do mesmo administrador continuam. Esqueleto da Fundação; a regra é do fluxo W-A.
 */
public class SignOutAdministratorUseCase {

    public record Command(Secret sessionToken, String correlationId) {}

    public SignOutAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorSessionRepository sessions,
            DataProtectionPort protection,
            AuthorshipService authorship) {}

    public void run(Actor actor, Command command) {
        throw new UnsupportedOperationException("SignOutAdministratorUseCase: fluxo W-A");
    }
}
