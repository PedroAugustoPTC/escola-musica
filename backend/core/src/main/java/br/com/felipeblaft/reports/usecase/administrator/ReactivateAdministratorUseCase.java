package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Reativa um administrador inativo ({@code ADMINISTRATOR_REACTIVATE}): volta a pendente de ativação
 * com um código novo, exibido uma única vez; as sessões antigas continuam revogadas e a senha
 * antiga não volta. Conta inexistente ou já ativa é tratada como inexistente.
 */
public class ReactivateAdministratorUseCase {

    public record Command(UUID administratorId, String correlationId) {}

    private final ActorAuthorizationService authorization;
    private final AdministratorRepository administrators;
    private final ActivationCodeIssuanceService issuance;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public ReactivateAdministratorUseCase(
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
        authorization.require(actor, Operation.ADMINISTRATOR_REACTIVATE);
        Administrator inactive =
                administrators
                        .searchById(command.administratorId())
                        .filter(administrator -> !administrator.active())
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        Instant now = clock.now();
        ActivationCodeIssuanceService.Issued issued = issuance.issue(now);
        Administrator reactivated = inactive.reactivate(issued.hash(), issued.expiresAt(), now);
        administrators.save(reactivated);
        authorship.record(
                actor,
                Operation.ADMINISTRATOR_REACTIVATE,
                AuthorshipTargetType.ADMINISTRATOR,
                reactivated.id(),
                command.correlationId());
        return new AdministratorWithCode(
                AdministratorView.of(reactivated),
                new Secret(issued.code().value()),
                issued.expiresAt());
    }
}
