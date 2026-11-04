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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Validação de {@link ConsentInput}, {@link GuardianConsent} e {@link ConsentTermVersion}. */
class ConsentValueObjectsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final String SENTINEL = "SENTINEL-GUARDIAN-NAME";

    private static ConsentTermVersion term(int number, boolean requiresNew) {
        return new ConsentTermVersion(
                UUID.randomUUID(), number, LocalDate.of(2026, 1, 1), "Text", requiresNew, ADMIN, NOW);
    }

    private static GuardianConsent record(ConsentInput input, List<ConsentTermVersion> terms) {
        return GuardianConsent.of(
                input, STUDENT, terms, TODAY, NOW, ADMIN, Role.ADMINISTRATOR, ActionOrigin.WEB);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> fields(DomainException error) {
        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        return (List<Map<String, String>>) error.details().get("fields");
    }

    @Test
    void validInputBecomesAConsentOfTheGivenTermVersion() {
        ConsentTermVersion one = term(1, false);
        GuardianConsent consent =
                record(new ConsentInput("Maria Guardian", "MOTHER", "2026-09-01", 1, "DIGITAL"), List.of(one));

        assertThat(consent.consentingName()).isEqualTo("Maria Guardian");
        assertThat(consent.relationship()).isEqualTo(ConsentRelationship.MOTHER);
        assertThat(consent.consentedOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(consent.termVersionId()).isEqualTo(one.id());
        assertThat(consent.termVersionNumber()).isEqualTo(1);
        assertThat(consent.collectionMethod()).isEqualTo(ConsentCollectionMethod.DIGITAL);
        assertThat(consent.recordedOrigin()).isEqualTo(ActionOrigin.WEB);
        assertThat(consent.revoked()).isFalse();
    }

    @Test
    void everyViolationIsReportedUnderTheConsentPrefix() {
        assertThatThrownBy(
                        () ->
                                record(
                                        new ConsentInput("A", "AUNT", "2026-09-11", 9, "FAX"),
                                        List.of(term(1, false))))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error ->
                                assertThat(fields(error))
                                        .extracting(entry -> entry.get("field"))
                                        .containsExactlyInAnyOrder(
                                                "consent.consentingName",
                                                "consent.relationship",
                                                "consent.consentedOn",
                                                "consent.termVersion",
                                                "consent.collectionMethod"));
    }

    @Test
    void missingFieldsAreViolationsToo() {
        assertThatThrownBy(() -> record(new ConsentInput(null, null, null, null, null), List.of(term(1, false))))
                .isInstanceOfSatisfying(
                        DomainException.class, error -> assertThat(fields(error)).hasSize(5));
    }

    @Test
    void invalidDateTextIsRefused() {
        assertThatThrownBy(
                        () ->
                                record(
                                        new ConsentInput("Maria Guardian", "MOTHER", "31/02/2026", 1, "DIGITAL"),
                                        List.of(term(1, false))))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error ->
                                assertThat(fields(error))
                                        .containsExactly(
                                                Map.of(
                                                        "field",
                                                        "consent.consentedOn",
                                                        "code",
                                                        "INVALID_DATA")));
    }

    @Test
    void todayIsAcceptedAndTheTelegramDateFormatToo() {
        ConsentTermVersion one = term(1, false);
        assertThat(record(new ConsentInput("Maria Guardian", "FATHER", "10/09/2026", 1, "OTHER"), List.of(one))
                        .consentedOn())
                .isEqualTo(TODAY);
    }

    @Test
    void termOlderThanTheLastOneRequiringNewConsentIsRefused() {
        List<ConsentTermVersion> terms = List.of(term(1, false), term(2, true), term(3, false));
        assertThatThrownBy(
                        () ->
                                record(
                                        new ConsentInput("Maria Guardian", "MOTHER", "2026-09-01", 1, "DIGITAL"),
                                        terms))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error ->
                                assertThat(fields(error))
                                        .containsExactly(
                                                Map.of(
                                                        "field",
                                                        "consent.termVersion",
                                                        "code",
                                                        "INVALID_DATA")));
        assertThat(
                        record(new ConsentInput("Maria Guardian", "MOTHER", "2026-09-01", 2, "DIGITAL"), terms)
                                .termVersionNumber())
                .isEqualTo(2);
        assertThat(
                        record(new ConsentInput("Maria Guardian", "MOTHER", "2026-09-01", 3, "DIGITAL"), terms)
                                .termVersionNumber())
                .isEqualTo(3);
    }

    @Test
    void revokeKeepsTheRecordAndTheRevokerRoleOnly() {
        GuardianConsent consent =
                record(
                        new ConsentInput("Maria Guardian", "MOTHER", "2026-09-01", 1, "DIGITAL"),
                        List.of(term(1, false)));
        GuardianConsent revoked = consent.revoke(LocalDate.of(2026, 9, 5), "STUDENT", ADMIN, TODAY);

        assertThat(revoked.revoked()).isTrue();
        assertThat(revoked.revokedOn()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(revoked.revokedBy()).isEqualTo("STUDENT");
        assertThat(revoked.revocationRecordedBy()).isEqualTo(ADMIN);
        assertThat(revoked.id()).isEqualTo(consent.id());
    }

    @Test
    void revokingTwiceIsRefused() {
        GuardianConsent revoked =
                record(
                                new ConsentInput("Maria Guardian", "MOTHER", "2026-09-01", 1, "DIGITAL"),
                                List.of(term(1, false)))
                        .revoke(LocalDate.of(2026, 9, 5), "MOTHER", ADMIN, TODAY);
        assertThatThrownBy(() -> revoked.revoke(LocalDate.of(2026, 9, 6), "MOTHER", ADMIN, TODAY))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error -> assertThat(error.code()).isEqualTo(ErrorCode.CONSENT_ALREADY_REVOKED));
    }

    @Test
    void revocationDateCannotBeBeforeTheConsentNorInTheFuture() {
        GuardianConsent consent =
                record(
                        new ConsentInput("Maria Guardian", "MOTHER", "2026-09-01", 1, "DIGITAL"),
                        List.of(term(1, false)));
        assertThatThrownBy(() -> consent.revoke(LocalDate.of(2026, 8, 31), "MOTHER", ADMIN, TODAY))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> consent.revoke(TODAY.plusDays(1), "MOTHER", ADMIN, TODAY))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> consent.revoke(TODAY, "NEIGHBOR", ADMIN, TODAY))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void erasureRemovesTheNameOfWhoConsented() {
        GuardianConsent consent =
                record(
                        new ConsentInput("Maria Guardian", "MOTHER", "2026-09-01", 1, "DIGITAL"),
                        List.of(term(1, false)));
        assertThat(consent.withoutName().consentingName()).isNull();
    }

    @Test
    void firstTermVersionIsNumberOne() {
        ConsentTermVersion first =
                ConsentTermVersion.publish(
                        Optional.empty(), TODAY, "Institutional text", true, ADMIN, TODAY, NOW);
        assertThat(first.number()).isEqualTo(1);
        assertThat(first.requiresNewConsent()).isTrue();
    }

    @Test
    void nextVersionIsSequential() {
        ConsentTermVersion current = term(4, false);
        assertThat(
                        ConsentTermVersion.publish(
                                        Optional.of(current), TODAY, "Text", false, ADMIN, TODAY, NOW)
                                .number())
                .isEqualTo(5);
    }

    @Test
    void textMustHaveBetweenOneAndTwentyThousandCharactersAndDateCannotBeFuture() {
        assertThatThrownBy(
                        () ->
                                ConsentTermVersion.publish(
                                        Optional.empty(), TODAY, "  ", false, ADMIN, TODAY, NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(
                        () ->
                                ConsentTermVersion.publish(
                                        Optional.empty(),
                                        TODAY,
                                        "x".repeat(20_001),
                                        false,
                                        ADMIN,
                                        TODAY,
                                        NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(
                        () ->
                                ConsentTermVersion.publish(
                                        Optional.empty(),
                                        TODAY.plusDays(1),
                                        "Text",
                                        false,
                                        ADMIN,
                                        TODAY,
                                        NOW))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        error ->
                                assertThat(fields(error))
                                        .extracting(entry -> entry.get("field"))
                                        .containsExactly("publishedOn"));
        assertThat(
                        ConsentTermVersion.publish(
                                        Optional.empty(), TODAY, "x".repeat(20_000), false, ADMIN, TODAY, NOW)
                                .text())
                .hasSize(20_000);
    }

    @Test
    void currentAndCompatibleVersionFollowTheTermHistory() {
        List<ConsentTermVersion> terms = List.of(term(3, false), term(1, false), term(2, true));
        assertThat(ConsentTermVersion.current(terms).orElseThrow().number()).isEqualTo(3);
        assertThat(ConsentTermVersion.compatibleFrom(terms)).isEqualTo(2);
        assertThat(ConsentTermVersion.compatibleFrom(List.of(term(1, false)))).isEqualTo(1);
        assertThat(ConsentTermVersion.compatibleFrom(List.of())).isEqualTo(1);
    }

    @Test
    void textualRepresentationNeverRevealsTheNameOfWhoConsented() {
        ConsentTermVersion one = term(1, false);
        ConsentInput input = new ConsentInput(SENTINEL, "MOTHER", "2026-09-01", 1, "DIGITAL");
        GuardianConsent consent = record(input, List.of(one));
        assertThat(input.toString()).doesNotContain(SENTINEL);
        assertThat(consent.toString()).doesNotContain(SENTINEL);
    }
}
