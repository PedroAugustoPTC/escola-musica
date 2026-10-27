package br.com.felipeblaft.reports.usecase.administrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.doubles.FakeAuthorshipRecorder;
import br.com.felipeblaft.reports.doubles.FakeDataProtection;
import br.com.felipeblaft.reports.doubles.FakePasswordHash;
import br.com.felipeblaft.reports.doubles.FixedActivationCodeGenerator;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAccessAttemptRepository;
import br.com.felipeblaft.reports.doubles.InMemoryAdministratorRepository;
import br.com.felipeblaft.reports.doubles.InMemoryAdministratorSessionRepository;
import br.com.felipeblaft.reports.doubles.InMemoryWebSessionRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.ActivationCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import java.time.Duration;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * Monta os dublês e os serviços compartilhados pelos testes dos casos de uso do administrador. A
 * revogação usa o {@link SessionRevocationService} real, com o repositório de sessões Web em
 * memória.
 */
final class AdministratorScenario {

    static final String PASSWORD = "correct horse battery";
    static final String ORIGIN = "10.0.0.1";
    static final String CORRELATION = "req-test1";
    static final Duration SESSION_DURATION = Duration.ofHours(2);
    static final Duration PROVISIONAL_VALIDITY = Duration.ofDays(7);

    final FixedClock clock = new FixedClock();
    final BusinessParameters parameters = BusinessParameters.defaults();
    final FakeDataProtection protection = new FakeDataProtection();
    final FakePasswordHash hasher = new FakePasswordHash();
    final InMemoryAdministratorRepository administrators = new InMemoryAdministratorRepository();
    final InMemoryAdministratorSessionRepository sessions =
            new InMemoryAdministratorSessionRepository(administrators);
    final InMemoryAccessAttemptRepository attemptsRepository =
            new InMemoryAccessAttemptRepository();
    final AccessAttemptsControlService attempts =
            new AccessAttemptsControlService(attemptsRepository, protection, clock, parameters);
    final FakeAuthorshipRecorder recorder = new FakeAuthorshipRecorder();
    final AuthorshipService authorship = new AuthorshipService(recorder, clock);
    final ActorAuthorizationService authorization =
            new ActorAuthorizationService(PermissionMatrix.standard());
    final FixedActivationCodeGenerator generator = new FixedActivationCodeGenerator();
    final ActivationCodeIssuanceService issuance =
            new ActivationCodeIssuanceService(generator, protection);
    final SessionRevocationService revocation =
            new SessionRevocationService(new InMemoryWebSessionRepository(), sessions, clock);

    /** Administrador ativado, com a senha {@link #PASSWORD} e definitiva. */
    Administrator activated(String name, String email) {
        return activated(name, email, PASSWORD);
    }

    Administrator activated(String name, String email, String password) {
        Instant now = clock.now();
        Administrator pending =
                Administrator.pending(name, email, "unused-code-hash", now.plusSeconds(1), now);
        Administrator administrator = pending.activate(hasher.hash(new Secret(password)), now);
        administrators.save(administrator);
        return administrator;
    }

    /** Administrador ativo, ainda sem senha, com o código de ativação informado. */
    Administrator pending(String name, String email, String code) {
        Instant now = clock.now();
        ActivationCodeIssuanceService.Issued issued = issueCode(code);
        Administrator administrator =
                Administrator.pending(name, email, issued.hash(), issued.expiresAt(), now);
        administrators.save(administrator);
        return administrator;
    }

    /** Primeiro administrador, com senha provisória. */
    Administrator provisional(String name, String email, String password) {
        Administrator administrator =
                Administrator.first(
                        name,
                        email,
                        hasher.hash(new Secret(password)),
                        PROVISIONAL_VALIDITY,
                        clock.now());
        administrators.save(administrator);
        return administrator;
    }

    private ActivationCodeIssuanceService.Issued issueCode(String code) {
        generator.schedule(code);
        return issuance.issue(clock.now());
    }

    SignInAdministratorUseCase signIn() {
        return new SignInAdministratorUseCase(
                administrators,
                sessions,
                hasher,
                protection,
                attempts,
                authorship,
                clock,
                SESSION_DURATION);
    }

    AdministratorSessionResult signInAs(Administrator administrator, String password) {
        return signIn().run(
                        new SignInAdministratorUseCase.Command(
                                administrator.email(), new Secret(password), ORIGIN, CORRELATION));
    }

    static void failWith(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).code())
                .isEqualTo(code);
    }

    /** Executa a ação e devolve a recusa de domínio que ela lançou. */
    static DomainException refusalOf(ThrowingCallable action) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(action);
        assertThat(thrown).isInstanceOf(DomainException.class);
        return (DomainException) thrown;
    }

    /** Cria um administrador pelo caso de uso e devolve o resultado, com o código em claro. */
    AdministratorWithCode created(Actor actor, String name, String email) {
        return new CreateAdministratorUseCase(
                        authorization, administrators, issuance, authorship, clock)
                .run(actor, new CreateAdministratorUseCase.Command(name, email, CORRELATION));
    }
}
