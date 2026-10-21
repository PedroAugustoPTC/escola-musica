package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.service.ActivationCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.util.UUID;

/**
 * Reativa um administrador ({@code ADMINISTRATOR_REACTIVATE}): volta a pendente de ativação com um
 * código novo, exibido uma única vez. Esqueleto da Fundação; a regra é do fluxo W-A.
 */
public class ReactivateAdministratorUseCase {

    public record Command(UUID administratorId, String correlationId) {}

    public ReactivateAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            ActivationCodeIssuanceService issuance,
            AuthorshipService authorship,
            ClockPort clock) {}

    public AdministratorWithCode run(Actor actor, Command command) {
        throw new UnsupportedOperationException("ReactivateAdministratorUseCase: fluxo W-A");
    }
}
