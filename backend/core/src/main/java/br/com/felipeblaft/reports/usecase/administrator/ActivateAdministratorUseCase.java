package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.ActivationCode;
import br.com.felipeblaft.reports.domain.access.Administrator;
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
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.Instant;
import java.util.Objects;

/**
 * Ativação da conta com e-mail, código de ativação e a nova senha ({@code ADMIN_ACTIVATE}, pública,
 * bloqueio {@code ADMIN_ACTIVATION}, contagem separada da entrada). Mensagem única {@code
 * INVALID_ACTIVATION} para e-mail desconhecido, código errado, vencido, de conta inativa ou já
 * usado. O código é de uso único e vale 7 dias; quem criou a conta pode usá-lo. A senha segue a
 * política; uma senha fora dela é recusada sem consumir o código e sem contar como falha. Não abre
 * sessão.
 */
public class ActivateAdministratorUseCase {

    private static final String OPERATION = Operation.ADMIN_ACTIVATE.name();

    public record Command(
            String email,
            Secret activationCode,
            Secret newPassword,
            String origin,
            String correlationId) {

        @Override
        public String toString() {
            return "ActivateAdministratorUseCase.Command";
        }
    }

    private final AdministratorRepository administrators;
    private final DataProtectionPort protection;
    private final PasswordHashPort passwords;
    private final AccessAttemptsControlService attempts;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public ActivateAdministratorUseCase(
            AdministratorRepository administrators,
            DataProtectionPort protection,
            PasswordHashPort passwords,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock) {
        this.administrators = Objects.requireNonNull(administrators);
        this.protection = Objects.requireNonNull(protection);
        this.passwords = Objects.requireNonNull(passwords);
        this.attempts = Objects.requireNonNull(attempts);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    public void run(Command command) {
        Instant now = clock.now();
        String origin = command.origin() == null ? "" : command.origin();
        String originHash = protection.originHash(AttemptScope.ADMIN_ACTIVATION, origin);

        verifyNotBlocked(command, origin, originHash);
        // o tamanho da senha não depende da conta: recusar aqui não revela nada sobre o código
        if (!PasswordPolicy.acceptableLength(command.newPassword())) {
            throw new DomainException(ErrorCode.PASSWORD_LENGTH);
        }

        Administrator administrator =
                administrators
                        .searchByEmail(Administrator.normalizeEmail(command.email()))
                        .orElse(null);
        String presented =
                ActivationCode.retry(
                                command.activationCode() == null
                                        ? null
                                        : command.activationCode().value())
                        .map(protection::hashActivationCode)
                        .orElse(null);
        if (administrator == null || !administrator.activationCodeMatches(presented, now)) {
            throw refuse(administrator, command, origin, originHash);
        }

        Secret password =
                PasswordPolicy.validate(
                        command.newPassword(), administrator.email(), administrator.name());
        administrators.save(administrator.activate(passwords.hash(password), now));
        attempts.confirmSuccess(AttemptScope.ADMIN_ACTIVATION, Channel.WEB, origin);
        authorship.recordAuthentication(
                ActionOrigin.WEB,
                Role.ADMINISTRATOR,
                administrator.id(),
                OPERATION,
                AuthorshipResult.SUCCESS,
                null,
                originHash,
                command.correlationId());
    }

    private void verifyNotBlocked(Command command, String origin, String originHash) {
        try {
            attempts.verifyLock(AttemptScope.ADMIN_ACTIVATION, Channel.WEB, origin);
        } catch (DomainException blocked) {
            authorship.recordAuthentication(
                    ActionOrigin.WEB,
                    null,
                    null,
                    OPERATION,
                    AuthorshipResult.BLOCKED,
                    ErrorCode.TEMPORARILY_BLOCKED.name(),
                    originHash,
                    command.correlationId());
            throw blocked;
        }
    }

    private DomainException refuse(
            Administrator administrator, Command command, String origin, String originHash) {
        attempts.registerFailure(AttemptScope.ADMIN_ACTIVATION, Channel.WEB, origin);
        authorship.recordAuthentication(
                ActionOrigin.WEB,
                administrator == null ? null : Role.ADMINISTRATOR,
                administrator == null ? null : administrator.id(),
                OPERATION,
                AuthorshipResult.FAILURE,
                administrator == null ? "UNKNOWN_EMAIL" : ErrorCode.INVALID_ACTIVATION.name(),
                originHash,
                command.correlationId());
        return new DomainException(ErrorCode.INVALID_ACTIVATION);
    }
}
