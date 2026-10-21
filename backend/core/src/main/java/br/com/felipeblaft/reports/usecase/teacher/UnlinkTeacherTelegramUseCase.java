package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import java.util.UUID;

/**
 * Desvincula o Telegram do professor ({@code TEACHER_UNLINK_TELEGRAM}) pelo mesmo caminho de todo
 * fim de vínculo. Esqueleto da Fundação; a regra é do fluxo W-B.
 */
public class UnlinkTeacherTelegramUseCase {

    public record Command(UUID teacherId, String correlationId) {}

    public UnlinkTeacherTelegramUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            TelegramLinkTerminationService linkTermination,
            AuthorshipService authorship) {}

    public void run(Actor actor, Command command) {
        throw new UnsupportedOperationException("UnlinkTeacherTelegramUseCase: fluxo W-B");
    }
}
