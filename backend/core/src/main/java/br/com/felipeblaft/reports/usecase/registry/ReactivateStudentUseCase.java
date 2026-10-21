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
 * Reativa um aluno ({@code STUDENT_REACTIVATE}), com a mesma regra do único professor ativo.
 * Esqueleto da Fundação; a regra é do fluxo W-C2.
 */
public class ReactivateStudentUseCase {

    public record Command(UUID studentId, String correlationId) {}

    public ReactivateStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            StudentRepository students,
            AssignmentRepository assignments,
            AuthorshipService authorship,
            ClockPort clock) {}

    public StudentView run(Actor actor, Command command) {
        throw new UnsupportedOperationException("ReactivateStudentUseCase: fluxo W-C2");
    }
}
