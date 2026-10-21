package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.util.Objects;

/**
 * "Sair" ({@code ADMIN_SIGN_OUT}): apaga só aquela sessão e grava {@code ADMIN_SIGN_OUT}; as outras
 * sessões do mesmo administrador continuam. Só a sessão do próprio ator pode ser encerrada.
 */
public class SignOutAdministratorUseCase {

    public record Command(Secret sessionToken, String correlationId) {}

    private final ActorAuthorizationService authorization;
    private final AdministratorSessionRepository sessions;
    private final DataProtectionPort protection;
    private final AuthorshipService authorship;

    public SignOutAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorSessionRepository sessions,
            DataProtectionPort protection,
            AuthorshipService authorship) {
        this.authorization = Objects.requireNonNull(authorization);
        this.sessions = Objects.requireNonNull(sessions);
        this.protection = Objects.requireNonNull(protection);
        this.authorship = Objects.requireNonNull(authorship);
    }

    public void run(Actor actor, Command command) {
        authorization.require(actor, Operation.ADMIN_SIGN_OUT);
        Secret token = command.sessionToken();
        if (token == null || token.value().isBlank()) {
            throw new DomainException(ErrorCode.NOT_AUTHENTICATED);
        }
        AdministratorSession session =
                sessions.searchByTokenHash(protection.sessionTokenHash(token.value()))
                        .map(AdministratorSessionRepository.Found::session)
                        .filter(found -> found.administratorId().equals(actor.id()))
                        .orElseThrow(() -> new DomainException(ErrorCode.NOT_AUTHENTICATED));
        sessions.remove(session.id());
        authorship.record(
                actor,
                Operation.ADMIN_SIGN_OUT,
                AuthorshipTargetType.ADMINISTRATOR,
                actor.id(),
                command.correlationId());
    }
}
