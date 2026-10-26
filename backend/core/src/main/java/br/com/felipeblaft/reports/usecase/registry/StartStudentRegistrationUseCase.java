package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import br.com.felipeblaft.reports.service.TelegramRegistrationAccessService;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Inicia o cadastro em andamento do Telegram por tipo ({@code CREATE}, {@code UPDATE}, {@code
 * INACTIVATE} ou {@code REACTIVATE}). Nos três últimos confere o escopo do professor (aluno alheio
 * ≡ inexistente, nada é criado). Há um cadastro por vínculo: o novo substitui o anterior. Nada vira
 * aluno antes da confirmação. Em {@code INACTIVATE} e {@code REACTIVATE} não há campo a coletar,
 * então o token do botão Confirmar já nasce aqui.
 */
public class StartStudentRegistrationUseCase {

    /** {@code studentId} é nulo em {@code CREATE} e obrigatório nos demais tipos. */
    public record Command(UUID linkId, StudentRegistrationDraft.Kind kind, UUID studentId) {}

    /**
     * @param confirmationToken só em {@code INACTIVATE} e {@code REACTIVATE} (que não têm campos a
     *     coletar): o token do botão Confirmar; nulo nos demais tipos
     */
    public record Result(
            StudentRegistrationDraft.Kind kind, Instant expiresAt, String confirmationToken) {

        public Result(StudentRegistrationDraft.Kind kind, Instant expiresAt) {
            this(kind, expiresAt, null);
        }

        @Override
        public String toString() {
            return "StartStudentRegistrationUseCase.Result[kind=" + kind + "]";
        }
    }

    private final ActorAuthorizationService authorization;
    private final StudentAccessService access;
    private final TelegramLinkRepository links;
    private final StudentRegistrationDraftRepository drafts;
    private final ClockPort clock;
    private final Duration draftValidity;

    public StartStudentRegistrationUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            TelegramLinkRepository links,
            StudentRegistrationDraftRepository drafts,
            ClockPort clock,
            Duration draftValidity) {
        this.authorization = Objects.requireNonNull(authorization);
        this.access = Objects.requireNonNull(access);
        this.links = Objects.requireNonNull(links);
        this.drafts = Objects.requireNonNull(drafts);
        this.clock = Objects.requireNonNull(clock);
        this.draftValidity = Objects.requireNonNull(draftValidity);
    }

    public Result run(Actor actor, Command command) {
        StudentRegistrationDraft.Kind kind = command.kind();
        TelegramRegistrationAccessService.require(authorization, actor, kind);
        requireOwnLink(actor, command.linkId());

        UUID studentId = null;
        if (kind.targetsExistingStudent()) {
            if (command.studentId() == null) {
                throw new DomainException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            // aluno alheio, sem professor ou inexistente: o mesmo erro, e nenhum rascunho nasce
            studentId = access.findVisible(actor, command.studentId()).id();
        }

        Instant now = clock.now();
        StudentRegistrationDraft draft =
                StudentRegistrationDraft.start(
                        command.linkId(), kind, studentId, now, draftValidity);
        if (kind.confirmationOnly()) {
            draft =
                    draft.withConfirmationToken(
                            StudentRegistrationDraft.newConfirmationToken(), now, draftValidity);
        }
        drafts.save(draft);
        return new Result(kind, draft.expiresAt(), draft.confirmationToken());
    }

    /** O vínculo informado é o do próprio professor. */
    private void requireOwnLink(Actor actor, UUID linkId) {
        boolean own =
                links.searchByTeacherId(actor.id())
                        .map(TelegramLink::id)
                        .filter(linkId::equals)
                        .isPresent();
        if (!own) {
            throw new DomainException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
