package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AccessCodeIssuanceService;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reativa o professor ({@code TEACHER_REACTIVATE}) com um código novo exibido uma única vez; as
 * associações são preservadas e as sessões e o vínculo não voltam (foram encerrados ao inativar).
 * Reativar quem já está ativo apenas troca o código, sem tocar em sessões.
 */
public class ReactivateTeacherUseCase {

    public record Command(UUID teacherId, String correlationId) {}

    private final ActorAuthorizationService authorization;
    private final TeacherRepository teachers;
    private final AccessCodeIssuanceService issuance;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public ReactivateTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            AccessCodeIssuanceService issuance,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.teachers = Objects.requireNonNull(teachers);
        this.issuance = Objects.requireNonNull(issuance);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    public TeacherWithCode run(Actor actor, Command command) {
        authorization.require(actor, Operation.TEACHER_REACTIVATE);
        Teacher teacher =
                teachers.searchById(command.teacherId())
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        Instant now = clock.now();
        teacher.reactivate(now);
        AccessCode code = issuance.emit(teacher, now);
        teachers.save(teacher);
        authorship.record(
                actor,
                Operation.TEACHER_REACTIVATE,
                AuthorshipTargetType.TEACHER,
                teacher.id(),
                command.correlationId());
        return new TeacherWithCode(
                TeacherView.of(teacher, false),
                new Secret(code.value()),
                teacher.accessCodeExpiresAt());
    }
}
