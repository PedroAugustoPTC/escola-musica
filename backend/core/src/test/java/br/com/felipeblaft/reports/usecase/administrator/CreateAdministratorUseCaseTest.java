package br.com.felipeblaft.reports.usecase.administrator;

import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.CORRELATION;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.failWith;
import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.refusalOf;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CreateAdministratorUseCaseTest {

    private final AdministratorScenario c = new AdministratorScenario();
    private final CreateAdministratorUseCase useCase =
            new CreateAdministratorUseCase(
                    c.authorization, c.administrators, c.issuance, c.authorship, c.clock);
    private final Administrator ana = c.activated("Ana Souza", "ana@school.test");
    private final Actor actor = ActorFixtures.administratorWeb(ana.id());

    private AdministratorWithCode create(String name, String email) {
        return useCase.run(actor, new CreateAdministratorUseCase.Command(name, email, CORRELATION));
    }

    @Test
    void createsAnActiveAdministratorWithoutPasswordAndWithAPendingCode() {
        AdministratorWithCode result = create("Bia Lima", "bia@school.test");

        Administrator saved = c.administrators.searchByEmail("bia@school.test").orElseThrow();
        assertThat(saved.active()).isTrue();
        assertThat(saved.passwordHash()).isNull();
        assertThat(saved.pendingActivation()).isTrue();
        assertThat(saved.activationCodeHash())
                .isEqualTo("activation:" + result.activationCode().value());
        assertThat(saved.activationCodeExpiresAt())
                .isEqualTo(c.clock.now().plus(Duration.ofDays(7)));
        assertThat(result.validUntil()).isEqualTo(c.clock.now().plus(Duration.ofDays(7)));
        assertThat(result.administrator().id()).isEqualTo(saved.id());
        assertThat(result.administrator().name()).isEqualTo("Bia Lima");
        assertThat(result.administrator().active()).isTrue();
        assertThat(result.administrator().activationState())
                .isEqualTo(AdministratorView.ActivationState.PENDING);
        assertThat(result.administrator().activatedAt()).isNull();
    }

    @Test
    void theCodeHasTwelveCharactersOfTheAlphabetAndIsShownOnlyInTheResult() {
        AdministratorWithCode result = create("Bia Lima", "bia@school.test");

        assertThat(result.activationCode().value()).hasSize(12).matches("[A-HJ-NP-Z2-9]{12}");
        assertThat(result.activationCode().value()).isNotEqualTo(result.toString());
        Administrator saved = c.administrators.searchByEmail("bia@school.test").orElseThrow();
        assertThat(saved.toString()).doesNotContain(result.activationCode().value());
        assertThat(result.administrator().toString()).doesNotContain("Bia", "bia@school.test");
    }

    @Test
    void everyAdministratorGetsADifferentCode() {
        AdministratorWithCode first = create("Bia Lima", "bia@school.test");
        AdministratorWithCode second = create("Caio Dias", "caio@school.test");

        assertThat(first.activationCode()).isNotEqualTo(second.activationCode());
    }

    @Test
    void theEmailIsStoredNormalizedAndTheNameTrimmed() {
        create("  Bia Lima ", "  BIA@School.TEST ");

        Administrator saved = c.administrators.searchByEmail("bia@school.test").orElseThrow();
        assertThat(saved.name()).isEqualTo("Bia Lima");
    }

    @Test
    void aDuplicateEmailIsRefusedIgnoringCaseAndSpacesWithTheNameOfTheExistingOne() {
        DomainException refusal = refusalOf(() -> create("Outra Ana", "  ANA@school.TEST "));

        assertThat(refusal.code()).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        assertThat(refusal.details()).isEqualTo(Map.of("name", "Ana Souza"));
        assertThat(c.administrators.count()).isEqualTo(1);
        assertThat(c.recorder.inTransaction().stream().map(AuthorshipRecord::operation))
                .doesNotContain("ADMINISTRATOR_CREATE");
    }

    @Test
    void anInactiveAdministratorStillOwnsTheEmail() {
        c.administrators.save(ana.inactivate(c.clock.now()));

        failWith(() -> create("Outra Ana", "ana@school.test"), ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void aFailedCreationConsumesNoCodeAndLeavesNoTrace() {
        failWith(() -> create("Outra Ana", "ana@school.test"), ErrorCode.DUPLICATE_EMAIL);

        assertThat(c.administrators.all()).containsExactly(ana);
    }

    @Test
    void invalidNameOrEmailIsRefusedWithTheFieldsAndNeverTheValues() {
        DomainException refusal = refusalOf(() -> create("A", "not-an-email"));

        assertThat(refusal.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(refusal.details().get("fields"))
                .isEqualTo(
                        List.of(
                                Map.of("field", "name", "code", "INVALID_NAME"),
                                Map.of("field", "email", "code", "INVALID_EMAIL")));
        assertThat(refusal.details().toString()).doesNotContain("not-an-email");
        assertThat(c.administrators.count()).isEqualTo(1);
    }

    @Test
    void writesTheCreationAuthorshipInTheSameTransactionWithoutTheCode() {
        AdministratorWithCode result = create("Bia Lima", "bia@school.test");

        AuthorshipRecord record = c.recorder.ofOperation("ADMINISTRATOR_CREATE").getFirst();
        assertThat(c.recorder.inTransaction()).contains(record);
        assertThat(record.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(record.authorId()).isEqualTo(ana.id());
        assertThat(record.authorRole()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(record.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(record.targetType()).isEqualTo(AuthorshipTargetType.ADMINISTRATOR);
        assertThat(record.targetId()).isEqualTo(result.administrator().id());
        assertThat(record.correlationId()).isEqualTo(CORRELATION);
        assertThat(c.recorder.records().toString())
                .doesNotContain(result.activationCode().value(), "bia@school.test");
    }

    @Test
    void onlyAnAdministratorMayCreate() {
        failWith(
                () ->
                        useCase.run(
                                ActorFixtures.teacherWeb(),
                                new CreateAdministratorUseCase.Command(
                                        "Bia Lima", "bia@school.test", CORRELATION)),
                ErrorCode.RESOURCE_NOT_FOUND);
        failWith(
                () ->
                        useCase.run(
                                ActorFixtures.teacherTelegram(),
                                new CreateAdministratorUseCase.Command(
                                        "Bia Lima", "bia@school.test", CORRELATION)),
                ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(c.administrators.count()).isEqualTo(1);
    }

    @Test
    void anAdministratorWithAProvisionalPasswordCannotCreate() {
        failWith(
                () ->
                        useCase.run(
                                ActorFixtures.administratorWithProvisionalPassword(),
                                new CreateAdministratorUseCase.Command(
                                        "Bia Lima", "bia@school.test", CORRELATION)),
                ErrorCode.PASSWORD_CHANGE_REQUIRED);
    }

    @Test
    void theCommandDoesNotRevealItsContent() {
        assertThat(
                        new CreateAdministratorUseCase.Command(
                                        "Bia", "bia@school.test", CORRELATION)
                                .toString())
                .doesNotContain("Bia", "bia@school.test");
    }
}
