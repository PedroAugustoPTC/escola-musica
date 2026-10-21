package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.util.UUID;

/**
 * Abre um aluno ({@code STUDENT_GET}). Aluno alheio, sem professor ou inexistente é {@code
 * RESOURCE_NOT_FOUND}, pelo mesmo caminho e com uma só consulta. Esqueleto da Fundação; a regra é
 * do fluxo W-C1.
 */
public class GetStudentUseCase {

    public record Command(UUID studentId, String correlationId) {}

    public GetStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            ClockPort clock) {}

    public StudentView run(Actor actor, Command command) {
        throw new UnsupportedOperationException("GetStudentUseCase: fluxo W-C1");
    }
}
