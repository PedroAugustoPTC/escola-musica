package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.BirthDate;
import br.com.felipeblaft.reports.domain.registry.BrazilianPhone;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.EmailAddress;
import br.com.felipeblaft.reports.domain.registry.Instrument;
import br.com.felipeblaft.reports.domain.registry.PersonName;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.TelegramRegistrationAccessService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Recebe um campo do cadastro em andamento (texto bruto), valida com as mesmas regras do cadastro
 * da Web e guarda o valor normalizado no rascunho (o CPF só cifrado). O responsável (nome,
 * telefone, e-mail) é coletado em três chamadas seguidas do campo {@code GUARDIAN}; chamá-lo de
 * novo com o responsável completo recomeça pelo nome. Ao fechar o cadastro, gera o resumo (sem CPF
 * nem contato) e o token de confirmação de uso único; qualquer campo mudado depois invalida o
 * token. A resposta recusada não altera nada. A checagem de CPF já cadastrado fica na confirmação
 * ({@code RegisterStudentUseCase}/{@code UpdateStudentUseCase}), sob a mesma transação.
 */
public class CollectStudentRegistrationFieldUseCase {

    /**
     * @param correlationId identificador do update do Telegram (autoria da recusa de CPF); o
     *     construtor sem ele usa um valor neutro, só para quem ainda não o informa
     */
    public record Command(
            UUID linkId, StudentRegistrationDraft.Field field, String text, String correlationId) {

        public Command(UUID linkId, StudentRegistrationDraft.Field field, String text) {
            this(linkId, field, text, "0");
        }

        @Override
        public String toString() {
            return "CollectStudentRegistrationFieldUseCase.Command[field=" + field + "]";
        }
    }

    /** Resumo mostrado ao professor na confirmação: nunca traz CPF, telefone nem e-mail. */
    public record Summary(
            String name, LocalDate birthDate, String instrument, String guardianName) {

        @Override
        public String toString() {
            return "Summary";
        }
    }

    /**
     * @param nextField próximo campo a perguntar; nulo quando o cadastro está completo
     * @param guardianRequired o aluno é menor de 18 anos na data de hoje no fuso da escola
     * @param summary e {@code confirmationToken} só vêm quando o cadastro está pronto para
     *     confirmar
     */
    public record Result(
            StudentRegistrationDraft.Field nextField,
            boolean guardianRequired,
            Summary summary,
            String confirmationToken) {

        @Override
        public String toString() {
            return "CollectStudentRegistrationFieldUseCase.Result[nextField=" + nextField + "]";
        }
    }

    private final ActorAuthorizationService authorization;
    private final StudentRegistrationDraftRepository drafts;
    private final DataProtectionPort protection;
    private final StudentRepository students;
    private final RegisterStudentUseCase.TeacherCpfGuard cpfGuard;
    private final ClockPort clock;
    private final Duration draftValidity;

    @SuppressWarnings("java:S107")
    public CollectStudentRegistrationFieldUseCase(
            ActorAuthorizationService authorization,
            StudentRegistrationDraftRepository drafts,
            StudentRepository students,
            AssignmentRepository assignments,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock,
            Duration draftValidity) {
        this.authorization = Objects.requireNonNull(authorization);
        this.drafts = Objects.requireNonNull(drafts);
        this.students = Objects.requireNonNull(students);
        this.cpfGuard =
                new RegisterStudentUseCase.TeacherCpfGuard(assignments, attempts, authorship);
        this.protection = Objects.requireNonNull(protection);
        this.clock = Objects.requireNonNull(clock);
        this.draftValidity = Objects.requireNonNull(draftValidity);
    }

    public Result run(Actor actor, Command command) {
        TelegramRegistrationAccessService.requireTelegramTeacher(actor);
        Instant now = clock.now();
        UUID linkId = command.linkId();
        StudentRegistrationDraft draft =
                drafts.searchByLinkId(linkId)
                        .orElseThrow(() -> new DomainException(ErrorCode.CONFIRMATION_EXPIRED));
        if (draft.expired(now)) {
            drafts.removeByLinkId(linkId);
            throw new DomainException(ErrorCode.CONFIRMATION_EXPIRED);
        }
        TelegramRegistrationAccessService.require(authorization, actor, draft.kind());
        if (draft.kind().confirmationOnly()) {
            throw new DomainException(ErrorCode.CALLBACK_EXPIRED);
        }

        LocalDate today = clock.todayInSchoolTimezone();
        String text = command.text() == null ? "" : command.text();
        StudentRegistrationDraft updated = apply(actor, draft, command, text, today, now);

        Summary summary = null;
        String token = null;
        if (updated.readyToConfirm(today)) {
            token = StudentRegistrationDraft.newConfirmationToken();
            updated = updated.withConfirmationToken(token, now, draftValidity);
            summary =
                    new Summary(
                            updated.name(),
                            updated.birthDate(),
                            updated.instrument(),
                            updated.guardianName());
        }
        drafts.save(updated);
        return new Result(
                updated.nextField(today), updated.guardianRequired(today), summary, token);
    }

    private StudentRegistrationDraft apply(
            Actor actor,
            StudentRegistrationDraft draft,
            Command command,
            String text,
            LocalDate today,
            Instant now) {
        return switch (command.field()) {
            case NAME ->
                    draft.withName(
                            validate("name", () -> PersonName.of(text).value()),
                            now,
                            draftValidity);
            case BIRTH_DATE -> {
                LocalDate birth = validate("birthDate", () -> BirthDate.parse(text, today).value());
                StudentRegistrationDraft withBirth = draft.withBirthDate(birth, now, draftValidity);
                // adulto não leva responsável: o que foi informado antes (como menor) é descartado
                yield withBirth.guardianRequired(today)
                        ? withBirth
                        : withBirth.withoutGuardian(now, draftValidity);
            }
            case CPF -> {
                Cpf cpf = validate("cpf", () -> Cpf.of(text));
                String hash = protection.hashCpf(cpf);
                checkCpfIsFree(actor, draft, hash, command.correlationId());
                yield draft.withCpf(protection.encryptCpf(cpf), hash, now, draftValidity);
            }
            case INSTRUMENT -> draft.withInstrument(instrument(draft, text), now, draftValidity);
            case GUARDIAN -> guardianPart(draft, text, now);
        };
    }

    /**
     * Duplicidade decidida já no CPF digitado (FR-024), com as mesmas regras do cadastro e da
     * edição: o bloqueio vale antes da consulta, cada consulta conta no teto, e o CPF de aluno de
     * outro professor responde só {@code CPF_UNAVAILABLE}. Na edição, o CPF do próprio aluno é
     * aceito. O rascunho não é alterado na recusa, para o professor corrigir o CPF ou cancelar.
     */
    private void checkCpfIsFree(
            Actor actor, StudentRegistrationDraft draft, String hash, String correlationId) {
        cpfGuard.beforeLookup(actor, correlationId, false);
        students.searchByCpfHash(hash)
                .filter(holder -> !holder.id().equals(draft.studentId()))
                .ifPresent(
                        holder -> {
                            throw cpfGuard.refuse(actor, holder, correlationId);
                        });
    }

    /** Em branco pula a pergunta no cadastro; na edição não há como "limpar" o instrumento. */
    private static String instrument(StudentRegistrationDraft draft, String text) {
        if (text.isBlank() && draft.kind() == StudentRegistrationDraft.Kind.CREATE) {
            return null;
        }
        return validate("instrument", () -> Instrument.of(text).value());
    }

    private StudentRegistrationDraft guardianPart(
            StudentRegistrationDraft draft, String text, Instant now) {
        StudentRegistrationDraft.GuardianPart part = draft.nextGuardianPart();
        if (part == null) {
            part = StudentRegistrationDraft.GuardianPart.NAME;
        }
        return switch (part) {
            case NAME ->
                    draft.withGuardianName(
                            validate("guardian.name", () -> PersonName.of(text).value()),
                            now,
                            draftValidity);
            case PHONE ->
                    draft.withGuardianPhone(
                            validate("guardian.phone", () -> BrazilianPhone.of(text).digits()),
                            now,
                            draftValidity);
            case EMAIL ->
                    draft.withGuardianEmail(
                            validate("guardian.email", () -> EmailAddress.of(text).value()),
                            now,
                            draftValidity);
        };
    }

    /**
     * Converte a recusa do objeto de valor em {@code VALIDATION_FAILED} do campo, igual ao cadastro
     * da Web; o valor digitado nunca volta na falha.
     */
    private static <T> T validate(String field, Supplier<T> parse) {
        try {
            return parse.get();
        } catch (DomainException error) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("fields", List.of(Map.of("field", field, "code", error.code().name()))));
        }
    }
}
