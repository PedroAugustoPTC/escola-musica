package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.TelegramLinkTerminationService;
import java.util.Objects;
import java.util.UUID;

/**
 * Desvincula o Telegram do professor ({@code TEACHER_UNLINK_TELEGRAM}) pelo mesmo caminho de todo
 * fim de vínculo: vínculo, cadastro em andamento e ações pendentes saem juntos, e o professor
 * precisa de um código válido para vincular de novo. Sem vínculo, não há o que fazer, mas o pedido
 * é aceito.
 */
public class UnlinkTeacherTelegramUseCase {

    public record Command(UUID teacherId, String correlationId) {}

    private final ActorAuthorizationService authorization;
    private final TeacherRepository teachers;
    private final TelegramLinkTerminationService linkTermination;
    private final AuthorshipService authorship;

    public UnlinkTeacherTelegramUseCase(
            ActorAuthorizationService authorization,
            TeacherRepository teachers,
            TelegramLinkTerminationService linkTermination,
            AuthorshipService authorship) {
        this.authorization = Objects.requireNonNull(authorization);
        this.teachers = Objects.requireNonNull(teachers);
        this.linkTermination = Objects.requireNonNull(linkTermination);
        this.authorship = Objects.requireNonNull(authorship);
    }

    public void run(Actor actor, Command command) {
        authorization.require(actor, Operation.TEACHER_UNLINK_TELEGRAM);
        teachers.searchById(command.teacherId())
                .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        linkTermination.terminateOfTeacher(command.teacherId());
        authorship.record(
                actor,
                Operation.TEACHER_UNLINK_TELEGRAM,
                AuthorshipTargetType.TEACHER,
                command.teacherId(),
                command.correlationId());
    }
}
