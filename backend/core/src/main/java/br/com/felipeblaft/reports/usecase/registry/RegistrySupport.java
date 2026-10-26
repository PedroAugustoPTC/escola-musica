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
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Passos comuns da situação do aluno e das associações. É uma interface só com métodos estáticos
 * para não virar um segundo "caso de uso": a decisão de permissão continua em cada caso de uso, que
 * é quem consulta a matriz.
 */
interface RegistrySupport {

    /**
     * Aluno visível ao ator ou {@code RESOURCE_NOT_FOUND}. Quando o professor não encontra (aluno
     * alheio, sem professor ou inexistente, sem distinguir), grava a negativa (FR-040, FR-025) com
     * o mesmo caminho nos dois casos. É a única implementação, de todos os casos de uso de aluno.
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
     * O professor inativo não age (a sessão dele já terminou; aqui é a defesa do núcleo); professor
     * desconhecido é {@code RESOURCE_NOT_FOUND}. Vale para toda operação de aluno do professor.
     */
    static void requireActiveActor(Actor actor, TeacherRepository teachers) {
        if (actor.role() != Role.TEACHER) {
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
     * Alvo do registro de autoria de uma associação. A associação não tem identificador conhecido
     * na retirada, então o alvo é um identificador estável derivado do par: o registro de associar
     * e o de retirar o mesmo par apontam para o mesmo alvo.
     */
    static UUID assignmentTarget(UUID teacherId, UUID studentId) {
        return UUID.nameUUIDFromBytes(
                ("assignment:" + teacherId + ":" + studentId).getBytes(StandardCharsets.UTF_8));
    }
}
