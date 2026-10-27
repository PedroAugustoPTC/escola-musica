package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.util.Objects;
import java.util.UUID;

/**
 * Retira a associação ({@code ASSIGNMENT_REMOVE}, FR-029). Nunca é bloqueada por relatório em
 * aberto e não apaga nem altera nenhum relatório: o acesso do professor ao aluno corta na hora (o
 * escopo é a associação) e os relatórios em aberto ficam congelados com ele, voltando se o aluno
 * for associado de novo (R-23). O aluno pode ficar sem professor; só o administrador o enxerga.
 * Associação inexistente é {@code RESOURCE_NOT_FOUND}.
 */
public class RemoveTeacherFromStudentUseCase {

    public record Command(UUID studentId, UUID teacherId, String correlationId) {}

    private final ActorAuthorizationService authorization;
    private final AssignmentRepository assignments;
    private final AuthorshipService authorship;

    public RemoveTeacherFromStudentUseCase(
            ActorAuthorizationService authorization,
            AssignmentRepository assignments,
            AuthorshipService authorship) {
        this.authorization = Objects.requireNonNull(authorization);
        this.assignments = Objects.requireNonNull(assignments);
        this.authorship = Objects.requireNonNull(authorship);
    }

    public void run(Actor actor, Command command) {
        authorization.require(actor, Operation.ASSIGNMENT_REMOVE);
        if (!assignments.delete(command.teacherId(), command.studentId())) {
            throw new DomainException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        authorship.record(
                actor,
                Operation.ASSIGNMENT_REMOVE,
                AuthorshipTargetType.ASSIGNMENT,
                RegistrySupport.assignmentTarget(command.teacherId(), command.studentId()),
                command.correlationId());
    }
}
