package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Dados da sessão corrente do administrador ({@code ADMIN_GET_SESSION}): nome, validade fixa e se a
 * troca de senha ainda é obrigatória. A sessão tem de ser a do próprio ator. Leitura: não grava
 * autoria nem renova a sessão.
 */
public class GetAdministratorSessionUseCase {

    public record Command(Secret sessionToken) {}

    /** O {@code toString} não revela o nome. */
    public record Result(
            UUID administratorId, String name, Instant expiresAt, boolean mustChangePassword) {

        @Override
        public String toString() {
            return "GetAdministratorSessionUseCase.Result[administratorId=" + administratorId + "]";
        }
    }

    private final ActorAuthorizationService authorization;
    private final AdministratorSessionRepository sessions;
    private final AdministratorRepository administrators;
    private final DataProtectionPort protection;

    public GetAdministratorSessionUseCase(
            ActorAuthorizationService authorization,
            AdministratorSessionRepository sessions,
            AdministratorRepository administrators,
            DataProtectionPort protection) {
        this.authorization = Objects.requireNonNull(authorization);
        this.sessions = Objects.requireNonNull(sessions);
        this.administrators = Objects.requireNonNull(administrators);
        this.protection = Objects.requireNonNull(protection);
    }

    public Result run(Actor actor, Command command) {
        authorization.require(actor, Operation.ADMIN_GET_SESSION);
        Secret token = command.sessionToken();
        if (token == null || token.value().isBlank()) {
            throw new DomainException(ErrorCode.NOT_AUTHENTICATED);
        }
        AdministratorSession session =
                sessions.searchByTokenHash(protection.sessionTokenHash(token.value()))
                        .map(AdministratorSessionRepository.Found::session)
                        .filter(found -> found.administratorId().equals(actor.id()))
                        .orElseThrow(() -> new DomainException(ErrorCode.NOT_AUTHENTICATED));
        Administrator administrator =
                administrators
                        .searchById(actor.id())
                        .filter(Administrator::active)
                        .orElseThrow(() -> new DomainException(ErrorCode.ACCESS_ENDED));
        return new Result(
                administrator.id(),
                administrator.name(),
                session.expiresAt(),
                administrator.passwordProvisional());
    }
}
