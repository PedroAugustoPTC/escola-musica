package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.TelegramRegistrationAccessService;
import java.util.Objects;
import java.util.UUID;

/**
 * Cancela o cadastro em andamento: o rascunho é apagado (CPF cifrado inclusive) e nada é gravado.
 * Sem rascunho, não há o que cancelar e nada acontece.
 */
public class CancelStudentRegistrationUseCase {

    public record Command(UUID linkId) {}

    private final ActorAuthorizationService authorization;
    private final StudentRegistrationDraftRepository drafts;

    public CancelStudentRegistrationUseCase(
            ActorAuthorizationService authorization, StudentRegistrationDraftRepository drafts) {
        this.authorization = Objects.requireNonNull(authorization);
        this.drafts = Objects.requireNonNull(drafts);
    }

    public void run(Actor actor, Command command) {
        TelegramRegistrationAccessService.requireTelegramTeacher(actor);
        drafts.searchByLinkId(command.linkId())
                .ifPresent(
                        draft -> {
                            TelegramRegistrationAccessService.require(
                                    authorization, actor, draft.kind());
                            drafts.removeByLinkId(command.linkId());
                        });
    }
}
