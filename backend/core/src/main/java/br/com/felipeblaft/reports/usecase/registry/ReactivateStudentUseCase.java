package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
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
 * Reativa um aluno ({@code STUDENT_REACTIVATE}), com a mesma regra do único professor ativo
 * (FR-026). O aluno volta às listas padrão e a novos relatórios; nada foi apagado e nada muda além
 * da situação.
 */
public class ReactivateStudentUseCase {

    public record Command(UUID studentId, String correlationId) {}

    private final ActorAuthorizationService authorization;
    private final StudentAccessService access;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final AssignmentRepository assignments;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public ReactivateStudentUseCase(
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
    }

    public StudentView run(Actor actor, Command command) {
        Permission permission = authorization.require(actor, Operation.STUDENT_REACTIVATE);
        RegistrySupport.requireActiveActor(permission, actor, teachers);
        Student student =
                RegistrySupport.visible(
                        access, authorship, actor, command.studentId(), command.correlationId());
        RegistrySupport.requireSoleActiveTeacher(
                permission,
                actor,
                student,
                assignments,
                authorship,
                Operation.STUDENT_REACTIVATE,
                command.correlationId());
        if (student.reactivate(clock.now())) {
            students.save(student);
            authorship.record(
                    actor,
                    Operation.STUDENT_REACTIVATE,
                    AuthorshipTargetType.STUDENT,
                    student.id(),
                    command.correlationId());
        }
        return RegistrySupport.view(actor, student, teachers, assignments, clock);
    }
}
