package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * FR-040: o registro de autoria só aceita identificadores em formato estrito. A porta {@code
 * AuthorshipRecorderPort} recebe o record direto, então o construtor é a última barreira contra
 * CPF, telefone, e-mail ou código gravados por engano.
 */
class AuthorshipRecordTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final String HASH = "a".repeat(64);

    private static AuthorshipRecord with(
            String operation, String errorCode, String hash, String id) {
        return new AuthorshipRecord(
                UUID.randomUUID(),
                NOW,
                null,
                null,
                ActionOrigin.WEB,
                operation,
                null,
                null,
                AuthorshipResult.FAILURE,
                errorCode,
                hash,
                id);
    }

    @Test
    void acceptsTheKnownIdentifierFormats() {
        List<String> correlations =
                List.of(
                        UUID.randomUUID().toString(),
                        "4711",
                        "1234567890",
                        "req-1",
                        "req-a1b2c3",
                        "load-1",
                        "startup-1");
        for (String correlation : correlations) {
            assertThatCode(() -> with("ADMIN_SIGN_IN", "UNKNOWN_EMAIL", HASH, correlation))
                    .as(correlation)
                    .doesNotThrowAnyException();
        }
        assertThatCode(() -> with("ADMIN_SIGN_IN", null, null, "req-1")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnOriginHashThatIsNotSixtyFourLowercaseHexCharacters() {
        List<String> invalid =
                List.of(
                        "origin-hash",
                        "a".repeat(63),
                        "a".repeat(65),
                        "A".repeat(64),
                        "g".repeat(64),
                        "52998224725",
                        "529.982.247-25",
                        "marta@school.com");
        for (String hash : invalid) {
            assertThatThrownBy(() -> with("ADMIN_SIGN_IN", null, hash, "req-1"))
                    .as(hash)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsACpfAsCorrelationWithOrWithoutMask() {
        for (String value : List.of("52998224725", "529.982.247-25", "529 982 247 25")) {
            assertThatThrownBy(() -> with("ADMIN_SIGN_IN", null, null, value))
                    .as(value)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsAnyElevenDigitSequenceEvenInsideAPrefixedToken() {
        for (String value : List.of("req-52998224725", "load-11987654321", "req-a11987654321b")) {
            assertThatThrownBy(() -> with("ADMIN_SIGN_IN", null, null, value))
                    .as(value)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsAPhoneAsCorrelationWithOrWithoutMask() {
        for (String value :
                List.of("11987654321", "(11) 98765-4321", "+5511987654321", "1198765-4321")) {
            assertThatThrownBy(() -> with("ADMIN_SIGN_IN", null, null, value))
                    .as(value)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsAnActivationOrAccessCodeAsCorrelationOrOriginHash() {
        for (String value : List.of("KQMXPTRWHNCV", "KQMX-PTRW-HNCV", "KQMXPTRW")) {
            assertThatThrownBy(() -> with("ADMIN_SIGN_IN", null, null, value))
                    .as(value)
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> with("ADMIN_SIGN_IN", null, value, "req-1"))
                    .as(value)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> with("ADMIN_SIGN_IN", null, null, "req-KQMXPTRWHNCV"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFreeTextAndBlankCorrelation() {
        for (String value : List.of("marta@school.com", " ", "", "req 1", "a".repeat(65))) {
            assertThatThrownBy(() -> with("ADMIN_SIGN_IN", null, null, value))
                    .as(value)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void operationAndErrorCodeMustBeUpperSnakeCodes() {
        assertThatThrownBy(() -> with("marta@school.com", null, null, "req-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> with("ADMIN_SIGN_IN", "52998224725", null, "req-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> with(" ", null, null, "req-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRejectionMessageNeverEchoesTheValue() {
        assertThatThrownBy(() -> with("ADMIN_SIGN_IN", null, null, "52998224725"))
                .hasMessageNotContaining("52998224725");
    }

    @Test
    void originHashIsOnlyAcceptedInAuthenticationAndBlockEvents() {
        for (String event : AuthorshipRecord.ORIGIN_HASH_EVENTS) {
            assertThatCode(() -> with(event, null, HASH, "req-1"))
                    .as(event)
                    .doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> with("STUDENT_REGISTER", null, HASH, "req-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(HASH);
        assertThatCode(() -> with("STUDENT_REGISTER", null, null, "req-1"))
                .doesNotThrowAnyException();
    }

    private static AuthorshipRecord withDetail(String operation, AuthorshipReadDetail detail) {
        return new AuthorshipRecord(
                UUID.randomUUID(),
                NOW,
                null,
                null,
                ActionOrigin.WEB,
                operation,
                null,
                null,
                AuthorshipResult.SUCCESS,
                null,
                null,
                "req-1",
                detail);
    }

    @Test
    void readDetailIsOnlyAcceptedInTheSchoolReadOperations() {
        AuthorshipReadDetail detail = AuthorshipReadDetail.ofQuery(null, null, null, null, 0);
        for (String operation : AuthorshipRecord.READ_DETAIL_OPERATIONS) {
            assertThatCode(() -> withDetail(operation, detail))
                    .as(operation)
                    .doesNotThrowAnyException();
        }
        assertThat(AuthorshipRecord.READ_DETAIL_OPERATIONS)
                .containsExactlyInAnyOrder(
                        // visão da escola (006)
                        "SCHOOL_HISTORY_QUERY",
                        "SCHOOL_REPORT_DOCUMENT_OPEN",
                        "SCHOOL_REPORT_DOCUMENT_DOWNLOAD",
                        // leituras e decisões do professor, consulta da trilha e consentimento
                        // (007)
                        "HISTORY_QUERY",
                        "REPORT_DOCUMENT_OPEN",
                        "REPORT_DOCUMENT_DOWNLOAD",
                        "REPORT_DOCUMENT_SENT",
                        "LESSON_REPORT_APPROVE",
                        "LESSON_REPORT_CANCEL",
                        "SEMESTER_REPORT_APPROVE",
                        "SEMESTER_REPORT_CANCEL",
                        "AUDIT_TRAIL_QUERY",
                        "CONSENT_RECORD");
        for (String operation : List.of("STUDENT_REGISTER", "ADMIN_SIGN_IN", "TEACHER_LIST")) {
            assertThatThrownBy(() -> withDetail(operation, detail))
                    .as(operation)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void itemCountIsOnlyAcceptedInTheOperationsThatCountItems() {
        AuthorshipReadDetail count = AuthorshipReadDetail.ofCount(40);
        for (String operation : AuthorshipRecord.ITEM_COUNT_OPERATIONS) {
            assertThatCode(() -> withDetail(operation, count))
                    .as(operation)
                    .doesNotThrowAnyException();
        }
        assertThat(AuthorshipRecord.ITEM_COUNT_OPERATIONS)
                .containsExactlyInAnyOrder(
                        "RETENTION_RUN",
                        "ERASURE_COMPLETED",
                        "AUDIT_TRAIL_PURGED",
                        "AUDIT_TRAIL_VERIFY");
        assertThatThrownBy(() -> withDetail("STUDENT_REGISTER", count))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordWithoutDetailStaysValidInAnyOperation() {
        assertThatCode(() -> withDetail("STUDENT_REGISTER", null)).doesNotThrowAnyException();
        assertThatCode(() -> withDetail("SCHOOL_HISTORY_QUERY", null)).doesNotThrowAnyException();
        assertThat(with("ADMIN_SIGN_IN", null, null, "req-1").readDetail()).isNull();
    }
}
