package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherIdentity;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Edita nome e e-mail do professor ({@code TEACHER_UPDATE}); o e-mail novo segue único, sem
 * diferenciar caixa nem espaços, inclusive entre inativos. A duplicidade identifica o dono do
 * e-mail pelo nome, para o administrador.
 */
public class UpdateTeacherUseCase {

    public record Command(UUID teacherId, String name, String email, String correlationId) {

        @Override
        public String toString() {
            return "UpdateTeacherUseCase.Command[teacherId=" + teacherId + "]";
        }
    }

    private final ActorAuthorizationService authorization;
    private final TeacherRepository teachers;
    private final TelegramLinkRepository links;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public UpdateTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            TelegramLinkRepository links,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.teachers = Objects.requireNonNull(teachers);
        this.links = Objects.requireNonNull(links);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    public TeacherView run(Actor actor, Command command) {
        authorization.require(actor, Operation.TEACHER_UPDATE);
        Teacher teacher =
                teachers.searchById(command.teacherId())
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        TeacherIdentity identity = TeacherIdentity.of(command.name(), command.email());
        teachers.searchByEmail(identity.email())
                .filter(other -> !other.id().equals(teacher.id()))
                .ifPresent(
                        other -> {
                            throw new DomainException(
                                    ErrorCode.DUPLICATE_EMAIL, Map.of("name", other.name()));
                        });
        Instant now = clock.now();
        teacher.rename(identity.name(), now);
        teacher.changeEmail(identity.email(), now);
        teachers.save(teacher);
        authorship.record(
                actor,
                Operation.TEACHER_UPDATE,
                AuthorshipTargetType.TEACHER,
                teacher.id(),
                command.correlationId());
        return TeacherView.of(teacher, links.searchByTeacherId(teacher.id()).isPresent());
    }
}
