package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.util.UUID;

/**
 * Inativa um aluno ({@code STUDENT_INACTIVATE}). O professor só o faz quando é o único professor
 * ativo associado ({@code SHARED_STUDENT_ACTION_DENIED}, FR-026). Não é bloqueada por relatório em
 * aberto e não altera relatório (SC-013). Esqueleto da Fundação; a regra é do fluxo W-C2.
 */
public class InactivateStudentUseCase {

    public record Command(UUID studentId, String correlationId) {}

    public InactivateStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            StudentRepository students,
            AssignmentRepository assignments,
            AuthorshipService authorship,
            ClockPort clock) {}

    public StudentView run(Actor actor, Command command) {
        throw new UnsupportedOperationException("InactivateStudentUseCase: fluxo W-C2");
    }
}
