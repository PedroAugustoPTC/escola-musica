package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.util.UUID;

/**
 * Associa um professor ativo a um aluno ({@code ASSIGNMENT_ASSIGN}); idempotente. Serve também à
 * carga inicial. Esqueleto da Fundação; a regra é do fluxo W-C2.
 */
public class AssignTeacherToStudentUseCase {

    public record Command(UUID studentId, UUID teacherId, String correlationId) {}

    public AssignTeacherToStudentUseCase(
            ActorAuthorizationService authorization,
            StudentRepository students,
            TeacherRepository teachers,
            AssignmentRepository assignments,
            AuthorshipService authorship,
            ClockPort clock) {}

    public void run(Actor actor, Command command) {
        throw new UnsupportedOperationException("AssignTeacherToStudentUseCase: fluxo W-C2");
    }
}
