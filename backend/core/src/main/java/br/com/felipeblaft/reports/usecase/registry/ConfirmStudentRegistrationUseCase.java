package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.CpfDecryptionPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.TelegramRegistrationAccessService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Confirma o cadastro em andamento: numa única transação confere o dono (o vínculo de quem chama),
 * o token de uso único e o prazo, executa o caso de uso da operação ({@code RegisterStudent},
 * {@code UpdateStudent}, {@code InactivateStudent} ou {@code ReactivateStudent}) e consome o
 * rascunho. Segundo toque ou token anterior responde {@code CONFIRMATION_ALREADY_DONE}; prazo
 * vencido, {@code CONFIRMATION_EXPIRED} (e o rascunho é descartado). Se o caso de uso recusar (CPF
 * indisponível, aluno que deixou de ser do professor, aluno compartilhado), nada é gravado e o
 * rascunho permanece, para o professor corrigir ou cancelar. O CPF é decifrado só aqui, para ser
 * entregue ao caso de uso que o grava.
 */
public class ConfirmStudentRegistrationUseCase {

    public record Command(UUID linkId, String confirmationToken, String correlationId) {

        @Override
        public String toString() {
            return "ConfirmStudentRegistrationUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final StudentRegistrationDraftRepository drafts;
    private final RegisterStudentUseCase register;
    private final UpdateStudentUseCase update;
    private final InactivateStudentUseCase inactivate;
    private final ReactivateStudentUseCase reactivate;
    private final CpfDecryptionPort cpfDecryption;
    private final TransactionPort transaction;
    private final ClockPort clock;

    @SuppressWarnings("java:S107")
    public ConfirmStudentRegistrationUseCase(
            ActorAuthorizationService authorization,
            StudentRegistrationDraftRepository drafts,
            RegisterStudentUseCase register,
            UpdateStudentUseCase update,
            InactivateStudentUseCase inactivate,
            ReactivateStudentUseCase reactivate,
            CpfDecryptionPort cpfDecryption,
            TransactionPort transaction,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.drafts = Objects.requireNonNull(drafts);
        this.register = Objects.requireNonNull(register);
        this.update = Objects.requireNonNull(update);
        this.inactivate = Objects.requireNonNull(inactivate);
        this.reactivate = Objects.requireNonNull(reactivate);
        this.cpfDecryption = Objects.requireNonNull(cpfDecryption);
        this.transaction = Objects.requireNonNull(transaction);
        this.clock = Objects.requireNonNull(clock);
    }

    public StudentView run(Actor actor, Command command) {
        TelegramRegistrationAccessService.requireTelegramTeacher(actor);
        Instant now = clock.now();
        UUID linkId = command.linkId();
        // o descarte do vencido fica fora da transação: a exceção desfaria a remoção
        if (drafts.searchByLinkId(linkId).filter(d -> d.expired(now)).isPresent()) {
            drafts.removeByLinkId(linkId);
            throw new DomainException(ErrorCode.CONFIRMATION_EXPIRED);
        }
        return transaction.run(() -> confirm(actor, command, now));
    }

    private StudentView confirm(Actor actor, Command command, Instant now) {
        UUID linkId = command.linkId();
        StudentRegistrationDraft draft =
                drafts.searchByLinkId(linkId)
                        .orElseThrow(
                                () -> new DomainException(ErrorCode.CONFIRMATION_ALREADY_DONE));
        if (draft.expired(now)) {
            throw new DomainException(ErrorCode.CONFIRMATION_EXPIRED);
        }
        TelegramRegistrationAccessService.require(authorization, actor, draft.kind());
        if (!draft.matchesToken(command.confirmationToken())) {
            throw new DomainException(ErrorCode.CONFIRMATION_ALREADY_DONE);
        }
        StudentView view = execute(actor, draft, command.correlationId());
        drafts.removeByLinkId(linkId);
        return view;
    }

    private StudentView execute(Actor actor, StudentRegistrationDraft draft, String correlationId) {
        return switch (draft.kind()) {
            case CREATE ->
                    register.run(
                            actor,
                            new RegisterStudentUseCase.Command(
                                    inputOf(draft), List.of(), correlationId, true));
            case UPDATE ->
                    update.run(
                            actor,
                            new UpdateStudentUseCase.Command(
                                    draft.studentId(), inputOf(draft), correlationId, true));
            case INACTIVATE ->
                    inactivate.run(
                            actor,
                            new InactivateStudentUseCase.Command(draft.studentId(), correlationId));
            case REACTIVATE ->
                    reactivate.run(
                            actor,
                            new ReactivateStudentUseCase.Command(draft.studentId(), correlationId));
        };
    }

    /** Só o que foi informado segue; campo ausente mantém o valor atual na edição. */
    private StudentInput inputOf(StudentRegistrationDraft draft) {
        var provided = draft.providedFields();
        GuardianInput guardian =
                provided.contains(Field.GUARDIAN)
                        ? new GuardianInput(
                                draft.guardianName(), draft.guardianPhone(), draft.guardianEmail())
                        : null;
        Secret cpf =
                provided.contains(Field.CPF)
                        ? new Secret(cpfDecryption.decrypt(draft.cpfEncrypted()).digits())
                        : null;
        return new StudentInput(
                provided.contains(Field.NAME) ? draft.name() : null,
                provided.contains(Field.BIRTH_DATE) ? draft.birthDate().toString() : null,
                cpf,
                guardian,
                provided.contains(Field.INSTRUMENT) ? draft.instrument() : null);
    }
}
