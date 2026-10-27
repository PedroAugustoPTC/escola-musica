package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Inativa um administrador, inclusive o próprio ({@code ADMINISTRATOR_INACTIVATE}): sessões
 * revogadas na hora, senha e código apagados. Nunca deixa a escola sem administrador ativo, sob
 * concorrência: os ativos são bloqueados em ordem fixa antes da decisão ({@code
 * LAST_ADMINISTRATOR}, R-15).
 */
public class InactivateAdministratorUseCase {

    public record Command(UUID administratorId, String correlationId) {}

    private final ActorAuthorizationService authorization;
    private final AdministratorRepository administrators;
    private final SessionRevocationService revocation;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public InactivateAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            SessionRevocationService revocation,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.administrators = Objects.requireNonNull(administrators);
        this.revocation = Objects.requireNonNull(revocation);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    public AdministratorView run(Actor actor, Command command) {
        authorization.require(actor, Operation.ADMINISTRATOR_INACTIVATE);
        List<Administrator> active = administrators.lockAllActiveForUpdate();
        Administrator target =
                active.stream()
                        .filter(
                                administrator ->
                                        administrator.id().equals(command.administratorId()))
                        .findFirst()
                        .orElse(null);
        if (target == null) {
            // não está entre os ativos: ou não existe, ou já está inativo (nada a fazer)
            return administrators
                    .searchById(command.administratorId())
                    .map(AdministratorView::of)
                    .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        }
        if (active.size() <= 1) {
            throw new DomainException(ErrorCode.LAST_ADMINISTRATOR);
        }
        Administrator inactive = target.inactivate(clock.now());
        administrators.save(inactive);
        revocation.revokeAdministrator(inactive.id(), SessionRevocationReason.ACCOUNT_INACTIVATED);
        authorship.record(
                actor,
                Operation.ADMINISTRATOR_INACTIVATE,
                AuthorshipTargetType.ADMINISTRATOR,
                inactive.id(),
                command.correlationId());
        return AdministratorView.of(inactive);
    }
}
