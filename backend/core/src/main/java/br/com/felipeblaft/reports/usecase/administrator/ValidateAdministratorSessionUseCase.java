package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import java.time.Instant;
import java.util.Objects;

/**
 * Valida a sessão do administrador em toda requisição, numa só consulta, sem cache de autorização
 * (FR-047): válida somente se {@code now < expiresAt}, não revogada e com a conta ativa. Devolve o
 * ator, com a marca de senha provisória. Não renova a sessão, não grava e não gera autoria (a
 * expiração não gera registro).
 */
public class ValidateAdministratorSessionUseCase {

    public record Result(Actor actor, Instant expiresAt) {}

    private final AdministratorSessionRepository sessions;
    private final DataProtectionPort protection;
    private final ClockPort clock;

    public ValidateAdministratorSessionUseCase(
            AdministratorSessionRepository sessions,
            DataProtectionPort protection,
            ClockPort clock) {
        this.sessions = Objects.requireNonNull(sessions);
        this.protection = Objects.requireNonNull(protection);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Recusa com {@code NOT_AUTHENTICATED}, {@code SESSION_EXPIRED} ou {@code ACCESS_ENDED}. */
    public Result run(Secret sessionToken) {
        if (sessionToken == null || sessionToken.value().isBlank()) {
            throw new DomainException(ErrorCode.NOT_AUTHENTICATED);
        }
        AdministratorSessionRepository.Found found =
                sessions.searchByTokenHash(protection.sessionTokenHash(sessionToken.value()))
                        .orElseThrow(() -> new DomainException(ErrorCode.NOT_AUTHENTICATED));
        AdministratorSession session = found.session();
        Administrator administrator = found.administrator();
        // conta inativada: o acesso foi encerrado, e reativar não ressuscita a sessão
        if (!administrator.active()
                || session.revocationReason() == SessionRevocationReason.ACCOUNT_INACTIVATED) {
            throw new DomainException(ErrorCode.ACCESS_ENDED);
        }
        if (!session.validAt(clock.now(), true)) {
            throw new DomainException(ErrorCode.SESSION_EXPIRED);
        }
        return new Result(
                Actor.administrator(administrator.id(), administrator.passwordProvisional()),
                session.expiresAt());
    }
}
