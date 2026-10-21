package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.util.Objects;

/**
 * Lista os administradores ({@code ADMINISTRATOR_LIST}) com busca e filtro de situação; nunca traz
 * hash nem código. Leitura: não gera registro de autoria.
 */
public class ListAdministratorsUseCase {

    /** {@code active} nulo lista todos. */
    public record Command(String search, Boolean active, PageRequest page) {

        @Override
        public String toString() {
            return "ListAdministratorsUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final AdministratorRepository administrators;

    public ListAdministratorsUseCase(
            ActorAuthorizationService authorization, AdministratorRepository administrators) {
        this.authorization = Objects.requireNonNull(authorization);
        this.administrators = Objects.requireNonNull(administrators);
    }

    public PageResult<AdministratorView> run(Actor actor, Command command) {
        authorization.require(actor, Operation.ADMINISTRATOR_LIST);
        String search =
                command.search() == null || command.search().isBlank()
                        ? null
                        : command.search().strip();
        PageRequest page = command.page() == null ? PageRequest.first() : command.page();
        return administrators.list(search, command.active(), page).map(AdministratorView::of);
    }
}
