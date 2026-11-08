package br.com.felipeblaft.reports.usecase.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.operations.AlertStatus;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAuditTrail;
import br.com.felipeblaft.reports.doubles.InMemoryOperationalAlertRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.ImmediateTransaction;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.PersistentOperationalAlertService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lista, resumo e reconhecimento dos alertas (FR-056). */
class OperationalAlertReadUseCasesTest {

    private final FixedClock clock = new FixedClock();
    private final InMemoryAuditTrail trail = new InMemoryAuditTrail();
    private final InMemoryOperationalAlertRepository repository =
            new InMemoryOperationalAlertRepository();
    private final AuthorshipService authorship = new AuthorshipService(trail, clock);
    private final ImmediateTransaction transaction = new ImmediateTransaction();
    private final ActorAuthorizationService authorization =
            new ActorAuthorizationService(PermissionMatrix.standard());
    private final PersistentOperationalAlertService alerts =
            new PersistentOperationalAlertService(repository, authorship, transaction, clock);
    private final ListOperationalAlertsUseCase list =
            new ListOperationalAlertsUseCase(authorization, repository, authorship, transaction);
    private final AcknowledgeOperationalAlertUseCase acknowledge =
            new AcknowledgeOperationalAlertUseCase(
                    authorization, repository, alerts, authorship, transaction);

    @Test
    @DisplayName("lista ativos primeiro e grava o evento da leitura; o resumo não grava evento")
    void listsActiveFirstAndSummaryWritesNoEvent() {
        alerts.report(AlertType.RETENTION_FAILED, "retention", "RUN_FAILED");
        clock.advance(Duration.ofMinutes(1));
        alerts.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");
        alerts.resolve(AlertType.RETENTION_FAILED, "retention");
        int before = trail.records().size();

        var page = list.run(ActorFixtures.administratorWeb(), null, PageRequest.first(), "req-1");

        assertThat(page.items()).extracting(OperationalAlert::status)
                .containsExactly(AlertStatus.ACTIVE, AlertStatus.NORMALIZED);
        assertThat(trail.records()).hasSize(before + 1);
        assertThat(trail.records().getLast().operation()).isEqualTo("OPERATIONS_ALERT_READ");

        int afterList = trail.records().size();
        assertThat(list.activeCount(ActorFixtures.administratorWeb())).isEqualTo(1);
        assertThat(trail.records()).hasSize(afterList);
    }

    @Test
    @DisplayName("filtra por situação")
    void filtersByStatus() {
        alerts.report(AlertType.RETENTION_FAILED, "retention", "RUN_FAILED");
        alerts.resolve(AlertType.RETENTION_FAILED, "retention");
        alerts.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");

        var page =
                list.run(
                        ActorFixtures.administratorWeb(),
                        AlertStatus.NORMALIZED,
                        PageRequest.first(),
                        "req-1");

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().type()).isEqualTo(AlertType.RETENTION_FAILED);
    }

    @Test
    @DisplayName("professor não lê alertas nem o resumo")
    void teacherCannotRead() {
        assertThatThrownBy(
                        () ->
                                list.run(
                                        ActorFixtures.teacherWeb(),
                                        null,
                                        PageRequest.first(),
                                        "req-1"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> list.activeCount(ActorFixtures.teacherWeb()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("reconhecer a quebra da trilha normaliza e grava o evento do administrador")
    void acknowledgingTheChainAlertNormalizesIt() {
        alerts.report(AlertType.AUDIT_CHAIN_BROKEN, "audit-trail", "CHAIN_BROKEN");
        UUID id = repository.findActive().getFirst().id();

        OperationalAlert result = acknowledge.run(ActorFixtures.administratorWeb(), id, "req-1");

        assertThat(result.status()).isEqualTo(AlertStatus.NORMALIZED);
        assertThat(repository.countActive()).isZero();
        assertThat(trail.records())
                .anySatisfy(
                        r -> {
                            assertThat(r.operation()).isEqualTo("OPERATIONS_ALERT_ACK");
                            assertThat(r.result()).isEqualTo(AuthorshipResult.SUCCESS);
                            assertThat(r.targetId()).isEqualTo(id);
                        });
    }

    @Test
    @DisplayName("tipo que normaliza sozinho, ou alerta já normalizado, dá ALERT_NOT_ACKNOWLEDGEABLE")
    void otherTypesAndNormalizedAlertsAreNotAcknowledgeable() {
        alerts.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");
        UUID storage = repository.findActive().getFirst().id();
        alerts.report(AlertType.AUDIT_CHAIN_BROKEN, "audit-trail", "CHAIN_BROKEN");
        UUID chain =
                repository.findActive().stream()
                        .filter(a -> a.type() == AlertType.AUDIT_CHAIN_BROKEN)
                        .findFirst()
                        .orElseThrow()
                        .id();
        acknowledge.run(ActorFixtures.administratorWeb(), chain, "req-1");

        for (UUID id : new UUID[] {storage, chain}) {
            assertThatThrownBy(
                            () -> acknowledge.run(ActorFixtures.administratorWeb(), id, "req-2"))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.ALERT_NOT_ACKNOWLEDGEABLE));
        }
        assertThat(repository.findById(storage).orElseThrow().active()).isTrue();
    }

    @Test
    @DisplayName("alerta inexistente dá RESOURCE_NOT_FOUND e professor é negado")
    void unknownAlertAndTeacher() {
        assertThatThrownBy(
                        () ->
                                acknowledge.run(
                                        ActorFixtures.administratorWeb(),
                                        UUID.randomUUID(),
                                        "req-1"))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThatThrownBy(
                        () ->
                                acknowledge.run(
                                        ActorFixtures.teacherWeb(), UUID.randomUUID(), "req-1"))
                .isInstanceOf(DomainException.class);
    }
}
