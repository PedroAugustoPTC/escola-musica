package br.com.felipeblaft.reports.usecase.operations;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import br.com.felipeblaft.reports.port.OperationalAlertRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.OperationalAlertService;
import java.util.Objects;
import java.util.UUID;

/**
 * Normalização manual de um alerta de quebra da trilha, depois da apuração ({@code
 * OPERATIONS_ALERT_ACK}). Os demais tipos normalizam sozinhos: reconhecê-los, ou reconhecer um já
 * normalizado, dá {@code ALERT_NOT_ACKNOWLEDGEABLE}. Alerta inexistente dá {@code
 * RESOURCE_NOT_FOUND}.
 */
public class AcknowledgeOperationalAlertUseCase {

    private final ActorAuthorizationService authorization;
    private final OperationalAlertRepository alerts;
    private final OperationalAlertService alertService;
    private final AuthorshipService authorship;
    private final TransactionPort transaction;

    public AcknowledgeOperationalAlertUseCase(
            ActorAuthorizationService authorization,
            OperationalAlertRepository alerts,
            OperationalAlertService alertService,
            AuthorshipService authorship,
            TransactionPort transaction) {
        this.authorization = Objects.requireNonNull(authorization);
        this.alerts = Objects.requireNonNull(alerts);
        this.alertService = Objects.requireNonNull(alertService);
        this.authorship = Objects.requireNonNull(authorship);
        this.transaction = Objects.requireNonNull(transaction);
    }

    public OperationalAlert run(Actor actor, UUID alertId, String correlationId) {
        authorization.require(actor, Operation.OPERATIONS_ALERT_ACK);
        OperationalAlert alert =
                alerts.findById(alertId)
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        if (alert.type() != AlertType.AUDIT_CHAIN_BROKEN || !alert.active()) {
            authorship.recordRead(
                    actor,
                    Operation.OPERATIONS_ALERT_ACK,
                    AuthorshipTargetType.ALERT,
                    alertId,
                    null,
                    AuthorshipResult.FAILURE,
                    ErrorCode.ALERT_NOT_ACKNOWLEDGEABLE.name(),
                    correlationId);
            throw new DomainException(ErrorCode.ALERT_NOT_ACKNOWLEDGEABLE);
        }
        return transaction.run(
                () -> {
                    alertService.resolve(alert.type(), alert.component());
                    authorship.recordRead(
                            actor,
                            Operation.OPERATIONS_ALERT_ACK,
                            AuthorshipTargetType.ALERT,
                            alertId,
                            null,
                            AuthorshipResult.SUCCESS,
                            null,
                            correlationId);
                    return alerts.findById(alertId).orElseThrow();
                });
    }
}
