package br.com.felipeblaft.reports.usecase.administrator;

import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.failWith;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.refusalOf;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.InMemoryAdministratorRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BootstrapFirstAdministratorUseCaseTest {

    private static final String PASSWORD = "provisional password";

    private final AdministratorScenario c = new AdministratorScenario();
    private final BootstrapFirstAdministratorUseCase useCase = useCaseOver(c.administrators);

    private BootstrapFirstAdministratorUseCase useCaseOver(
            InMemoryAdministratorRepository repository) {
        return new BootstrapFirstAdministratorUseCase(
                c.authorization,
                repository,
                c.hasher,
                c.authorship,
                c.clock,
                AdministratorScenario.PROVISIONAL_VALIDITY);
    }

    private BootstrapFirstAdministratorUseCase.Command command(
            String name, String email, String password) {
        return new BootstrapFirstAdministratorUseCase.Command(
                name, email, password == null ? null : new Secret(password));
    }

    private BootstrapFirstAdministratorUseCase.Outcome bootstrap() {
        return useCase.run(
                ActorFixtures.bootstrap(), command("Ana Souza", "ana@school.test", PASSWORD));
    }

    @Test
    void withNoAdministratorItCreatesTheFirstOneWithAProvisionalPassword() {
        assertThat(bootstrap()).isEqualTo(BootstrapFirstAdministratorUseCase.Outcome.CREATED);

        Administrator saved = c.administrators.searchByEmail("ana@school.test").orElseThrow();
        assertThat(c.administrators.count()).isEqualTo(1);
        assertThat(saved.name()).isEqualTo("Ana Souza");
        assertThat(saved.active()).isTrue();
        assertThat(saved.passwordProvisional()).isTrue();
        assertThat(saved.passwordProvisionalExpiresAt())
                .isEqualTo(c.clock.now().plus(Duration.ofDays(7)));
        assertThat(c.hasher.matches(new Secret(PASSWORD), saved.passwordHash())).isTrue();
        assertThat(saved.passwordHash()).doesNotContain(PASSWORD);
        assertThat(saved.activationCodeHash()).isNull();
    }

    @Test
    void theEmailIsNormalizedAndThePasswordKeptInNfc() {
        useCase.run(
                ActorFixtures.bootstrap(),
                command("Ana Souza", "  ANA@School.TEST ", "senha-café-bem-longa"));

        Administrator saved = c.administrators.searchByEmail("ana@school.test").orElseThrow();
        assertThat(c.hasher.matches(new Secret("senha-café-bem-longa"), saved.passwordHash()))
                .isTrue();
    }

    @Test
    void theProvisionalPasswordSignsInAndForcesTheChange() {
        bootstrap();
        Administrator saved = c.administrators.searchByEmail("ana@school.test").orElseThrow();

        assertThat(c.signInAs(saved, PASSWORD).mustChangePassword()).isTrue();
    }

    @Test
    void theProvisionalPasswordStopsWorkingAfterItsValidity() {
        bootstrap();
        Administrator saved = c.administrators.searchByEmail("ana@school.test").orElseThrow();
        c.clock.advance(Duration.ofDays(7));

        failWith(() -> c.signInAs(saved, PASSWORD), ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void ifAnyAdministratorExistsTheVariablesAreIgnored() {
        Administrator existing = c.activated("Bia Lima", "bia@school.test");
        int hashes = c.hasher.hashesProduced();

        BootstrapFirstAdministratorUseCase.Outcome outcome = bootstrap();

        assertThat(outcome).isEqualTo(BootstrapFirstAdministratorUseCase.Outcome.ALREADY_EXISTS);
        assertThat(c.administrators.all()).containsExactly(existing);
        assertThat(c.hasher.hashesProduced()).isEqualTo(hashes);
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void anInactiveOrPendingAdministratorAlsoCountsAsExisting() {
        c.administrators.save(c.activated("Bia Lima", "bia@school.test").inactivate(c.clock.now()));

        assertThat(bootstrap())
                .isEqualTo(BootstrapFirstAdministratorUseCase.Outcome.ALREADY_EXISTS);
    }

    @Test
    void whenAnAdministratorExistsInvalidVariablesDoNotStopTheStartup() {
        c.activated("Bia Lima", "bia@school.test");

        BootstrapFirstAdministratorUseCase.Outcome outcome =
                useCase.run(ActorFixtures.bootstrap(), command("A", "invalid", "short"));

        assertThat(outcome).isEqualTo(BootstrapFirstAdministratorUseCase.Outcome.ALREADY_EXISTS);
    }

    @Test
    void theCreationIsAtomicAndALostRaceIsIgnored() {
        InMemoryAdministratorRepository racing =
                new InMemoryAdministratorRepository() {
                    @Override
                    public boolean insertIfNoneExists(Administrator administrator) {
                        // outra instância inseriu o primeiro entre a leitura e a gravação
                        super.save(
                                Administrator.first(
                                        "Outra",
                                        "other@school.test",
                                        "hash",
                                        Duration.ofDays(7),
                                        c.clock.now()));
                        return super.insertIfNoneExists(administrator);
                    }
                };

        BootstrapFirstAdministratorUseCase.Outcome outcome =
                useCaseOver(racing)
                        .run(
                                ActorFixtures.bootstrap(),
                                command("Ana Souza", "ana@school.test", PASSWORD));

        assertThat(outcome).isEqualTo(BootstrapFirstAdministratorUseCase.Outcome.ALREADY_EXISTS);
        assertThat(racing.count()).isEqualTo(1);
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void writesTheBootstrapAuthorshipAsTheSystemAtStartup() {
        bootstrap();
        Administrator saved = c.administrators.searchByEmail("ana@school.test").orElseThrow();

        AuthorshipRecord record =
                c.recorder.ofOperation("FIRST_ADMINISTRATOR_BOOTSTRAP").getFirst();
        assertThat(c.recorder.inTransaction()).contains(record);
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.authorId()).isNull();
        assertThat(record.authorRole()).isNull();
        assertThat(record.origin()).isEqualTo(ActionOrigin.SYSTEM_STARTUP);
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.ADMINISTRATOR);
        assertThat(record.targetId()).isEqualTo(saved.id());
        assertThat(record.originHash()).isNull();
        assertThat(c.recorder.records().toString()).doesNotContain(PASSWORD, "ana@school.test");
    }

    @Test
    void aPasswordOutsideThePolicyIsRefusedNamingTheFieldNeverTheValue() {
        DomainException refusal =
                refusalOf(
                        () ->
                                useCase.run(
                                        ActorFixtures.bootstrap(),
                                        command("Ana Souza", "ana@school.test", "short-pw")));

        assertThat(refusal.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(refusal.details().get("fields"))
                .isEqualTo(List.of(Map.of("field", "password", "code", "PASSWORD_LENGTH")));
        assertThat(refusal.details().toString()).doesNotContain("short-pw");
        assertThat(c.administrators.count()).isZero();
        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void aPasswordEqualToTheEmailOrTheNameIsRefused() {
        DomainException sameAsEmail =
                refusalOf(
                        () ->
                                useCase.run(
                                        ActorFixtures.bootstrap(),
                                        command(
                                                "Ana Souza",
                                                "long.address@school.test",
                                                "LONG.address@school.test")));
        DomainException sameAsName =
                refusalOf(
                        () ->
                                useCase.run(
                                        ActorFixtures.bootstrap(),
                                        command(
                                                "Ana Clara Souza",
                                                "ana@school.test",
                                                "ana clara souza")));

        assertThat(sameAsEmail.details().get("fields"))
                .isEqualTo(
                        List.of(Map.of("field", "password", "code", "PASSWORD_EQUALS_IDENTITY")));
        assertThat(sameAsName.details().get("fields"))
                .isEqualTo(
                        List.of(Map.of("field", "password", "code", "PASSWORD_EQUALS_IDENTITY")));
    }

    @Test
    void anInvalidEmailOrNameIsRefusedNamingTheFields() {
        DomainException refusal =
                refusalOf(
                        () ->
                                useCase.run(
                                        ActorFixtures.bootstrap(),
                                        command("A", "not-an-email", PASSWORD)));

        assertThat(refusal.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(refusal.details().get("fields"))
                .isEqualTo(
                        List.of(
                                Map.of("field", "name", "code", "INVALID_NAME"),
                                Map.of("field", "email", "code", "INVALID_EMAIL")));
        assertThat(refusal.details().toString()).doesNotContain("not-an-email");
    }

    @Test
    void everyViolationIsReportedTogether() {
        DomainException refusal =
                refusalOf(() -> useCase.run(ActorFixtures.bootstrap(), command(null, null, null)));

        assertThat(refusal.details().get("fields"))
                .isEqualTo(
                        List.of(
                                Map.of("field", "name", "code", "INVALID_NAME"),
                                Map.of("field", "email", "code", "INVALID_EMAIL"),
                                Map.of("field", "password", "code", "PASSWORD_LENGTH")));
    }

    @Test
    void onlyTheStartupSystemActorMayBootstrap() {
        for (var actor :
                List.of(
                        ActorFixtures.initialLoad(),
                        ActorFixtures.administratorWeb(),
                        ActorFixtures.teacherWeb(),
                        ActorFixtures.teacherTelegram())) {
            failWith(
                    () -> useCase.run(actor, command("Ana Souza", "ana@school.test", PASSWORD)),
                    ErrorCode.RESOURCE_NOT_FOUND);
        }
        assertThat(c.administrators.count()).isZero();
    }

    @Test
    void theCommandDoesNotRevealItsContent() {
        assertThat(command("Ana Souza", "ana@school.test", PASSWORD).toString())
                .doesNotContain("Ana", "ana@school.test", PASSWORD);
    }
}
