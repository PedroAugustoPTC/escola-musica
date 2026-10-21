package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import java.util.UUID;

/**
 * Reemite o código de acesso do professor ({@code TEACHER_REISSUE_ACCESS_CODE}), invalidando o
 * anterior e as sessões Web; opcionalmente encerra o vínculo do Telegram. Substitui o {@code
 * EmitAccessCodeUseCase} da carga inicial, que continua até o fluxo W-K migrar o comando {@code
 * issue-code}. Esqueleto da Fundação; a regra é do fluxo W-B.
 */
public class ReissueAccessCodeUseCase {

    public record Command(UUID teacherId, boolean endTelegramLink, String correlationId) {}

    public ReissueAccessCodeUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            AccessCodeIssuanceService issuance,
            SessionRevocationService revocation,
            TelegramLinkTerminationService linkTermination,
            AuthorshipService authorship,
            ClockPort clock) {}

    public TeacherWithCode run(Actor actor, Command command) {
        throw new UnsupportedOperationException("ReissueAccessCodeUseCase: fluxo W-B");
    }
}
