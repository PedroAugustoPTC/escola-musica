package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;

/**
 * Lista os administradores ({@code ADMINISTRATOR_LIST}) com busca e filtro de situação; nunca traz
 * hash nem código. Esqueleto da Fundação; a regra é do fluxo W-A.
 */
public class ListAdministratorsUseCase {

    /** {@code active} nulo lista todos. */
    public record Command(String search, Boolean active, PageRequest page) {

        @Override
        public String toString() {
            return "ListAdministratorsUseCase.Command";
        }
    }

    public ListAdministratorsUseCase(
            ActorAuthorizationService authorization, AdministratorRepository administrators) {}

    public PageResult<AdministratorView> run(Actor actor, Command command) {
        throw new UnsupportedOperationException("ListAdministratorsUseCase: fluxo W-A");
    }
}
