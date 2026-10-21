package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.util.UUID;

/**
 * Cancela o cadastro em andamento: o rascunho é apagado (CPF cifrado inclusive) e nada é gravado.
 * Esqueleto da Fundação; a regra é do fluxo W-D.
 */
public class CancelStudentRegistrationUseCase {

    public record Command(UUID linkId) {}

    public CancelStudentRegistrationUseCase(
            ActorAuthorizationService authorization, StudentRegistrationDraftRepository drafts) {}

    public void run(Actor actor, Command command) {
        throw new UnsupportedOperationException("CancelStudentRegistrationUseCase: fluxo W-D");
    }
}
