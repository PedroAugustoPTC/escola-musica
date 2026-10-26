package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import java.util.Objects;
import java.util.UUID;

/**
 * Inativa o professor ({@code TEACHER_INACTIVATE}): invalida o código, revoga as sessões Web e
 * encerra vínculo, rascunho e ações pendentes. Não é bloqueada por relatório em aberto e não cria,
 * altera, aprova nem cancela relatório (SC-013): este caso de uso nem recebe porta de relatório.
 * Repetir a inativação é inofensivo: os efeitos de corte são reaplicados e a autoria é gravada só
 * quando o estado muda.
 */
public class InactivateTeacherUseCase {

    public record Command(UUID teacherId, String correlationId) {}

    private final ActorAuthorizationService authorization;
    private final TeacherRepository teachers;
    private final SessionRevocationService revocation;
    private final TelegramLinkTerminationService linkTermination;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public InactivateTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            SessionRevocationService revocation,
            TelegramLinkTerminationService linkTermination,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.teachers = Objects.requireNonNull(teachers);
        this.revocation = Objects.requireNonNull(revocation);
        this.linkTermination = Objects.requireNonNull(linkTermination);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    public TeacherView run(Actor actor, Command command) {
        authorization.require(actor, Operation.TEACHER_INACTIVATE);
        Teacher teacher =
                teachers.searchById(command.teacherId())
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean changed = teacher.inactivate(clock.now());
        if (changed) {
            teachers.save(teacher);
        }
        revocation.revokeTeacher(teacher.id(), SessionRevocationReason.ACCOUNT_INACTIVATED);
        linkTermination.terminateOfTeacher(teacher.id());
        if (changed) {
            authorship.record(
                    actor,
                    Operation.TEACHER_INACTIVATE,
                    AuthorshipTargetType.TEACHER,
                    teacher.id(),
                    command.correlationId());
        }
        return TeacherView.of(teacher, false);
    }
}
