package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorIdentity;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.service.ActivationCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Cria um administrador pendente de ativação ({@code ADMINISTRATOR_CREATE}), com código de ativação
 * de 7 dias exibido uma única vez. O e-mail é único entre administradores, sem diferenciar caixa
 * nem espaços, inclusive entre inativos, também sob concorrência (inserção atômica).
 */
public class CreateAdministratorUseCase {

    public record Command(String name, String email, String correlationId) {

        @Override
        public String toString() {
            return "CreateAdministratorUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final AdministratorRepository administrators;
    private final ActivationCodeIssuanceService issuance;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public CreateAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            ActivationCodeIssuanceService issuance,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.administrators = Objects.requireNonNull(administrators);
        this.issuance = Objects.requireNonNull(issuance);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    public AdministratorWithCode run(Actor actor, Command command) {
        authorization.require(actor, Operation.ADMINISTRATOR_CREATE);
        AdministratorIdentity identity = AdministratorIdentity.of(command.name(), command.email());
        Instant now = clock.now();
        ActivationCodeIssuanceService.Issued issued = issuance.issue(now);
        Administrator administrator =
                Administrator.pending(
                        identity.name(), identity.email(), issued.hash(), issued.expiresAt(), now);
        if (!administrators.insertIfAbsent(administrator)) {
            Map<String, Object> details =
                    administrators
                            .searchByEmail(identity.email())
                            .<Map<String, Object>>map(existing -> Map.of("name", existing.name()))
                            .orElse(Map.of());
            throw new DomainException(ErrorCode.DUPLICATE_EMAIL, details);
        }
        authorship.record(
                actor,
                Operation.ADMINISTRATOR_CREATE,
                AuthorshipTargetType.ADMINISTRATOR,
                administrator.id(),
                command.correlationId());
        return new AdministratorWithCode(
                AdministratorView.of(administrator),
                new Secret(issued.code().value()),
                issued.expiresAt());
    }
}
