package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.util.Objects;
import java.util.UUID;

/** Abre um professor ({@code TEACHER_GET}); inexistente é {@code RESOURCE_NOT_FOUND}. */
public class GetTeacherUseCase {

    public record Command(UUID teacherId) {}

    private final ActorAuthorizationService authorization;
    private final TeacherRepository teachers;
    private final TelegramLinkRepository links;

    public GetTeacherUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            TelegramLinkRepository links) {
        this.authorization = Objects.requireNonNull(authorization);
        this.teachers = Objects.requireNonNull(teachers);
        this.links = Objects.requireNonNull(links);
    }

    public TeacherView run(Actor actor, Command command) {
        authorization.require(actor, Operation.TEACHER_GET);
        return teachers.searchById(command.teacherId())
                .map(
                        teacher ->
                                TeacherView.of(
                                        teacher, links.searchByTeacherId(teacher.id()).isPresent()))
                .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
