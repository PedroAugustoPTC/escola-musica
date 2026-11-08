package br.com.felipeblaft.reports.domain.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ErasureRequestTest {

    private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);
    private static final UUID ADMIN = UUID.randomUUID();
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    private static ErasureRequest received() {
        return ErasureRequest.register(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "MOTHER",
                "Requester Name",
                TODAY,
                "EMAIL",
                "PROTO-2026/001",
                ADMIN,
                TODAY,
                NOW);
    }

    private static ErasureScope scope() {
        return new ErasureScope(java.util.Map.of("APPROVED", 2), java.util.Map.of(), 2, 1, 1);
    }

    @Test
    void registersInReceivedStatusWithoutToken() {
        ErasureRequest request = received();

        assertThat(request.status()).isEqualTo(ErasureStatus.RECEIVED);
        assertThat(request.confirmation()).isEmpty();
        assertThat(request.scope()).isEmpty();
    }

    @Test
    void rejectsProtocolOutsideThePattern() {
        for (String protocol : new String[] {"", "has space", "ação", "x".repeat(41), null}) {
            assertThatThrownBy(
                            () ->
                                    ErasureRequest.register(
                                            UUID.randomUUID(),
                                            UUID.randomUUID(),
                                            "STUDENT",
                                            "Requester Name",
                                            TODAY,
                                            "PHONE",
                                            protocol,
                                            ADMIN,
                                            TODAY,
                                            NOW))
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
        }
    }

    @Test
    void rejectsFutureRequestDate() {
        assertThatThrownBy(
                        () ->
                                ErasureRequest.register(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "STUDENT",
                                        "Requester Name",
                                        TODAY.plusDays(1),
                                        "PHONE",
                                        "P1",
                                        ADMIN,
                                        TODAY,
                                        NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void previewIssuesNewTokenAndInvalidatesThePreviousOne() {
        ErasureRequest first = received().withPreview(HASH_A, NOW);
        ErasureRequest second = first.withPreview(HASH_B, NOW.plusSeconds(30));

        assertThat(second.status()).isEqualTo(ErasureStatus.RECEIVED);
        assertThat(second.confirmation().orElseThrow().tokenHash()).isEqualTo(HASH_B);
        assertThat(second.confirmation().orElseThrow().matches(HASH_A, NOW.plusSeconds(31)))
                .isFalse();
        assertThat(second.confirmation().orElseThrow().matches(HASH_B, NOW.plusSeconds(31)))
                .isTrue();
    }

    @Test
    void tokenIsValidForTenMinutes() {
        ErasureConfirmation confirmation =
                received().withPreview(HASH_A, NOW).confirmation().orElseThrow();

        assertThat(confirmation.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(confirmation.matches(HASH_A, NOW.plus(Duration.ofMinutes(10)).minusMillis(1)))
                .isTrue();
        assertThat(confirmation.matches(HASH_A, NOW.plus(Duration.ofMinutes(10)))).isFalse();
    }

    @Test
    void confirmationWordMustBeExactlyEliminar() {
        assertThat(ErasureConfirmation.isConfirmationWord("ELIMINAR")).isTrue();
        for (String word :
                new String[] {"eliminar", "Eliminar", " ELIMINAR", "ELIMINAR ", "", null}) {
            assertThat(ErasureConfirmation.isConfirmationWord(word)).isFalse();
        }
    }

    @Test
    void confirmMovesToInProgressAndConsumesTheToken() {
        ErasureRequest request = received().withPreview(HASH_A, NOW);

        ErasureRequest confirmed = request.confirm(HASH_A, ADMIN, scope(), NOW.plusSeconds(60));

        assertThat(confirmed.status()).isEqualTo(ErasureStatus.IN_PROGRESS);
        assertThat(confirmed.confirmation()).isEmpty();
        assertThat(confirmed.confirmedBy()).isEqualTo(ADMIN);
        assertThat(confirmed.confirmedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(confirmed.scope()).contains(scope());
    }

    @Test
    void confirmWithExpiredOrWrongTokenFailsWithConfirmationExpired() {
        ErasureRequest request = received().withPreview(HASH_A, NOW);

        assertThatThrownBy(
                        () ->
                                request.confirm(
                                        HASH_A, ADMIN, scope(), NOW.plus(Duration.ofMinutes(11))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.CONFIRMATION_EXPIRED);
        assertThatThrownBy(() -> request.confirm(HASH_B, ADMIN, scope(), NOW.plusSeconds(1)))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.CONFIRMATION_EXPIRED);
        assertThatThrownBy(() -> received().confirm(HASH_A, ADMIN, scope(), NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.CONFIRMATION_EXPIRED);
    }

    @Test
    void completeClearsRequesterNameAndStampsCompletion() {
        ErasureRequest inProgress =
                received()
                        .withPreview(HASH_A, NOW)
                        .confirm(HASH_A, ADMIN, scope(), NOW.plusSeconds(5));

        ErasureRequest completed = inProgress.complete(NOW.plusSeconds(9));

        assertThat(completed.status()).isEqualTo(ErasureStatus.COMPLETED);
        assertThat(completed.requesterName()).isNull();
        assertThat(completed.completedAt()).isEqualTo(NOW.plusSeconds(9));
    }

    @Test
    void completedRequestRejectsPreviewAndConfirm() {
        ErasureRequest completed =
                received()
                        .withPreview(HASH_A, NOW)
                        .confirm(HASH_A, ADMIN, scope(), NOW)
                        .complete(NOW);

        assertThatThrownBy(() -> completed.withPreview(HASH_B, NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.ERASURE_ALREADY_COMPLETED);
        assertThatThrownBy(() -> completed.confirm(HASH_B, ADMIN, scope(), NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.ERASURE_ALREADY_COMPLETED);
    }

    @Test
    void inProgressRequestCanBePreviewedAgainToResume() {
        ErasureRequest inProgress =
                received().withPreview(HASH_A, NOW).confirm(HASH_A, ADMIN, scope(), NOW);

        ErasureRequest again = inProgress.withPreview(HASH_B, NOW.plusSeconds(120));

        assertThat(again.status()).isEqualTo(ErasureStatus.IN_PROGRESS);
        assertThat(again.confirmation().orElseThrow().tokenHash()).isEqualTo(HASH_B);
        assertThat(again.confirm(HASH_B, ADMIN, scope(), NOW.plusSeconds(130)).status())
                .isEqualTo(ErasureStatus.IN_PROGRESS);
    }

    @Test
    void toStringNeverRevealsTheRequesterName() {
        assertThat(received().toString()).doesNotContain("Requester Name");
    }
}
