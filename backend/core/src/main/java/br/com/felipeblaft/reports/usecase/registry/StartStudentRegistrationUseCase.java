package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Inicia o cadastro em andamento do Telegram por tipo ({@code CREATE}, {@code UPDATE}, {@code
 * INACTIVATE} ou {@code REACTIVATE}). Nos três últimos confere o escopo do professor (aluno alheio
 * ≡ inexistente, nada é criado). Nada vira aluno antes da confirmação. Esqueleto da Fundação; a
 * regra é do fluxo W-D.
 */
public class StartStudentRegistrationUseCase {

    /** {@code studentId} é nulo em {@code CREATE} e obrigatório nos demais tipos. */
    public record Command(UUID linkId, StudentRegistrationDraft.Kind kind, UUID studentId) {}

    public record Result(StudentRegistrationDraft.Kind kind, Instant expiresAt) {}

    public StartStudentRegistrationUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            TelegramLinkRepository links,
            StudentRegistrationDraftRepository drafts,
            ClockPort clock,
            Duration draftValidity) {}

    public Result run(Actor actor, Command command) {
        throw new UnsupportedOperationException("StartStudentRegistrationUseCase: fluxo W-D");
    }
}
