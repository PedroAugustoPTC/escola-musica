package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.Duration;

/**
 * Cria o primeiro administrador a partir do ambiente, com senha provisória, somente se ainda não
 * existe nenhum ({@code FIRST_ADMINISTRATOR_BOOTSTRAP}, ator de sistema {@code SYSTEM_STARTUP}). A
 * senha nunca vai a log nem a autoria. Esqueleto da Fundação; a regra é do fluxo W-A.
 */
public class BootstrapFirstAdministratorUseCase {

    public record Command(String name, String email, Secret password) {

        @Override
        public String toString() {
            return "BootstrapFirstAdministratorUseCase.Command";
        }
    }

    /** O que aconteceu: criou, ou ignorou porque já existe algum administrador. */
    public enum Outcome {
        CREATED,
        ALREADY_EXISTS
    }

    public BootstrapFirstAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            PasswordHashPort passwords,
            AuthorshipService authorship,
            ClockPort clock,
            Duration provisionalPasswordValidity) {}

    public Outcome run(Actor actor, Command command) {
        throw new UnsupportedOperationException("BootstrapFirstAdministratorUseCase: fluxo W-A");
    }
}
