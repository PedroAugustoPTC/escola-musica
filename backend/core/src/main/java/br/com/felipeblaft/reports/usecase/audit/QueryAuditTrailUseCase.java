package br.com.felipeblaft.reports.usecase.audit;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipReadDetail;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.audit.AuditTrailEntry;
import br.com.felipeblaft.reports.domain.audit.AuditTrailFilter;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.port.AuditTrailQueryPort;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Consulta da trilha de auditoria ({@code AUDIT_TRAIL_QUERY}, FR-045): só o administrador, com
 * período obrigatório de até 90 dias. A autorização é a primeira instrução. A própria consulta é um
 * evento da trilha, gravado na mesma transação da leitura: se o evento não puder ser gravado, nada
 * é devolvido. A recusa de filtro grava o evento de falha em transação independente.
 */
public class QueryAuditTrailUseCase {

    /** Pedido da consulta; todos os filtros, menos o período, são opcionais. */
    public record Command(
            LocalDate from,
            LocalDate to,
            UUID actorId,
            Role actorRole,
            ActionOrigin origin,
            String operation,
            AuthorshipResult result,
            UUID targetId,
            PageRequest page,
            String correlationId) {

        public Command {
            Objects.requireNonNull(page);
            Objects.requireNonNull(correlationId);
        }
    }

    private final ActorAuthorizationService authorization;
    private final AuditTrailQueryPort trail;
    private final AuthorshipService authorship;
    private final TransactionPort transaction;

    public QueryAuditTrailUseCase(
            ActorAuthorizationService authorization,
            AuditTrailQueryPort trail,
            AuthorshipService authorship,
            TransactionPort transaction) {
        this.authorization = Objects.requireNonNull(authorization);
        this.trail = Objects.requireNonNull(trail);
        this.authorship = Objects.requireNonNull(authorship);
        this.transaction = Objects.requireNonNull(transaction);
    }

    public PageResult<AuditTrailEntry> run(Actor actor, Command command) {
        authorization.require(actor, Operation.AUDIT_TRAIL_QUERY);
        AuditTrailFilter filter = validated(actor, command);
        return transaction.run(
                () -> {
                    PageResult<AuditTrailEntry> page = trail.query(filter);
                    authorship.recordRead(
                            actor,
                            Operation.AUDIT_TRAIL_QUERY,
                            AuthorshipTargetType.AUDIT_TRAIL,
                            null,
                            detail(command),
                            AuthorshipResult.SUCCESS,
                            null,
                            command.correlationId());
                    return page;
                });
    }

    private AuditTrailFilter validated(Actor actor, Command command) {
        try {
            return new AuditTrailFilter(
                    command.from(),
                    command.to(),
                    command.actorId(),
                    command.actorRole(),
                    command.origin(),
                    command.operation(),
                    command.result(),
                    command.targetId(),
                    command.page());
        } catch (DomainException refusal) {
            authorship.recordRead(
                    actor,
                    Operation.AUDIT_TRAIL_QUERY,
                    AuthorshipTargetType.AUDIT_TRAIL,
                    null,
                    null,
                    AuthorshipResult.FAILURE,
                    ErrorCode.VALIDATION_FAILED.name(),
                    command.correlationId());
            throw refusal;
        }
    }

    private static AuthorshipReadDetail detail(Command command) {
        return AuthorshipReadDetail.ofTrailQuery(
                command.from(),
                command.to(),
                command.operation(),
                command.actorId(),
                command.targetId(),
                command.page().page());
    }
}
