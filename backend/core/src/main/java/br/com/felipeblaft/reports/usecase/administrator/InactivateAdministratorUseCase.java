package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import java.util.UUID;

/**
 * Inativa um administrador, inclusive o próprio ({@code ADMINISTRATOR_INACTIVATE}): sessões
 * revogadas, senha e código apagados. Nunca deixa a escola sem administrador ativo, sob
 * concorrência ({@code LAST_ADMINISTRATOR}). Esqueleto da Fundação; a regra é do fluxo W-A.
 */
public class InactivateAdministratorUseCase {

    public record Command(UUID administratorId, String correlationId) {}

    public InactivateAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            SessionRevocationService revocation,
            AuthorshipService authorship,
            ClockPort clock) {}

    public AdministratorView run(Actor actor, Command command) {
        throw new UnsupportedOperationException("InactivateAdministratorUseCase: fluxo W-A");
    }
}
