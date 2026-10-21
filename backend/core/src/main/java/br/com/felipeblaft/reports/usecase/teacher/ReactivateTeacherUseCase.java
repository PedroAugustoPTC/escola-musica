package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.util.UUID;

/**
 * Reativa o professor ({@code TEACHER_REACTIVATE}) com um código novo exibido uma única vez; as
 * associações são preservadas e as sessões e o vínculo não voltam. Esqueleto da Fundação; a regra é
 * do fluxo W-B.
 */
public class ReactivateTeacherUseCase {

    public record Command(UUID teacherId, String correlationId) {}

    public ReactivateTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            AccessCodeIssuanceService issuance,
            AuthorshipService authorship,
            ClockPort clock) {}

    public TeacherWithCode run(Actor actor, Command command) {
        throw new UnsupportedOperationException("ReactivateTeacherUseCase: fluxo W-B");
    }
}
