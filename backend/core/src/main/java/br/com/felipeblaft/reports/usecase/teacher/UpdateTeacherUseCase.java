package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.util.UUID;

/**
 * Edita nome e e-mail do professor ({@code TEACHER_UPDATE}); o e-mail novo segue único. Esqueleto
 * da Fundação; a regra é do fluxo W-B.
 */
public class UpdateTeacherUseCase {

    public record Command(UUID teacherId, String name, String email, String correlationId) {

        @Override
        public String toString() {
            return "UpdateTeacherUseCase.Command[teacherId=" + teacherId + "]";
        }
    }

    public UpdateTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            TelegramLinkRepository links,
            AuthorshipService authorship,
            ClockPort clock) {}

    public TeacherView run(Actor actor, Command command) {
        throw new UnsupportedOperationException("UpdateTeacherUseCase: fluxo W-B");
    }
}
