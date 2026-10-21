package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.service.ActivationCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;

/**
 * Cria um administrador pendente de ativação ({@code ADMINISTRATOR_CREATE}), com código de ativação
 * de 7 dias exibido uma única vez; e-mail único. Esqueleto da Fundação; a regra é do fluxo W-A.
 */
public class CreateAdministratorUseCase {

    public record Command(String name, String email, String correlationId) {

        @Override
        public String toString() {
            return "CreateAdministratorUseCase.Command";
        }
    }

    public CreateAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            ActivationCodeIssuanceService issuance,
            AuthorshipService authorship,
            ClockPort clock) {}

    public AdministratorWithCode run(Actor actor, Command command) {
        throw new UnsupportedOperationException("CreateAdministratorUseCase: fluxo W-A");
    }
}
