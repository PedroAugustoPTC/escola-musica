package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.util.UUID;

/**
 * Confirma o cadastro em andamento: numa única transação confere o dono (o vínculo de quem chama),
 * o token de uso único e o prazo, executa o caso de uso da operação ({@code RegisterStudent},
 * {@code UpdateStudent}, {@code InactivateStudent} ou {@code ReactivateStudent}) e consome o
 * rascunho. Segundo toque ou token anterior responde {@code CONFIRMATION_ALREADY_DONE}; prazo
 * vencido, {@code CONFIRMATION_EXPIRED}. Esqueleto da Fundação; a regra é do fluxo W-D.
 */
public class ConfirmStudentRegistrationUseCase {

    public record Command(UUID linkId, String confirmationToken, String correlationId) {

        @Override
        public String toString() {
            return "ConfirmStudentRegistrationUseCase.Command";
        }
    }

    public ConfirmStudentRegistrationUseCase(
            ActorAuthorizationService authorization,
            StudentRegistrationDraftRepository drafts,
            RegisterStudentUseCase register,
            UpdateStudentUseCase update,
            InactivateStudentUseCase inactivate,
            ReactivateStudentUseCase reactivate,
            TransactionPort transaction,
            ClockPort clock) {}

    public StudentView run(Actor actor, Command command) {
        throw new UnsupportedOperationException("ConfirmStudentRegistrationUseCase: fluxo W-D");
    }
}
