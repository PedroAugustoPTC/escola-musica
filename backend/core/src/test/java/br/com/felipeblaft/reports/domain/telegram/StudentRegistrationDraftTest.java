package br.com.felipeblaft.reports.domain.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.GuardianPart;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Cadastro em andamento do Telegram (FR-036): estado, prazo, token de uso único, sem PII. */
class StudentRegistrationDraftTest {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");
    private static final Duration VALIDITY = Duration.ofMinutes(30);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);
    private static final LocalDate MINOR = LocalDate.of(2014, 5, 10);
    private static final LocalDate ADULT = LocalDate.of(1990, 3, 15);

    private final UUID link = UUID.randomUUID();

    private StudentRegistrationDraft create() {
        return StudentRegistrationDraft.start(link, Kind.CREATE, null, T0, VALIDITY);
    }

    private StudentRegistrationDraft update() {
        return StudentRegistrationDraft.start(link, Kind.UPDATE, UUID.randomUUID(), T0, VALIDITY);
    }

    private StudentRegistrationDraft withGuardian(StudentRegistrationDraft draft) {
        return draft.withGuardianName("Mãe", T0, VALIDITY)
                .withGuardianPhone("11912345678", T0, VALIDITY)
                .withGuardianEmail("mae@example.com", T0, VALIDITY);
    }

    @Test
    void startCreatesAnEmptyDraftWithTheInactivityDeadline() {
        StudentRegistrationDraft draft = create();

        assertThat(draft.linkId()).isEqualTo(link);
        assertThat(draft.kind()).isEqualTo(Kind.CREATE);
        assertThat(draft.providedFields()).isEmpty();
        assertThat(draft.confirmationToken()).isNull();
        assertThat(draft.createdAt()).isEqualTo(T0);
        assertThat(draft.updatedAt()).isEqualTo(T0);
        assertThat(draft.expiresAt()).isEqualTo(T0.plus(VALIDITY));
    }

    @Test
    void createHasNoStudentAndTheOtherKindsRequireOne() {
        UUID student = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                StudentRegistrationDraft.start(
                                        link, Kind.CREATE, student, T0, VALIDITY))
                .isInstanceOf(IllegalArgumentException.class);
        for (Kind kind : new Kind[] {Kind.UPDATE, Kind.INACTIVATE, Kind.REACTIVATE}) {
            assertThatThrownBy(() -> StudentRegistrationDraft.start(link, kind, null, T0, VALIDITY))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(
                            StudentRegistrationDraft.start(link, kind, student, T0, VALIDITY)
                                    .studentId())
                    .isEqualTo(student);
        }
    }

    @Test
    void kindsTellWhichOnesTargetAStudentAndWhichOnlyConfirm() {
        assertThat(Kind.CREATE.targetsExistingStudent()).isFalse();
        assertThat(Kind.UPDATE.targetsExistingStudent()).isTrue();
        assertThat(Kind.INACTIVATE.targetsExistingStudent()).isTrue();
        assertThat(Kind.REACTIVATE.targetsExistingStudent()).isTrue();
        assertThat(Kind.CREATE.confirmationOnly()).isFalse();
        assertThat(Kind.UPDATE.confirmationOnly()).isFalse();
        assertThat(Kind.INACTIVATE.confirmationOnly()).isTrue();
        assertThat(Kind.REACTIVATE.confirmationOnly()).isTrue();
    }

    @Test
    void expiresExactlyAtTheDeadline() {
        StudentRegistrationDraft draft = create();

        assertThat(draft.expired(T0.plus(VALIDITY).minusMillis(1))).isFalse();
        assertThat(draft.expired(T0.plus(VALIDITY))).isTrue();
        assertThat(draft.expired(T0.plus(VALIDITY).plusSeconds(1))).isTrue();
    }

    @Test
    void everyFieldRenewsTheDeadlineAndRecordsTheField() {
        Instant later = T0.plusSeconds(600);
        StudentRegistrationDraft draft =
                create().withName("Ana", later, VALIDITY).withBirthDate(MINOR, later, VALIDITY);

        assertThat(draft.name()).isEqualTo("Ana");
        assertThat(draft.birthDate()).isEqualTo(MINOR);
        assertThat(draft.providedFields()).containsExactlyInAnyOrder(Field.NAME, Field.BIRTH_DATE);
        assertThat(draft.createdAt()).isEqualTo(T0);
        assertThat(draft.updatedAt()).isEqualTo(later);
        assertThat(draft.expiresAt()).isEqualTo(later.plus(VALIDITY));
    }

    @Test
    void cpfIsKeptOnlyEncryptedAndAsKeyedHash() {
        byte[] encrypted = {1, 2, 3};
        StudentRegistrationDraft draft = create().withCpf(encrypted, "hash-1", T0, VALIDITY);

        assertThat(draft.cpfEncrypted()).containsExactly(1, 2, 3);
        assertThat(draft.cpfHash()).isEqualTo("hash-1");
        assertThat(draft.providedFields()).containsExactly(Field.CPF);
        assertThatThrownBy(() -> create().withCpf(null, "h", T0, VALIDITY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> create().withCpf(encrypted, null, T0, VALIDITY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void theEncryptedArrayNeverEscapes() {
        byte[] encrypted = {1, 2, 3};
        StudentRegistrationDraft draft = create().withCpf(encrypted, "hash-1", T0, VALIDITY);

        encrypted[0] = 99;
        draft.cpfEncrypted()[1] = 99;

        assertThat(draft.cpfEncrypted()).containsExactly(1, 2, 3);
        assertThat(create().cpfEncrypted()).isNull();
    }

    @Test
    void equalityComparesTheArrayByContent() {
        StudentRegistrationDraft one = create().withCpf(new byte[] {1, 2}, "h", T0, VALIDITY);
        StudentRegistrationDraft same = create().withCpf(new byte[] {1, 2}, "h", T0, VALIDITY);
        StudentRegistrationDraft otherBytes =
                create().withCpf(new byte[] {1, 3}, "h", T0, VALIDITY);
        StudentRegistrationDraft otherName = one.withName("Ana", T0, VALIDITY);
        StudentRegistrationDraft other = update();

        assertThat(one).isEqualTo(one).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(one).isNotEqualTo(otherBytes).isNotEqualTo(otherName).isNotEqualTo("x");
        assertThat(one).isNotEqualTo(other);
        assertThat(create()).isEqualTo(create()).hasSameHashCodeAs(create());
    }

    @Test
    void equalityLooksAtEveryField() {
        StudentRegistrationDraft base =
                withGuardian(
                                create().withName("Ana", T0, VALIDITY)
                                        .withBirthDate(MINOR, T0, VALIDITY)
                                        .withCpf(new byte[] {1}, "h", T0, VALIDITY))
                        .withInstrument("Violão", T0, VALIDITY)
                        .withConfirmationToken("ABCD2345", T0, VALIDITY);
        UUID student = UUID.randomUUID();
        java.util.List<StudentRegistrationDraft> others =
                java.util.List.of(
                        new StudentRegistrationDraft(
                                UUID.randomUUID(),
                                base.kind(),
                                base.studentId(),
                                base.providedFields(),
                                base.name(),
                                base.instrument(),
                                base.birthDate(),
                                base.cpfEncrypted(),
                                base.cpfHash(),
                                base.guardianName(),
                                base.guardianPhone(),
                                base.guardianEmail(),
                                base.confirmationToken(),
                                base.createdAt(),
                                base.updatedAt(),
                                base.expiresAt()),
                        copy(
                                base,
                                base.linkId(),
                                Kind.UPDATE,
                                student,
                                "Ana",
                                "Violão",
                                "h",
                                "Mãe",
                                "11912345678",
                                "mae@example.com",
                                "ABCD2345",
                                T0,
                                T0,
                                base.expiresAt()),
                        copy(
                                base,
                                base.linkId(),
                                base.kind(),
                                null,
                                "Bia",
                                "Violão",
                                "h",
                                "Mãe",
                                "11912345678",
                                "mae@example.com",
                                "ABCD2345",
                                T0,
                                T0,
                                base.expiresAt()),
                        copy(
                                base,
                                base.linkId(),
                                base.kind(),
                                null,
                                "Ana",
                                "Piano",
                                "h",
                                "Mãe",
                                "11912345678",
                                "mae@example.com",
                                "ABCD2345",
                                T0,
                                T0,
                                base.expiresAt()),
                        copy(
                                base,
                                base.linkId(),
                                base.kind(),
                                null,
                                "Ana",
                                "Violão",
                                "x",
                                "Mãe",
                                "11912345678",
                                "mae@example.com",
                                "ABCD2345",
                                T0,
                                T0,
                                base.expiresAt()),
                        copy(
                                base,
                                base.linkId(),
                                base.kind(),
                                null,
                                "Ana",
                                "Violão",
                                "h",
                                "Pai",
                                "11912345678",
                                "mae@example.com",
                                "ABCD2345",
                                T0,
                                T0,
                                base.expiresAt()),
                        copy(
                                base,
                                base.linkId(),
                                base.kind(),
                                null,
                                "Ana",
                                "Violão",
                                "h",
                                "Mãe",
                                "11900000000",
                                "mae@example.com",
                                "ABCD2345",
                                T0,
                                T0,
                                base.expiresAt()),
                        copy(
                                base,
                                base.linkId(),
                                base.kind(),
                                null,
                                "Ana",
                                "Violão",
                                "h",
                                "Mãe",
                                "11912345678",
                                "pai@example.com",
                                "ABCD2345",
                                T0,
                                T0,
                                base.expiresAt()),
                        copy(
                                base,
                                base.linkId(),
                                base.kind(),
                                null,
                                "Ana",
                                "Violão",
                                "h",
                                "Mãe",
                                "11912345678",
                                "mae@example.com",
                                "ZZZZ2345",
                                T0,
                                T0,
                                base.expiresAt()),
                        copy(
                                base,
                                base.linkId(),
                                base.kind(),
                                null,
                                "Ana",
                                "Violão",
                                "h",
                                "Mãe",
                                "11912345678",
                                "mae@example.com",
                                "ABCD2345",
                                T0.minusSeconds(1),
                                T0,
                                base.expiresAt()),
                        copy(
                                base,
                                base.linkId(),
                                base.kind(),
                                null,
                                "Ana",
                                "Violão",
                                "h",
                                "Mãe",
                                "11912345678",
                                "mae@example.com",
                                "ABCD2345",
                                T0,
                                T0.plusSeconds(1),
                                base.expiresAt()),
                        copy(
                                base,
                                base.linkId(),
                                base.kind(),
                                null,
                                "Ana",
                                "Violão",
                                "h",
                                "Mãe",
                                "11912345678",
                                "mae@example.com",
                                "ABCD2345",
                                T0,
                                T0,
                                base.expiresAt().plusSeconds(1)));
        for (StudentRegistrationDraft other : others) {
            assertThat(other).isNotEqualTo(base);
        }
        assertThat(
                        copy(
                                base,
                                base.linkId(),
                                base.kind(),
                                null,
                                "Ana",
                                "Violão",
                                "h",
                                "Mãe",
                                "11912345678",
                                "mae@example.com",
                                "ABCD2345",
                                T0,
                                T0,
                                base.expiresAt()))
                .isEqualTo(base);
    }

    private static StudentRegistrationDraft copy(
            StudentRegistrationDraft base,
            UUID link,
            Kind kind,
            UUID student,
            String name,
            String instrument,
            String hash,
            String guardianName,
            String phone,
            String email,
            String token,
            Instant created,
            Instant updated,
            Instant expires) {
        return new StudentRegistrationDraft(
                link,
                kind,
                student,
                base.providedFields(),
                name,
                instrument,
                base.birthDate(),
                base.cpfEncrypted(),
                hash,
                guardianName,
                phone,
                email,
                token,
                created,
                updated,
                expires);
    }

    @Test
    void instrumentCanBeSkipped() {
        StudentRegistrationDraft draft = create().withInstrument(null, T0, VALIDITY);

        assertThat(draft.instrument()).isNull();
        assertThat(draft.providedFields()).containsExactly(Field.INSTRUMENT);
    }

    @Test
    void guardianRequiredFollowsTheAgeOnTheGivenDay() {
        assertThat(create().guardianRequired(TODAY)).isFalse();
        assertThat(create().withBirthDate(MINOR, T0, VALIDITY).guardianRequired(TODAY)).isTrue();
        assertThat(create().withBirthDate(ADULT, T0, VALIDITY).guardianRequired(TODAY)).isFalse();
        // faz 18 anos hoje: já é adulto; falta um dia: ainda é menor
        assertThat(
                        create().withBirthDate(LocalDate.of(2008, 9, 21), T0, VALIDITY)
                                .guardianRequired(TODAY))
                .isFalse();
        assertThat(
                        create().withBirthDate(LocalDate.of(2008, 9, 22), T0, VALIDITY)
                                .guardianRequired(TODAY))
                .isTrue();
    }

    @Test
    void createAsksNameBirthDateCpfGuardianAndInstrumentInOrder() {
        StudentRegistrationDraft draft = create();
        assertThat(draft.nextField(TODAY)).isEqualTo(Field.NAME);
        draft = draft.withName("Ana", T0, VALIDITY);
        assertThat(draft.nextField(TODAY)).isEqualTo(Field.BIRTH_DATE);
        draft = draft.withBirthDate(MINOR, T0, VALIDITY);
        assertThat(draft.nextField(TODAY)).isEqualTo(Field.CPF);
        draft = draft.withCpf(new byte[] {1}, "h", T0, VALIDITY);
        assertThat(draft.nextField(TODAY)).isEqualTo(Field.GUARDIAN);
        draft = withGuardian(draft);
        assertThat(draft.nextField(TODAY)).isEqualTo(Field.CONSENT);
        draft = draft.withConsent(completeConsent(), T0, VALIDITY);
        assertThat(draft.nextField(TODAY)).isEqualTo(Field.INSTRUMENT);
        assertThat(draft.readyToConfirm(TODAY)).isFalse();
        draft = draft.withInstrument("Violão", T0, VALIDITY);
        assertThat(draft.nextField(TODAY)).isNull();
        assertThat(draft.readyToConfirm(TODAY)).isTrue();
    }

    @Test
    void createSkipsTheGuardianForAnAdult() {
        StudentRegistrationDraft draft =
                create().withName("Ana", T0, VALIDITY)
                        .withBirthDate(ADULT, T0, VALIDITY)
                        .withCpf(new byte[] {1}, "h", T0, VALIDITY);

        assertThat(draft.nextField(TODAY)).isEqualTo(Field.INSTRUMENT);
    }

    @Test
    void updateOnlyAsksForTheGuardianWhenTheNewDateMakesTheStudentAMinor() {
        StudentRegistrationDraft draft = update();
        assertThat(draft.nextField(TODAY)).isNull();
        assertThat(draft.readyToConfirm(TODAY)).isFalse();

        draft = draft.withName("Ana", T0, VALIDITY);
        assertThat(draft.nextField(TODAY)).isNull();
        assertThat(draft.readyToConfirm(TODAY)).isTrue();

        draft = draft.withBirthDate(MINOR, T0, VALIDITY);
        assertThat(draft.nextField(TODAY)).isEqualTo(Field.GUARDIAN);
        assertThat(draft.readyToConfirm(TODAY)).isFalse();

        draft = withGuardian(draft);
        assertThat(draft.nextField(TODAY)).isEqualTo(Field.CONSENT);
        assertThat(draft.readyToConfirm(TODAY)).isFalse();

        draft = draft.withConsent(completeConsent(), T0, VALIDITY);
        assertThat(draft.nextField(TODAY)).isNull();
        assertThat(draft.readyToConfirm(TODAY)).isTrue();
    }

    private static br.com.felipeblaft.reports.domain.telegram.ConsentDraft completeConsent() {
        return new br.com.felipeblaft.reports.domain.telegram.ConsentDraft(
                1, "Maria Guardian", "MOTHER", "2026-09-01", "DIGITAL");
    }

    @Test
    void statusKindsHaveNoFieldAndAreAlwaysReadyToConfirm() {
        for (Kind kind : new Kind[] {Kind.INACTIVATE, Kind.REACTIVATE}) {
            StudentRegistrationDraft draft =
                    StudentRegistrationDraft.start(link, kind, UUID.randomUUID(), T0, VALIDITY);
            assertThat(draft.nextField(TODAY)).isNull();
            assertThat(draft.readyToConfirm(TODAY)).isTrue();
            assertThat(draft.providedFields()).isEmpty();
        }
    }

    @Test
    void guardianIsAskedPartByPartAndOnlyCompleteWithTheEmail() {
        StudentRegistrationDraft draft = create();
        assertThat(draft.nextGuardianPart()).isEqualTo(GuardianPart.NAME);

        draft = draft.withGuardianName("Mãe", T0, VALIDITY);
        assertThat(draft.nextGuardianPart()).isEqualTo(GuardianPart.PHONE);
        assertThat(draft.providedFields()).doesNotContain(Field.GUARDIAN);

        draft = draft.withGuardianPhone("11912345678", T0, VALIDITY);
        assertThat(draft.nextGuardianPart()).isEqualTo(GuardianPart.EMAIL);
        assertThat(draft.providedFields()).doesNotContain(Field.GUARDIAN);

        draft = draft.withGuardianEmail("mae@example.com", T0, VALIDITY);
        assertThat(draft.nextGuardianPart()).isNull();
        assertThat(draft.providedFields()).contains(Field.GUARDIAN);
        assertThat(draft.guardianName()).isEqualTo("Mãe");
        assertThat(draft.guardianPhone()).isEqualTo("11912345678");
        assertThat(draft.guardianEmail()).isEqualTo("mae@example.com");
    }

    @Test
    void emailBeforeThePhoneDoesNotCompleteTheGuardian() {
        StudentRegistrationDraft draft =
                create().withGuardianName("Mãe", T0, VALIDITY)
                        .withGuardianEmail("mae@example.com", T0, VALIDITY);

        assertThat(draft.providedFields()).doesNotContain(Field.GUARDIAN);
    }

    @Test
    void restartingTheGuardianDiscardsThePhoneAndTheEmail() {
        StudentRegistrationDraft restarted =
                withGuardian(create()).withGuardianName("Pai", T0, VALIDITY);

        assertThat(restarted.guardianName()).isEqualTo("Pai");
        assertThat(restarted.guardianPhone()).isNull();
        assertThat(restarted.guardianEmail()).isNull();
        assertThat(restarted.providedFields()).doesNotContain(Field.GUARDIAN);
        assertThat(restarted.nextGuardianPart()).isEqualTo(GuardianPart.PHONE);
    }

    @Test
    void withoutGuardianDiscardsEverythingAboutTheGuardian() {
        StudentRegistrationDraft complete = withGuardian(create().withName("Ana", T0, VALIDITY));
        StudentRegistrationDraft partial = create().withGuardianName("Mãe", T0, VALIDITY);

        for (StudentRegistrationDraft draft : new StudentRegistrationDraft[] {complete, partial}) {
            StudentRegistrationDraft cleared = draft.withoutGuardian(T0, VALIDITY);
            assertThat(cleared.guardianName()).isNull();
            assertThat(cleared.guardianPhone()).isNull();
            assertThat(cleared.guardianEmail()).isNull();
            assertThat(cleared.providedFields()).doesNotContain(Field.GUARDIAN);
            assertThat(cleared.nextGuardianPart()).isEqualTo(GuardianPart.NAME);
        }
        assertThat(complete.withoutGuardian(T0, VALIDITY).name()).isEqualTo("Ana");
        StudentRegistrationDraft untouched = create().withName("Ana", T0, VALIDITY);
        assertThat(untouched.withoutGuardian(T0.plusSeconds(5), VALIDITY)).isSameAs(untouched);
    }

    @Test
    void changingAnyFieldInvalidatesThePreviousToken() {
        String token = StudentRegistrationDraft.newConfirmationToken();
        StudentRegistrationDraft confirmable =
                create().withName("Ana", T0, VALIDITY).withConfirmationToken(token, T0, VALIDITY);
        assertThat(confirmable.matchesToken(token)).isTrue();

        assertThat(confirmable.withName("Bia", T0, VALIDITY).confirmationToken()).isNull();
        assertThat(confirmable.withBirthDate(MINOR, T0, VALIDITY).confirmationToken()).isNull();
        assertThat(confirmable.withCpf(new byte[] {1}, "h", T0, VALIDITY).confirmationToken())
                .isNull();
        assertThat(confirmable.withInstrument("Violão", T0, VALIDITY).confirmationToken()).isNull();
        assertThat(confirmable.withGuardianName("M", T0, VALIDITY).confirmationToken()).isNull();
        assertThat(confirmable.withGuardianPhone("1", T0, VALIDITY).confirmationToken()).isNull();
        assertThat(confirmable.withGuardianEmail("e", T0, VALIDITY).confirmationToken()).isNull();
    }

    @Test
    void tokenMatchesOnlyTheExactValue() {
        StudentRegistrationDraft draft = create().withConfirmationToken("ABCD2345", T0, VALIDITY);

        assertThat(draft.matchesToken("ABCD2345")).isTrue();
        assertThat(draft.matchesToken("ABCD2346")).isFalse();
        assertThat(draft.matchesToken("abcd2345")).isFalse();
        assertThat(draft.matchesToken("ABCD234")).isFalse();
        assertThat(draft.matchesToken(null)).isFalse();
        assertThat(create().matchesToken("ABCD2345")).isFalse();
        assertThat(create().matchesToken(null)).isFalse();
    }

    @Test
    void storingTheTokenRenewsTheDeadlineAndKeepsTheFields() {
        Instant later = T0.plusSeconds(300);
        StudentRegistrationDraft draft =
                create().withName("Ana", T0, VALIDITY)
                        .withConfirmationToken("ABCD2345", later, VALIDITY);

        assertThat(draft.name()).isEqualTo("Ana");
        assertThat(draft.providedFields()).containsExactly(Field.NAME);
        assertThat(draft.expiresAt()).isEqualTo(later.plus(VALIDITY));
        assertThat(draft.createdAt()).isEqualTo(T0);
        assertThatThrownBy(() -> create().withConfirmationToken(null, T0, VALIDITY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void generatedTokensHaveEightCharactersFromTheSafeAlphabetAndDiffer() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = StudentRegistrationDraft.newConfirmationToken();
            assertThat(token).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
            tokens.add(token);
        }
        assertThat(tokens).hasSizeGreaterThan(190);
    }

    @Test
    void toStringRevealsNoPersonalData() {
        StudentRegistrationDraft draft =
                withGuardian(
                                create().withName("Ana Souza", T0, VALIDITY)
                                        .withBirthDate(MINOR, T0, VALIDITY)
                                        .withCpf(
                                                new byte[] {7, 7}, "cpf-hash-secret", T0, VALIDITY))
                        .withInstrument("Violão", T0, VALIDITY)
                        .withConfirmationToken("ABCD2345", T0, VALIDITY);

        assertThat(draft.toString())
                .doesNotContain("Ana", "Souza", "2014", "cpf-hash", "11912345678", "mae@")
                .doesNotContain("Violão", "ABCD2345");
    }

    @Test
    void mandatoryFieldsAreChecked() {
        assertThatThrownBy(
                        () ->
                                new StudentRegistrationDraft(
                                        null,
                                        Kind.CREATE,
                                        null,
                                        Set.of(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        T0,
                                        T0,
                                        T0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void consentKindAsksOnlyForTheConsentAndIsReadyOnceItIsComplete() {
        StudentRegistrationDraft draft =
                StudentRegistrationDraft.start(link, Kind.CONSENT, UUID.randomUUID(), T0, VALIDITY);

        assertThat(Kind.CONSENT.targetsExistingStudent()).isTrue();
        assertThat(Kind.CONSENT.confirmationOnly()).isFalse();
        assertThat(draft.nextField(TODAY)).isEqualTo(Field.CONSENT);
        assertThat(draft.readyToConfirm(TODAY)).isFalse();

        ConsentDraft complete =
                new ConsentDraft(1, "Maria Souza", "MOTHER", "2026-09-21", "DIGITAL");
        StudentRegistrationDraft done = draft.withConsent(complete, T0, VALIDITY);

        assertThat(done.nextField(TODAY)).isNull();
        assertThat(done.readyToConfirm(TODAY)).isTrue();
    }
}
