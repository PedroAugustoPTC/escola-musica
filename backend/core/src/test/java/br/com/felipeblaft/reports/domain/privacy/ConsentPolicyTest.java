package br.com.felipeblaft.reports.domain.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Situação do consentimento e quando ele é exigido (FR-030 a FR-035, FR-081). */
class ConsentPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    private static ConsentTermVersion term(int number, boolean requiresNew) {
        return new ConsentTermVersion(
                UUID.randomUUID(),
                number,
                LocalDate.of(2026, 1, number),
                "Institutional text " + number,
                requiresNew,
                ADMIN,
                NOW);
    }

    private static GuardianConsent consent(ConsentTermVersion term, LocalDate revokedOn) {
        return new GuardianConsent(
                UUID.randomUUID(),
                STUDENT,
                "Maria Guardian",
                ConsentRelationship.MOTHER,
                LocalDate.of(2026, 2, 1),
                term.id(),
                term.number(),
                ConsentCollectionMethod.SIGNED_PAPER,
                ADMIN,
                Role.ADMINISTRATOR,
                ActionOrigin.WEB,
                NOW,
                revokedOn,
                revokedOn == null ? null : "MOTHER",
                revokedOn == null ? null : ADMIN);
    }

    @Test
    void adultOnTheirBirthdayDoesNotRequireConsent() {
        assertThat(ConsentPolicy.required(LocalDate.of(2008, 9, 10), TODAY)).isFalse();
        assertThat(ConsentPolicy.required(LocalDate.of(2008, 9, 11), TODAY)).isTrue();
        assertThat(ConsentPolicy.required(LocalDate.of(2000, 1, 1), TODAY)).isFalse();
    }

    @Test
    void minorWithoutAnyRecordIsMissing() {
        assertThat(ConsentPolicy.status(List.of(), 1)).isEqualTo(ConsentStatus.MISSING);
    }

    @Test
    void validConsentOfACompatibleVersion() {
        ConsentTermVersion first = term(1, false);
        assertThat(ConsentPolicy.status(List.of(consent(first, null)), 1))
                .isEqualTo(ConsentStatus.VALID);
    }

    @Test
    void revokedConsentIsRevoked() {
        ConsentTermVersion first = term(1, false);
        assertThat(ConsentPolicy.status(List.of(consent(first, LocalDate.of(2026, 5, 1))), 1))
                .isEqualTo(ConsentStatus.REVOKED);
    }

    @Test
    void versionOlderThanTheLastOneThatRequiresNewConsentIsSuperseded() {
        ConsentTermVersion first = term(1, false);
        assertThat(ConsentPolicy.status(List.of(consent(first, null)), 2))
                .isEqualTo(ConsentStatus.SUPERSEDED);
    }

    @Test
    void compatibleVersionKeepsTheConsentValid() {
        ConsentTermVersion second = term(2, true);
        assertThat(ConsentPolicy.status(List.of(consent(second, null)), 2))
                .isEqualTo(ConsentStatus.VALID);
    }

    @Test
    void aNewRecordAfterTheRevocationMakesItValidAgain() {
        ConsentTermVersion first = term(1, false);
        List<GuardianConsent> history =
                List.of(consent(first, null), consent(first, LocalDate.of(2026, 5, 1)));
        assertThat(ConsentPolicy.status(history, 1)).isEqualTo(ConsentStatus.VALID);
    }

    @Test
    void anyValidRecordWinsOverRevokedAndSuperseded() {
        ConsentTermVersion first = term(1, false);
        ConsentTermVersion third = term(3, true);
        List<GuardianConsent> history =
                List.of(
                        consent(first, LocalDate.of(2026, 5, 1)),
                        consent(first, null),
                        consent(third, null));
        assertThat(ConsentPolicy.status(history, 3)).isEqualTo(ConsentStatus.VALID);
    }

    @Test
    void revokedBeatsSupersededWhenNoRecordIsValid() {
        ConsentTermVersion first = term(1, false);
        List<GuardianConsent> history =
                List.of(consent(first, null), consent(first, LocalDate.of(2026, 5, 1)));
        assertThat(ConsentPolicy.status(history, 2)).isEqualTo(ConsentStatus.REVOKED);
    }

    @Test
    void aiIsBlockedOnlyForMinorsWithoutValidConsent() {
        LocalDate minor = LocalDate.of(2015, 1, 1);
        LocalDate adult = LocalDate.of(2000, 1, 1);
        assertThat(ConsentPolicy.blocksAi(minor, TODAY, ConsentStatus.VALID)).isFalse();
        assertThat(ConsentPolicy.blocksAi(minor, TODAY, ConsentStatus.MISSING)).isTrue();
        assertThat(ConsentPolicy.blocksAi(minor, TODAY, ConsentStatus.REVOKED)).isTrue();
        assertThat(ConsentPolicy.blocksAi(minor, TODAY, ConsentStatus.SUPERSEDED)).isTrue();
        assertThat(ConsentPolicy.blocksAi(adult, TODAY, ConsentStatus.MISSING)).isFalse();
    }

    @Test
    void studentWhoTurnsEighteenStopsRequiringConsent() {
        LocalDate birth = LocalDate.of(2008, 9, 11);
        assertThat(ConsentPolicy.blocksAi(birth, TODAY, ConsentStatus.MISSING)).isTrue();
        assertThat(ConsentPolicy.blocksAi(birth, TODAY.plusDays(1), ConsentStatus.MISSING))
                .isFalse();
    }

    @Test
    void registrationOfAMinorWithoutConsentInputIsRefusedOnTheConsentField() {
        assertThatThrownBy(
                        () ->
                                ConsentPolicy.requireOnRegistration(
                                        LocalDate.of(2015, 1, 1), TODAY, ConsentStatus.MISSING, null))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error -> {
                            assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                            assertThat(error.details().get("fields"))
                                    .isEqualTo(
                                            List.of(
                                                    Map.of(
                                                            "field",
                                                            "consent",
                                                            "code",
                                                            "CONSENT_REQUIRED")));
                        });
    }

    @Test
    void registrationAcceptsAnAdultWithoutInputAndAMinorWithInput() {
        ConsentInput input =
                new ConsentInput("Maria Guardian", "MOTHER", "2026-09-01", 1, "SIGNED_PAPER");
        ConsentPolicy.requireOnRegistration(
                LocalDate.of(2000, 1, 1), TODAY, ConsentStatus.MISSING, null);
        ConsentPolicy.requireOnRegistration(
                LocalDate.of(2015, 1, 1), TODAY, ConsentStatus.MISSING, input);
    }

    @Test
    void editThatMakesTheStudentAMinorNeedsConsentInputUnlessAlreadyValid() {
        LocalDate minor = LocalDate.of(2015, 1, 1);
        assertThatThrownBy(
                        () ->
                                ConsentPolicy.requireOnRegistration(
                                        minor, TODAY, ConsentStatus.MISSING, null))
                .isInstanceOf(DomainException.class);
        ConsentPolicy.requireOnRegistration(minor, TODAY, ConsentStatus.VALID, null);
    }
}
