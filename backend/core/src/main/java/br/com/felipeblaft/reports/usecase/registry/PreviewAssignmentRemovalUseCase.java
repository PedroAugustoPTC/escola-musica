package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.util.UUID;

/**
 * Prévia da retirada de associação ({@code ASSIGNMENT_PREVIEW_REMOVAL}): quantos relatórios em
 * aberto do professor com o aluno ficariam congelados. Esqueleto da Fundação; a regra é do fluxo
 * W-C2.
 */
public class PreviewAssignmentRemovalUseCase {

    public record Command(UUID studentId, UUID teacherId) {}

    public record Result(int frozenReports) {}

    public PreviewAssignmentRemovalUseCase(
            ActorAuthorizationService authorization,
            AssignmentRepository assignments,
            OpenReportsQueryPort openReports) {}

    public Result run(Actor actor, Command command) {
        throw new UnsupportedOperationException("PreviewAssignmentRemovalUseCase: fluxo W-C2");
    }
}
