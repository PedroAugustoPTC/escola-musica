package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.PasswordPolicy;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Troca obrigatória da senha provisória do primeiro administrador ({@code ADMIN_CHANGE_PASSWORD}):
 * a nova senha segue a política e difere da provisória; a provisória deixa de valer, todas as
 * sessões existentes do administrador são revogadas ({@code PASSWORD_CHANGED}) e abre-se uma sessão
 * nova, de identificador novo e sem a restrição.
 */
public class ChangeProvisionalPasswordUseCase {

    public record Command(Secret newPassword, String correlationId) {

        @Override
        public String toString() {
            return "ChangeProvisionalPasswordUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final AdministratorRepository administrators;
    private final AdministratorSessionRepository sessions;
    private final SessionRevocationService revocation;
    private final PasswordHashPort passwords;
    private final DataProtectionPort protection;
    private final AuthorshipService authorship;
    private final ClockPort clock;
    private final Duration sessionDuration;

    public ChangeProvisionalPasswordUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            AdministratorSessionRepository sessions,
            SessionRevocationService revocation,
            PasswordHashPort passwords,
            DataProtectionPort protection,
            AuthorshipService authorship,
            ClockPort clock,
            Duration sessionDuration) {
        this.authorization = Objects.requireNonNull(authorization);
        this.administrators = Objects.requireNonNull(administrators);
        this.sessions = Objects.requireNonNull(sessions);
        this.revocation = Objects.requireNonNull(revocation);
        this.passwords = Objects.requireNonNull(passwords);
        this.protection = Objects.requireNonNull(protection);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
        this.sessionDuration = Objects.requireNonNull(sessionDuration);
    }

    public AdministratorSessionResult run(Actor actor, Command command) {
        authorization.require(actor, Operation.ADMIN_CHANGE_PASSWORD);
        Administrator administrator =
                administrators
                        .searchById(actor.id())
                        .filter(Administrator::active)
                        .orElseThrow(() -> new DomainException(ErrorCode.ACCESS_ENDED));
        if (!administrator.passwordProvisional()) {
            throw new DomainException(ErrorCode.PASSWORD_CHANGE_NOT_REQUIRED);
        }
        Secret password =
                PasswordPolicy.validate(
                        command.newPassword(), administrator.email(), administrator.name());
        if (passwords.matches(password, administrator.passwordHash())) {
            throw new DomainException(ErrorCode.PASSWORD_SAME_AS_PROVISIONAL);
        }

        Instant now = clock.now();
        administrators.save(administrator.changePassword(passwords.hash(password), now));
        revocation.revokeAdministrator(
                administrator.id(), SessionRevocationReason.PASSWORD_CHANGED);
        String token = protection.generateSessionToken();
        AdministratorSession session =
                AdministratorSession.open(
                        administrator.id(),
                        protection.sessionTokenHash(token),
                        now,
                        sessionDuration);
        sessions.save(session);
        authorship.record(
                actor,
                Operation.ADMIN_CHANGE_PASSWORD,
                AuthorshipTargetType.ADMINISTRATOR,
                administrator.id(),
                command.correlationId());
        return new AdministratorSessionResult(
                administrator.id(),
                administrator.name(),
                new Secret(token),
                session.expiresAt(),
                false);
    }
}
