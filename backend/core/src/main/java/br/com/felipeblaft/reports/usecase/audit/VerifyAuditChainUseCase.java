package br.com.felipeblaft.reports.usecase.audit;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipReadDetail;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.audit.AuditAnchor;
import br.com.felipeblaft.reports.domain.audit.AuditChain;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.port.AuditTrailQueryPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.OperationalAlertService;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Verificação da integridade da cadeia da trilha ({@code AUDIT_TRAIL_VERIFY}, FR-044): percorre os
 * eventos em lotes, a partir da âncora do último expurgo (ou do início), e confere o encadeamento e
 * o hash de cada linha. Cadeia íntegra grava o evento com a quantidade verificada; cadeia quebrada
 * abre o alerta {@code AUDIT_CHAIN_BROKEN} e grava o evento de falha em transação independente.
 * Roda pelo administrador (sob demanda) e pela retenção (ao fim de cada execução).
 */
public class VerifyAuditChainUseCase {

    public static final String BROKEN_CODE = "AUDIT_CHAIN_BROKEN";
    public static final int DEFAULT_BATCH_SIZE = 1000;

    /** Situação da cadeia. */
    public enum Status {
        INTACT,
        BROKEN
    }

    /** Resultado da verificação; {@code firstBrokenSequence} só existe quando a cadeia quebrou. */
    public record Result(Status status, long checkedEvents, Long firstBrokenSequence) {}

    private final ActorAuthorizationService authorization;
    private final AuditTrailQueryPort trail;
    private final AuthorshipService authorship;
    private final OperationalAlertService alerts;
    private final int batchSize;

    public VerifyAuditChainUseCase(
            ActorAuthorizationService authorization,
            AuditTrailQueryPort trail,
            AuthorshipService authorship,
            OperationalAlertService alerts,
            int batchSize) {
        this.authorization = Objects.requireNonNull(authorization);
        this.trail = Objects.requireNonNull(trail);
        this.authorship = Objects.requireNonNull(authorship);
        this.alerts = Objects.requireNonNull(alerts);
        if (batchSize < 1) {
            throw new IllegalArgumentException("lote de verificação inválido");
        }
        this.batchSize = batchSize;
    }

    public Result run(Actor actor, String correlationId) {
        authorization.require(actor, Operation.AUDIT_TRAIL_VERIFY);
        AuditAnchor anchor = trail.latestAnchor().orElse(null);
        String expectedPrevious =
                anchor == null ? AuditChain.GENESIS_HASH : anchor.lastRecordHash();
        long after = anchor == null ? 0 : anchor.purgedUntilSequence();
        long checked = 0;
        while (true) {
            List<AuditChain.Link> batch = trail.chainFrom(after, batchSize);
            if (batch.isEmpty()) {
                break;
            }
            OptionalLong broken = AuditChain.firstBroken(batch, expectedPrevious);
            if (broken.isPresent()) {
                return broken(actor, correlationId, checked, broken.getAsLong());
            }
            AuditChain.Link last = batch.getLast();
            expectedPrevious = last.recordHash();
            after = last.fields().sequence();
            checked += batch.size();
        }
        authorship.recordRead(
                actor,
                Operation.AUDIT_TRAIL_VERIFY,
                AuthorshipTargetType.AUDIT_TRAIL,
                null,
                AuthorshipReadDetail.ofCount(Math.toIntExact(checked)),
                AuthorshipResult.SUCCESS,
                null,
                correlationId);
        return new Result(Status.INTACT, checked, null);
    }

    private Result broken(Actor actor, String correlationId, long checked, long sequence) {
        authorship.recordRead(
                actor,
                Operation.AUDIT_TRAIL_VERIFY,
                AuthorshipTargetType.AUDIT_TRAIL,
                null,
                AuthorshipReadDetail.ofCount(Math.toIntExact(checked)),
                AuthorshipResult.FAILURE,
                BROKEN_CODE,
                correlationId);
        // depois do registro independente: o alerta grava na transação externa e, se tomasse a trava
        // da cadeia antes, o registro independente (transação própria) esperaria por ela para sempre
        alerts.report(AlertType.AUDIT_CHAIN_BROKEN, "audit-trail", "CHAIN_BROKEN");
        return new Result(Status.BROKEN, checked, sequence);
    }
}
