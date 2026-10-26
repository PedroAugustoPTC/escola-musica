package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.util.Objects;
import java.util.UUID;

/**
 * Prévia da retirada de associação ({@code ASSIGNMENT_PREVIEW_REMOVAL}, FR-029): quantos relatórios
 * em aberto do professor com o aluno ficariam congelados. É uma leitura de contagem: o
 * administrador não vê conteúdo, nada é gravado e nada é alterado. Associação inexistente é {@code
 * RESOURCE_NOT_FOUND}.
 */
public class PreviewAssignmentRemovalUseCase {

    public record Command(UUID studentId, UUID teacherId) {}

    public record Result(int frozenReports) {}

    private final ActorAuthorizationService authorization;
    private final AssignmentRepository assignments;
    private final OpenReportsQueryPort openReports;

    public PreviewAssignmentRemovalUseCase(
            ActorAuthorizationService authorization,
            AssignmentRepository assignments,
            OpenReportsQueryPort openReports) {
        this.authorization = Objects.requireNonNull(authorization);
        this.assignments = Objects.requireNonNull(assignments);
        this.openReports = Objects.requireNonNull(openReports);
    }

    public Result run(Actor actor, Command command) {
        authorization.require(actor, Operation.ASSIGNMENT_PREVIEW_REMOVAL);
        if (!assignments.exists(command.teacherId(), command.studentId())) {
            throw new DomainException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return new Result(openReports.countOpenReports(command.teacherId(), command.studentId()));
    }
}
