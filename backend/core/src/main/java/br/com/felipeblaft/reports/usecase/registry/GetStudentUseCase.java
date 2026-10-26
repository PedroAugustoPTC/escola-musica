package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Abre um aluno ({@code STUDENT_GET}). Aluno alheio, sem professor ou inexistente é {@code
 * RESOURCE_NOT_FOUND}, pelo mesmo caminho e com uma só consulta (R-14); para o professor a negativa
 * deixa um registro {@code AUTHORIZATION_DENIED} com o identificador de correlação (FR-040). A
 * leitura autorizada não gera registro.
 */
public class GetStudentUseCase {

    public record Command(UUID studentId, String correlationId) {}

    private final ActorAuthorizationService authorization;
    private final StudentAccessService access;
    private final AuthorshipService authorship;
    private final Views views;

    public GetStudentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.access = Objects.requireNonNull(access);
        this.authorship = Objects.requireNonNull(authorship);
        this.views = new Views(assignments, teachers, clock);
    }

    public StudentView run(Actor actor, Command command) {
        authorization.require(actor, Operation.STUDENT_GET);
        Student student =
                findVisibleOrRecordDenial(
                        access, authorship, actor, command.studentId(), command.correlationId());
        return views.of(student, actor.role() == Role.TEACHER);
    }

    /**
     * Aluno visível ao ator ou {@code RESOURCE_NOT_FOUND}. Quando o professor não encontra (aluno
     * alheio ou inexistente, sem distinguir), grava a negativa com o mesmo caminho nos dois casos.
     */
    static Student findVisibleOrRecordDenial(
            StudentAccessService access,
            AuthorshipService authorship,
            Actor actor,
            UUID studentId,
            String correlationId) {
        try {
            return access.findVisible(actor, studentId);
        } catch (DomainException error) {
            if (error.code() == ErrorCode.RESOURCE_NOT_FOUND && actor.role() == Role.TEACHER) {
                authorship.record(
                        actor,
                        AuthorshipRecord.AUTHORIZATION_DENIED,
                        AuthorshipTargetType.STUDENT,
                        studentId,
                        AuthorshipResult.DENIED,
                        ErrorCode.RESOURCE_NOT_FOUND.name(),
                        correlationId);
            }
            throw error;
        }
    }

    /**
     * Monta o {@link StudentView} dos casos de uso de aluno: nunca traz CPF; o administrador vê os
     * professores associados, o professor vê se pode inativar (FR-026).
     */
    static final class Views {

        private final AssignmentRepository assignments;
        private final TeacherRepository teachers;
        private final ClockPort clock;

        Views(AssignmentRepository assignments, TeacherRepository teachers, ClockPort clock) {
            this.assignments = Objects.requireNonNull(assignments);
            this.teachers = Objects.requireNonNull(teachers);
            this.clock = Objects.requireNonNull(clock);
        }

        StudentView of(Student student, boolean forTeacher) {
            StudentView.GuardianView guardian =
                    student.guardianName() == null
                            ? null
                            : new StudentView.GuardianView(
                                    student.guardianName(),
                                    student.guardianPhone(),
                                    student.guardianEmail());
            List<StudentView.TeacherRef> refs = forTeacher ? List.of() : teachersOf(student);
            Boolean canInactivate =
                    forTeacher ? assignments.countActiveTeachers(student.id()) <= 1 : null;
            return new StudentView(
                    student.id(),
                    student.name(),
                    student.birthDate(),
                    student.ageAt(clock.todayInSchoolTimezone()),
                    student.instrument(),
                    student.active(),
                    guardian,
                    refs,
                    canInactivate);
        }

        private List<StudentView.TeacherRef> teachersOf(Student student) {
            return assignments.listTeachersOfStudent(student.id()).stream()
                    .map(teachers::searchById)
                    .flatMap(Optional::stream)
                    .sorted(
                            Comparator.comparing(
                                    (Teacher teacher) -> StudentSearch.normalize(teacher.name())))
                    .map(
                            teacher ->
                                    new StudentView.TeacherRef(
                                            teacher.id(), teacher.name(), teacher.active()))
                    .toList();
        }
    }
}
