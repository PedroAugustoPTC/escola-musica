package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.util.UUID;

/**
 * Retira a associação ({@code ASSIGNMENT_REMOVE}): o acesso do professor ao aluno corta na hora; os
 * relatórios em aberto ficam congelados com o professor, sem alteração (R-23). Esqueleto da
 * Fundação; a regra é do fluxo W-C2.
 */
public class RemoveTeacherFromStudentUseCase {

    public record Command(UUID studentId, UUID teacherId, String correlationId) {}

    public RemoveTeacherFromStudentUseCase(
            ActorAuthorizationService authorization,
            AssignmentRepository assignments,
            AuthorshipService authorship,
            ClockPort clock) {}

    public void run(Actor actor, Command command) {
        throw new UnsupportedOperationException("RemoveTeacherFromStudentUseCase: fluxo W-C2");
    }
}
