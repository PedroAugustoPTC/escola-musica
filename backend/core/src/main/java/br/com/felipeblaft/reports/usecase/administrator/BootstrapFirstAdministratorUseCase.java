package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorIdentity;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.PasswordPolicy;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Cria o primeiro administrador a partir do ambiente, com senha provisória, somente se ainda não
 * existe nenhum ({@code FIRST_ADMINISTRATOR_BOOTSTRAP}, ator de sistema {@code SYSTEM_STARTUP}). Se
 * já existe algum administrador (em qualquer situação), as variáveis são ignoradas, até as
 * inválidas. A criação é atômica. Valores inválidos recusam com {@code VALIDATION_FAILED} e {@code
 * details.fields} (campo {@code name}, {@code email} ou {@code password}), nunca com o valor. A
 * senha nunca vai a log nem a autoria.
 */
public class BootstrapFirstAdministratorUseCase {

    /** Identificador de correlação das ações da inicialização (formato aceito pela autoria). */
    static final String CORRELATION_ID = "startup-bootstrap";

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

    private final ActorAuthorizationService authorization;
    private final AdministratorRepository administrators;
    private final PasswordHashPort passwords;
    private final AuthorshipService authorship;
    private final ClockPort clock;
    private final Duration provisionalPasswordValidity;

    public BootstrapFirstAdministratorUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            PasswordHashPort passwords,
            AuthorshipService authorship,
            ClockPort clock,
            Duration provisionalPasswordValidity) {
        this.authorization = Objects.requireNonNull(authorization);
        this.administrators = Objects.requireNonNull(administrators);
        this.passwords = Objects.requireNonNull(passwords);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
        this.provisionalPasswordValidity = Objects.requireNonNull(provisionalPasswordValidity);
    }

    public Outcome run(Actor actor, Command command) {
        authorization.require(actor, Operation.FIRST_ADMINISTRATOR_BOOTSTRAP);
        if (administrators.count() > 0) {
            return Outcome.ALREADY_EXISTS;
        }

        List<AdministratorIdentity.Violation> violations =
                new ArrayList<>(AdministratorIdentity.violations(command.name(), command.email()));
        Secret password = null;
        try {
            password =
                    PasswordPolicy.validate(
                            command.password(),
                            Administrator.normalizeEmail(command.email()),
                            command.name());
        } catch (DomainException refusal) {
            violations.add(new AdministratorIdentity.Violation("password", refusal.code()));
        }
        if (!violations.isEmpty()) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED, AdministratorIdentity.details(violations));
        }

        AdministratorIdentity identity = AdministratorIdentity.of(command.name(), command.email());
        Administrator first =
                Administrator.first(
                        identity.name(),
                        identity.email(),
                        passwords.hash(password),
                        provisionalPasswordValidity,
                        clock.now());
        if (!administrators.insertIfNoneExists(first)) {
            return Outcome.ALREADY_EXISTS;
        }
        authorship.record(
                actor,
                Operation.FIRST_ADMINISTRATOR_BOOTSTRAP,
                AuthorshipTargetType.ADMINISTRATOR,
                first.id(),
                CORRELATION_ID);
        return Outcome.CREATED;
    }
}
