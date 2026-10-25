package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.util.Objects;
import java.util.UUID;

/**
 * Associa um professor ativo a um aluno ({@code ASSIGNMENT_ASSIGN}, FR-028); idempotente: repetir
 * não grava nem registra de novo. Professor inativo é {@code TEACHER_INACTIVE}. Reassociar devolve
 * ao professor os relatórios que ficaram congelados (R-23). Serve também à carga inicial, cujo
 * aluno acabou de ser gravado pelo próprio caso de uso dela.
 */
public class AssignTeacherToStudentUseCase {

    public record Command(UUID studentId, UUID teacherId, String correlationId) {}

    private final ActorAuthorizationService authorization;
    private final StudentAccessService access;
    private final TeacherRepository teachers;
    private final AssignmentRepository assignments;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public AssignTeacherToStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            TeacherRepository teachers,
            AssignmentRepository assignments,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.access = Objects.requireNonNull(access);
        this.teachers = Objects.requireNonNull(teachers);
        this.assignments = Objects.requireNonNull(assignments);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    public void run(Actor actor, Command command) {
        authorization.require(actor, Operation.ASSIGNMENT_ASSIGN);
        if (!actor.isSystem()) {
            // o ator de sistema (carga inicial) não tem escopo: o aluno acabou de ser gravado
            access.findVisible(actor, command.studentId());
        }
        Teacher teacher =
                teachers.searchById(command.teacherId())
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!teacher.active()) {
            throw new DomainException(ErrorCode.TEACHER_INACTIVE);
        }
        if (assignments.exists(teacher.id(), command.studentId())) {
            return;
        }
        assignments.save(TeacherStudent.create(teacher.id(), command.studentId(), clock.now()));
        authorship.record(
                actor,
                Operation.ASSIGNMENT_ASSIGN,
                AuthorshipTargetType.ASSIGNMENT,
                RegistrySupport.assignmentTarget(teacher.id(), command.studentId()),
                command.correlationId());
    }
}
