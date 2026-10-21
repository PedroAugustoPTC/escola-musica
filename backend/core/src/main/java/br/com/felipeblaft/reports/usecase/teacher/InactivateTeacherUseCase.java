package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import java.util.UUID;

/**
 * Inativa o professor ({@code TEACHER_INACTIVATE}): revoga as sessões Web, encerra vínculo,
 * rascunho e ações pendentes e invalida o código. Não é bloqueada por relatório em aberto e não
 * cria, altera, aprova nem cancela relatório (SC-013). Esqueleto da Fundação; a regra é do fluxo
 * W-B.
 */
public class InactivateTeacherUseCase {

    public record Command(UUID teacherId, String correlationId) {}

    public InactivateTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            SessionRevocationService revocation,
            TelegramLinkTerminationService linkTermination,
            AuthorshipService authorship,
            ClockPort clock) {}

    public TeacherView run(Actor actor, Command command) {
        throw new UnsupportedOperationException("InactivateTeacherUseCase: fluxo W-B");
    }
}
