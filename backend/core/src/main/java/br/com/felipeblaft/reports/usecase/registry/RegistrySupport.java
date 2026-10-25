package br.com.felipeblaft.reports.usecase.registry;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Permission;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Passos comuns da situação do aluno e das associações. É uma interface só com métodos estáticos
 * para não virar um segundo "caso de uso": a decisão de permissão continua em cada caso de uso, que
 * é quem consulta a matriz.
 */
interface RegistrySupport {

    /**
     * Aluno visível ao ator. Para o professor, aluno alheio, sem professor ou inexistente deixa o
     * mesmo registro de negativa (FR-040, FR-025) e o mesmo erro, sem nenhum dado.
     */
    static Student visible(
            StudentAccessService access,
            AuthorshipService authorship,
            Actor actor,
            UUID studentId,
            String correlationId) {
        try {
            return access.findVisible(actor, studentId);
        } catch (DomainException denied) {
            if (actor.role() == Role.TEACHER) {
                authorship.record(
                        actor,
                        AuthorshipRecord.AUTHORIZATION_DENIED,
                        AuthorshipTargetType.STUDENT,
                        studentId,
                        AuthorshipResult.DENIED,
                        denied.code().name(),
                        correlationId);
            }
            throw denied;
        }
    }

    /** O professor inativo não age (a sessão dele já terminou; aqui é a defesa do núcleo). */
    static void requireActiveActor(Permission permission, Actor actor, TeacherRepository teachers) {
        if (permission != Permission.ALLOW_SOLE_ACTIVE_TEACHER || actor.role() != Role.TEACHER) {
            return;
        }
        Teacher teacher =
                teachers.searchById(actor.id())
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!teacher.active()) {
            throw new DomainException(ErrorCode.TEACHER_INACTIVE);
        }
    }

    /**
     * FR-026: com mais de um professor ativo associado, o professor não inativa nem reativa; a
     * negativa deixa registro em transação independente e orienta procurar o administrador.
     */
    static void requireSoleActiveTeacher(
            Permission permission,
            Actor actor,
            Student student,
            AssignmentRepository assignments,
            AuthorshipService authorship,
            Operation operation,
            String correlationId) {
        if (permission != Permission.ALLOW_SOLE_ACTIVE_TEACHER
                || assignments.countActiveTeachers(student.id()) <= 1) {
            return;
        }
        authorship.record(
                actor,
                operation.name(),
                AuthorshipTargetType.STUDENT,
                student.id(),
                AuthorshipResult.DENIED,
                ErrorCode.SHARED_STUDENT_ACTION_DENIED.name(),
                correlationId);
        throw new DomainException(ErrorCode.SHARED_STUDENT_ACTION_DENIED);
    }

    /**
     * O aluno como a interface o recebe (sem CPF). O administrador vê os professores associados; o
     * professor nunca vê os outros e recebe se ainda pode inativar (FR-026).
     */
    static StudentView view(
            Actor actor,
            Student student,
            TeacherRepository teachers,
            AssignmentRepository assignments,
            ClockPort clock) {
        boolean hasGuardian =
                student.guardianName() != null
                        || student.guardianPhone() != null
                        || student.guardianEmail() != null;
        StudentView.GuardianView guardian =
                hasGuardian
                        ? new StudentView.GuardianView(
                                student.guardianName(),
                                student.guardianPhone(),
                                student.guardianEmail())
                        : null;
        boolean administrator = actor.role() == Role.ADMINISTRATOR;
        List<StudentView.TeacherRef> refs =
                administrator
                        ? assignments.listTeachersOfStudent(student.id()).stream()
                                .map(teachers::searchById)
                                .flatMap(Optional::stream)
                                .map(t -> new StudentView.TeacherRef(t.id(), t.name(), t.active()))
                                .toList()
                        : List.of();
        Boolean canInactivate =
                administrator ? null : assignments.countActiveTeachers(student.id()) <= 1;
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

    /**
     * Alvo do registro de autoria de uma associação. A associação não tem identificador conhecido
     * na retirada, então o alvo é um identificador estável derivado do par: o registro de associar
     * e o de retirar o mesmo par apontam para o mesmo alvo.
     */
    static UUID assignmentTarget(UUID teacherId, UUID studentId) {
        return UUID.nameUUIDFromBytes(
                ("assignment:" + teacherId + ":" + studentId).getBytes(StandardCharsets.UTF_8));
    }
}
