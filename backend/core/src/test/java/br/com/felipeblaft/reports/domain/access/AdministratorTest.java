package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdministratorTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");
    private static final Instant LATER = NOW.plus(Duration.ofHours(1));
    private static final Instant CODE_EXPIRY = NOW.plus(Duration.ofDays(7));

    private Administrator pending() {
        return Administrator.pending("Ana Souza", "ana@school.test", "code-hash", CODE_EXPIRY, NOW);
    }

    private Administrator activated() {
        return pending().activate("password-hash", LATER);
    }

    private Administrator first() {
        return Administrator.first(
                "Bia Lima", "bia@school.test", "provisional-hash", Duration.ofDays(7), NOW);
    }

    @Test
    void normalizesTheEmailWithoutSpacesAtTheEdgesAndInLowerCase() {
        assertThat(Administrator.normalizeEmail("  Ana.Souza@School.TEST "))
                .isEqualTo("ana.souza@school.test");
        assertThat(Administrator.normalizeEmail(null)).isNull();
    }

    @Test
    void aNewAdministratorIsActiveWithoutPasswordAndWithAPendingCode() {
        Administrator administrator = pending();

        assertThat(administrator.id()).isNotNull();
        assertThat(administrator.active()).isTrue();
        assertThat(administrator.passwordHash()).isNull();
        assertThat(administrator.passwordProvisional()).isFalse();
        assertThat(administrator.passwordProvisionalExpiresAt()).isNull();
        assertThat(administrator.activationCodeHash()).isEqualTo("code-hash");
        assertThat(administrator.activationCodeExpiresAt()).isEqualTo(CODE_EXPIRY);
        assertThat(administrator.activatedAt()).isNull();
        assertThat(administrator.createdAt()).isEqualTo(NOW);
        assertThat(administrator.updatedAt()).isEqualTo(NOW);
        assertThat(administrator.pendingActivation()).isTrue();
        assertThat(administrator.activated()).isFalse();
    }

    @Test
    void aPendingAdministratorCannotSignIn() {
        assertThat(pending().canSignInAt(NOW)).isFalse();
    }

    @Test
    void theEmailOfANewAdministratorIsNormalized() {
        Administrator administrator =
                Administrator.pending(
                        "Ana Souza", "  ANA@School.test ", "code-hash", CODE_EXPIRY, NOW);

        assertThat(administrator.email()).isEqualTo("ana@school.test");
    }

    @Test
    void activationDefinesThePasswordAndConsumesTheCode() {
        Administrator activated = activated();

        assertThat(activated.passwordHash()).isEqualTo("password-hash");
        assertThat(activated.activationCodeHash()).isNull();
        assertThat(activated.activationCodeExpiresAt()).isNull();
        assertThat(activated.activatedAt()).isEqualTo(LATER);
        assertThat(activated.updatedAt()).isEqualTo(LATER);
        assertThat(activated.passwordProvisional()).isFalse();
        assertThat(activated.activated()).isTrue();
        assertThat(activated.pendingActivation()).isFalse();
        assertThat(activated.canSignInAt(LATER)).isTrue();
    }

    @Test
    void activationKeepsTheIdentityOfTheAccount() {
        Administrator pending = pending();

        Administrator activated = pending.activate("password-hash", LATER);

        assertThat(activated.id()).isEqualTo(pending.id());
        assertThat(activated.name()).isEqualTo(pending.name());
        assertThat(activated.email()).isEqualTo(pending.email());
        assertThat(activated.createdAt()).isEqualTo(pending.createdAt());
    }

    @Test
    void anAlreadyActivatedAccountCannotBeActivatedAgain() {
        Administrator activated = activated();

        assertThatThrownBy(() -> activated.activate("other-hash", LATER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anInactiveAccountCannotBeActivated() {
        Administrator inactive = pending().inactivate(LATER);

        assertThatThrownBy(() -> inactive.activate("password-hash", LATER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theCodeMatchesOnlyWhileValidAndPending() {
        Administrator pending = pending();

        assertThat(pending.activationCodeMatches("code-hash", NOW)).isTrue();
        assertThat(pending.activationCodeMatches("code-hash", CODE_EXPIRY.minusMillis(1))).isTrue();
        assertThat(pending.activationCodeMatches("code-hash", CODE_EXPIRY)).isFalse();
        assertThat(pending.activationCodeMatches("other-hash", NOW)).isFalse();
        assertThat(pending.activationCodeMatches(null, NOW)).isFalse();
    }

    @Test
    void anInactiveOrAlreadyActivatedAccountNeverMatchesACode() {
        assertThat(activated().activationCodeMatches("code-hash", LATER)).isFalse();
        assertThat(pending().inactivate(LATER).activationCodeMatches("code-hash", LATER)).isFalse();
    }

    @Test
    void theFirstAdministratorIsActivatedWithAProvisionalPassword() {
        Administrator first = first();

        assertThat(first.active()).isTrue();
        assertThat(first.passwordHash()).isEqualTo("provisional-hash");
        assertThat(first.passwordProvisional()).isTrue();
        assertThat(first.passwordProvisionalExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(first.activationCodeHash()).isNull();
        assertThat(first.activatedAt()).isEqualTo(NOW);
        assertThat(first.activated()).isTrue();
    }

    @Test
    void theProvisionalPasswordAuthenticatesOnlyUntilItExpires() {
        Administrator first = first();
        Instant expiry = NOW.plus(Duration.ofDays(7));

        assertThat(first.canSignInAt(NOW)).isTrue();
        assertThat(first.canSignInAt(expiry.minusMillis(1))).isTrue();
        assertThat(first.canSignInAt(expiry)).isFalse();
        assertThat(first.canSignInAt(expiry.plusSeconds(1))).isFalse();
    }

    @Test
    void theProvisionalValidityMustBePositive() {
        assertThatThrownBy(
                        () ->
                                Administrator.first(
                                        "Bia Lima",
                                        "bia@school.test",
                                        "provisional-hash",
                                        Duration.ZERO,
                                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changingTheProvisionalPasswordMakesItDefinitive() {
        Administrator changed = first().changePassword("new-hash", LATER);

        assertThat(changed.passwordHash()).isEqualTo("new-hash");
        assertThat(changed.passwordProvisional()).isFalse();
        assertThat(changed.passwordProvisionalExpiresAt()).isNull();
        assertThat(changed.updatedAt()).isEqualTo(LATER);
        assertThat(changed.canSignInAt(NOW.plus(Duration.ofDays(30)))).isTrue();
    }

    @Test
    void onlyAProvisionalPasswordIsChangedThisWay() {
        Administrator activated = activated();

        assertThatThrownBy(() -> activated.changePassword("new-hash", LATER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rehashKeepsTheProvisionalMarks() {
        Administrator rehashed = first().rehash("stronger-hash", LATER);

        assertThat(rehashed.passwordHash()).isEqualTo("stronger-hash");
        assertThat(rehashed.passwordProvisional()).isTrue();
        assertThat(rehashed.passwordProvisionalExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(rehashed.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void inactivationErasesThePasswordAndTheCode() {
        Administrator inactive = activated().inactivate(LATER.plusSeconds(1));
        Administrator inactiveProvisional = first().inactivate(LATER);

        assertThat(inactive.active()).isFalse();
        assertThat(inactive.passwordHash()).isNull();
        assertThat(inactive.activationCodeHash()).isNull();
        assertThat(inactive.activationCodeExpiresAt()).isNull();
        assertThat(inactive.updatedAt()).isEqualTo(LATER.plusSeconds(1));
        assertThat(inactive.canSignInAt(LATER)).isFalse();
        assertThat(inactive.pendingActivation()).isFalse();
        assertThat(inactiveProvisional.passwordProvisional()).isFalse();
        assertThat(inactiveProvisional.passwordProvisionalExpiresAt()).isNull();
        assertThat(inactiveProvisional.passwordHash()).isNull();
    }

    @Test
    void inactivatingAPendingAccountErasesTheCode() {
        Administrator inactive = pending().inactivate(LATER);

        assertThat(inactive.activationCodeHash()).isNull();
        assertThat(inactive.active()).isFalse();
    }

    @Test
    void reactivationReturnsAsPendingWithANewCode() {
        Administrator reactivated =
                activated()
                        .inactivate(LATER)
                        .reactivate("new-code-hash", CODE_EXPIRY, LATER.plusSeconds(5));

        assertThat(reactivated.active()).isTrue();
        assertThat(reactivated.passwordHash()).isNull();
        assertThat(reactivated.activatedAt()).isNull();
        assertThat(reactivated.activationCodeHash()).isEqualTo("new-code-hash");
        assertThat(reactivated.activationCodeExpiresAt()).isEqualTo(CODE_EXPIRY);
        assertThat(reactivated.pendingActivation()).isTrue();
        assertThat(reactivated.canSignInAt(LATER)).isFalse();
        assertThat(reactivated.updatedAt()).isEqualTo(LATER.plusSeconds(5));
    }

    @Test
    void anActiveAccountCannotBeReactivated() {
        Administrator activated = activated();

        assertThatThrownBy(() -> activated.reactivate("new-code-hash", CODE_EXPIRY, LATER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anInactiveAccountDoesNotLookPending() {
        assertThat(activated().inactivate(LATER).pendingActivation()).isFalse();
    }

    @Test
    void requiresTheMandatoryFields() {
        assertThatThrownBy(
                        () ->
                                new Administrator(
                                        null,
                                        "Ana",
                                        "ana@school.test",
                                        true,
                                        null,
                                        false,
                                        null,
                                        null,
                                        null,
                                        null,
                                        NOW,
                                        NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void toStringRevealsOnlyTheId() {
        Administrator administrator = activated();

        assertThat(administrator.toString())
                .contains(administrator.id().toString())
                .doesNotContain("Ana", "ana@school.test", "password-hash", "code-hash");
    }
}
