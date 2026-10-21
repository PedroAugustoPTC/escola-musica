package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.PasswordPolicy;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Entrada do administrador com e-mail e senha (operação {@code ADMIN_SIGN_IN}, pública). Mesma
 * mensagem e mesmo tempo para e-mail inexistente, senha errada, conta inativa e conta ainda não
 * ativada (a verificação de senha roda por inteiro mesmo sem conta); bloqueio por origem, checado
 * antes da senha; id de sessão novo a cada entrada, com validade fixa e sem renovação.
 */
public class SignInAdministratorUseCase {

    private static final String OPERATION = Operation.ADMIN_SIGN_IN.name();

    /** {@code origin} é o endereço visto pelo proxy; só o seu resumo é guardado. */
    public record Command(String email, Secret password, String origin, String correlationId) {

        @Override
        public String toString() {
            return "SignInAdministratorUseCase.Command";
        }
    }

    private final AdministratorRepository administrators;
    private final AdministratorSessionRepository sessions;
    private final PasswordHashPort passwords;
    private final DataProtectionPort protection;
    private final AccessAttemptsControlService attempts;
    private final AuthorshipService authorship;
    private final ClockPort clock;
    private final Duration sessionDuration;

    public SignInAdministratorUseCase(
            AdministratorRepository administrators,
            AdministratorSessionRepository sessions,
            PasswordHashPort passwords,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock,
            Duration sessionDuration) {
        this.administrators = Objects.requireNonNull(administrators);
        this.sessions = Objects.requireNonNull(sessions);
        this.passwords = Objects.requireNonNull(passwords);
        this.protection = Objects.requireNonNull(protection);
        this.attempts = Objects.requireNonNull(attempts);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
        this.sessionDuration = Objects.requireNonNull(sessionDuration);
    }

    public AdministratorSessionResult run(Command command) {
        Instant now = clock.now();
        String origin = command.origin() == null ? "" : command.origin();
        String originHash = protection.originHash(AttemptScope.ADMIN_SIGN_IN, origin);

        verifyNotBlocked(command, origin, originHash);

        Administrator administrator =
                administrators
                        .searchByEmail(Administrator.normalizeEmail(command.email()))
                        .orElse(null);
        Secret password = normalized(command.password());
        boolean acceptable = PasswordPolicy.acceptableLength(password);
        boolean verifiable = administrator != null && administrator.canSignInAt(now) && acceptable;
        if (!verifiable) {
            // o mesmo custo de uma verificação completa, sem revelar se a conta existe
            passwords.verifyAgainstDecoy(acceptable ? password : new Secret(""));
            throw refuse(administrator, command, origin, originHash);
        }
        if (!passwords.matches(password, administrator.passwordHash())) {
            throw refuse(administrator, command, origin, originHash);
        }

        attempts.confirmSuccess(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, origin);
        if (passwords.needsRehash(administrator.passwordHash())) {
            administrators.save(administrator.rehash(passwords.hash(password), now));
        }
        String token = protection.generateSessionToken();
        AdministratorSession session =
                AdministratorSession.open(
                        administrator.id(),
                        protection.sessionTokenHash(token),
                        now,
                        sessionDuration);
        sessions.save(session);
        authorship.recordAuthentication(
                ActionOrigin.WEB,
                Role.ADMINISTRATOR,
                administrator.id(),
                OPERATION,
                AuthorshipResult.SUCCESS,
                null,
                originHash,
                command.correlationId());
        return new AdministratorSessionResult(
                administrator.id(),
                administrator.name(),
                new Secret(token),
                session.expiresAt(),
                administrator.passwordProvisional());
    }

    private void verifyNotBlocked(Command command, String origin, String originHash) {
        try {
            attempts.verifyLock(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, origin);
        } catch (DomainException blocked) {
            if (blocked.code() == ErrorCode.TEMPORARILY_BLOCKED) {
                authorship.recordAuthentication(
                        ActionOrigin.WEB,
                        null,
                        null,
                        OPERATION,
                        AuthorshipResult.BLOCKED,
                        ErrorCode.TEMPORARILY_BLOCKED.name(),
                        originHash,
                        command.correlationId());
            }
            throw blocked;
        }
    }

    private DomainException refuse(
            Administrator administrator, Command command, String origin, String originHash) {
        attempts.registerFailure(AttemptScope.ADMIN_SIGN_IN, Channel.WEB, origin);
        authorship.recordAuthentication(
                ActionOrigin.WEB,
                administrator == null ? null : Role.ADMINISTRATOR,
                administrator == null ? null : administrator.id(),
                OPERATION,
                AuthorshipResult.FAILURE,
                administrator == null ? "UNKNOWN_EMAIL" : ErrorCode.INVALID_CREDENTIALS.name(),
                originHash,
                command.correlationId());
        return new DomainException(ErrorCode.INVALID_CREDENTIALS);
    }

    /** A senha é guardada em NFC; ausente vira vazia, que nunca confere. */
    private static Secret normalized(Secret password) {
        if (password == null) {
            return new Secret("");
        }
        return new Secret(Normalizer.normalize(password.value(), Normalizer.Form.NFC));
    }
}
