package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.util.UUID;

/**
 * Abre um professor ({@code TEACHER_GET}); inexistente é {@code RESOURCE_NOT_FOUND}. Esqueleto da
 * Fundação; a regra é do fluxo W-B.
 */
public class GetTeacherUseCase {

    public record Command(UUID teacherId) {}

    public GetTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            TelegramLinkRepository links) {}

    public TeacherView run(Actor actor, Command command) {
        throw new UnsupportedOperationException("GetTeacherUseCase: fluxo W-B");
    }
}
