package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reemite o código de acesso do professor ({@code TEACHER_REISSUE_ACCESS_CODE}): o anterior morre
 * na hora e as sessões Web são revogadas ({@code CODE_REISSUED}); o vínculo do Telegram, com o
 * cadastro em andamento e as ações pendentes, só termina se o administrador marcar a opção.
 * Professor inativo é recusado com {@code TEACHER_INACTIVE} (reative antes).
 */
public class ReissueAccessCodeUseCase {

    public record Command(UUID teacherId, boolean endTelegramLink, String correlationId) {}

    private final ActorAuthorizationService authorization;
    private final TeacherRepository teachers;
    private final AccessCodeIssuanceService issuance;
    private final SessionRevocationService revocation;
    private final TelegramLinkTerminationService linkTermination;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public ReissueAccessCodeUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            AccessCodeIssuanceService issuance,
            SessionRevocationService revocation,
            TelegramLinkTerminationService linkTermination,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.teachers = Objects.requireNonNull(teachers);
        this.issuance = Objects.requireNonNull(issuance);
        this.revocation = Objects.requireNonNull(revocation);
        this.linkTermination = Objects.requireNonNull(linkTermination);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    public TeacherWithCode run(Actor actor, Command command) {
        authorization.require(actor, Operation.TEACHER_REISSUE_ACCESS_CODE);
        Teacher teacher =
                teachers.searchById(command.teacherId())
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!teacher.active()) {
            throw new DomainException(ErrorCode.TEACHER_INACTIVE);
        }
        Instant now = clock.now();
        AccessCode code = issuance.emit(teacher, now);
        teachers.save(teacher);
        revocation.revokeTeacher(teacher.id(), SessionRevocationReason.CODE_REISSUED);
        boolean linked;
        if (command.endTelegramLink()) {
            linkTermination.terminateOfTeacher(teacher.id());
            linked = false;
        } else {
            linked = linkTermination.hasLink(teacher.id());
        }
        authorship.record(
                actor,
                Operation.TEACHER_REISSUE_ACCESS_CODE,
                AuthorshipTargetType.TEACHER,
                teacher.id(),
                command.correlationId());
        return new TeacherWithCode(
                TeacherView.of(teacher, linked),
                new Secret(code.value()),
                teacher.accessCodeExpiresAt());
    }
}
