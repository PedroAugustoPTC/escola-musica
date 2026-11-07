package br.com.felipeblaft.reports.usecase.operations;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.operations.AlertStatus;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import br.com.felipeblaft.reports.port.OperationalAlertRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.util.Objects;

/**
 * Leitura dos alertas ({@code OPERATIONS_ALERT_READ}, FR-056). A lista é um evento da trilha,
 * gravado na mesma transação da leitura; o resumo do indicador do cabeçalho não grava evento, para
 * a consulta periódica não inundar a trilha.
 */
public class ListOperationalAlertsUseCase {

    private final ActorAuthorizationService authorization;
    private final OperationalAlertRepository alerts;
    private final AuthorshipService authorship;
    private final TransactionPort transaction;

    public ListOperationalAlertsUseCase(
            ActorAuthorizationService authorization,
            OperationalAlertRepository alerts,
            AuthorshipService authorship,
            TransactionPort transaction) {
        this.authorization = Objects.requireNonNull(authorization);
        this.alerts = Objects.requireNonNull(alerts);
        this.authorship = Objects.requireNonNull(authorship);
        this.transaction = Objects.requireNonNull(transaction);
    }

    public PageResult<OperationalAlert> run(
            Actor actor, AlertStatus status, PageRequest page, String correlationId) {
        authorization.require(actor, Operation.OPERATIONS_ALERT_READ);
        return transaction.run(
                () -> {
                    PageResult<OperationalAlert> found = alerts.list(status, page);
                    authorship.recordRead(
                            actor,
                            Operation.OPERATIONS_ALERT_READ,
                            AuthorshipTargetType.ALERT,
                            null,
                            null,
                            AuthorshipResult.SUCCESS,
                            null,
                            correlationId);
                    return found;
                });
    }

    /** Quantos alertas estão ativos; sem evento na trilha. */
    public int activeCount(Actor actor) {
        authorization.require(actor, Operation.OPERATIONS_ALERT_READ);
        return alerts.countActive();
    }
}
