package br.com.felipeblaft.reports.usecase.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAuditTrail;
import br.com.felipeblaft.reports.doubles.RecordingAlertService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.usecase.audit.VerifyAuditChainUseCase.Result;
import br.com.felipeblaft.reports.usecase.audit.VerifyAuditChainUseCase.Status;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verificação da cadeia da trilha (FR-044): íntegra, quebrada, em lotes e a partir da âncora. */
class VerifyAuditChainUseCaseTest {

    private final FixedClock clock = new FixedClock();
    private final InMemoryAuditTrail trail = new InMemoryAuditTrail();
    private final AuthorshipService authorship = new AuthorshipService(trail, clock);
    private final RecordingAlertService alerts = new RecordingAlertService();
    private final VerifyAuditChainUseCase verify =
            new VerifyAuditChainUseCase(
                    new ActorAuthorizationService(PermissionMatrix.standard()),
                    trail,
                    authorship,
                    alerts,
                    3);
    private final Actor admin = ActorFixtures.administratorWeb();

    private void seed(int count) {
        for (int i = 0; i < count; i++) {
            clock.advance(Duration.ofSeconds(1));
            authorship.record(
                    admin,
                    Operation.STUDENT_REGISTER,
                    AuthorshipTargetType.STUDENT,
                    UUID.randomUUID(),
                    "req-" + i);
        }
    }

    @Test
    @DisplayName(
            "cadeia íntegra, em lotes menores que o total, dá INTACT e grava o evento com a quantidade")
    void intactChainAcrossBatches() {
        seed(7);

        Result result = verify.run(admin, "req-v");

        assertThat(result.status()).isEqualTo(Status.INTACT);
        assertThat(result.checkedEvents()).isEqualTo(7);
        assertThat(result.firstBrokenSequence()).isNull();
        AuthorshipRecord event = trail.records().getLast();
        assertThat(event.operation()).isEqualTo("AUDIT_TRAIL_VERIFY");
        assertThat(event.result()).isEqualTo(AuthorshipResult.SUCCESS);
        assertThat(event.readDetail().itemCount()).isEqualTo(7);
        assertThat(alerts.reported()).isEmpty();
    }

    @Test
    @DisplayName(
            "linha adulterada dá BROKEN com a primeira sequência quebrada e abre AUDIT_CHAIN_BROKEN")
    void tamperedRowIsDetected() {
        seed(7);
        trail.tamper(5);

        Result result = verify.run(admin, "req-v");

        assertThat(result.status()).isEqualTo(Status.BROKEN);
        assertThat(result.firstBrokenSequence()).isEqualTo(5L);
        assertThat(alerts.reported())
                .extracting(RecordingAlertService.Reported::type)
                .containsExactly(AlertType.AUDIT_CHAIN_BROKEN);
        AuthorshipRecord event = trail.records().getLast();
        assertThat(event.operation()).isEqualTo("AUDIT_TRAIL_VERIFY");
        assertThat(event.result()).isEqualTo(AuthorshipResult.FAILURE);
        assertThat(event.errorCode()).isEqualTo("AUDIT_CHAIN_BROKEN");
    }

    @Test
    @DisplayName("depois do expurgo a verificação parte da âncora e continua íntegra")
    void startsFromTheAnchorAfterAPurge() {
        seed(6);
        trail.purgeOlderThan(clock.now().minusSeconds(2));

        Result result = verify.run(admin, "req-v");

        assertThat(trail.latestAnchor()).isPresent();
        assertThat(result.status()).isEqualTo(Status.INTACT);
        assertThat(result.checkedEvents()).isGreaterThan(0);
    }

    @Test
    @DisplayName("sem eventos a cadeia é íntegra com zero eventos verificados")
    void emptyTrailIsIntact() {
        Result result = verify.run(admin, "req-v");

        assertThat(result.status()).isEqualTo(Status.INTACT);
        assertThat(result.checkedEvents()).isZero();
    }

    @Test
    @DisplayName("professor não verifica; a retenção (ator de sistema) pode")
    void onlyAdministratorAndRetentionMayVerify() {
        seed(2);

        assertThatThrownBy(() -> verify.run(ActorFixtures.teacherWeb(), "req-v"))
                .isInstanceOf(DomainException.class);
        assertThat(verify.run(Actor.retention(), "req-v").status()).isEqualTo(Status.INTACT);
    }

    @Test
    @DisplayName("a última verificação fica consultável")
    void lastVerificationIsReadable() {
        seed(3);
        verify.run(admin, "req-v");

        assertThat(trail.lastVerification()).isPresent();
        assertThat(trail.lastVerification().get().intact()).isTrue();
        assertThat(trail.lastVerification().get().checkedEvents()).isEqualTo(3);
    }
}
