package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.util.Objects;
import java.util.UUID;

/**
 * Inativa um aluno ({@code STUDENT_INACTIVATE}). O administrador inativa qualquer aluno; o
 * professor só os próprios e, havendo mais de um professor ativo associado, a ação é negada com
 * {@code SHARED_STUDENT_ACTION_DENIED} (FR-026; professor inativo não conta). Nunca é bloqueada por
 * relatório em aberto e não altera relatório nem associação (FR-022, SC-013): o aluno só sai das
 * listas padrão e de novos relatórios.
 */
public class InactivateStudentUseCase {

    public record Command(UUID studentId, String correlationId) {}

    private final ActorAuthorizationService authorization;
    private final StudentAccessService access;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final AssignmentRepository assignments;
    private final AuthorshipService authorship;
    private final ClockPort clock;
    private final GetStudentUseCase.Views views;

    public InactivateStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            StudentRepository students,
            TeacherRepository teachers,
            AssignmentRepository assignments,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.access = Objects.requireNonNull(access);
        this.students = Objects.requireNonNull(students);
        this.teachers = Objects.requireNonNull(teachers);
        this.assignments = Objects.requireNonNull(assignments);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
        this.views = new GetStudentUseCase.Views(assignments, teachers, clock);
    }

    public StudentView run(Actor actor, Command command) {
        Permission permission = authorization.require(actor, Operation.STUDENT_INACTIVATE);
        RegistrySupport.requireActiveActor(actor, teachers);
        Student student =
                RegistrySupport.findVisibleOrRecordDenial(
                        access, authorship, actor, command.studentId(), command.correlationId());
        RegistrySupport.requireSoleActiveTeacher(
                permission,
                actor,
                student,
                assignments,
                authorship,
                Operation.STUDENT_INACTIVATE,
                command.correlationId());
        if (student.inactivate(clock.now())) {
            students.save(student);
            authorship.record(
                    actor,
                    Operation.STUDENT_INACTIVATE,
                    AuthorshipTargetType.STUDENT,
                    student.id(),
                    command.correlationId());
        }
        return views.of(student, actor.role() == Role.TEACHER);
    }
}
