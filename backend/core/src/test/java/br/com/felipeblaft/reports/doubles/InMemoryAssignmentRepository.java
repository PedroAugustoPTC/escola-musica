package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Associações professor-aluno em memória. Para contar professores ativos o dublê precisa conhecer
 * os professores: construído com um {@link InMemoryTeacherRepository}, conta só os ativos
 * (professor desconhecido não conta); sem ele, todo professor associado conta como ativo.
 */
public class InMemoryAssignmentRepository implements AssignmentRepository {

    private final List<TeacherStudent> data = new ArrayList<>();
    private final InMemoryTeacherRepository teachers;

    public InMemoryAssignmentRepository() {
        this(null);
    }

    public InMemoryAssignmentRepository(InMemoryTeacherRepository teachers) {
        this.teachers = teachers;
    }

    @Override
    public boolean exists(UUID teacherId, UUID studentId) {
        return data.stream()
                .anyMatch(a -> a.teacherId().equals(teacherId) && a.studentId().equals(studentId));
    }

    /** Grava a associação; repetir o par viola a unicidade, como no banco. */
    @Override
    public void save(TeacherStudent assignment) {
        if (exists(assignment.teacherId(), assignment.studentId())) {
            throw new IllegalStateException("associação já existe");
        }
        data.add(assignment);
    }

    public void assign(UUID teacherId, UUID studentId) {
        data.add(TeacherStudent.create(teacherId, studentId, Instant.EPOCH));
    }

    public List<UUID> studentsOf(UUID teacherId) {
        return data.stream()
                .filter(a -> a.teacherId().equals(teacherId))
                .map(TeacherStudent::studentId)
                .toList();
    }

    public List<TeacherStudent> all() {
        return List.copyOf(data);
    }

    @Override
    public boolean delete(UUID teacherId, UUID studentId) {
        return data.removeIf(
                a -> a.teacherId().equals(teacherId) && a.studentId().equals(studentId));
    }

    @Override
    public List<UUID> listTeachersOfStudent(UUID studentId) {
        return data.stream()
                .filter(a -> a.studentId().equals(studentId))
                .map(TeacherStudent::teacherId)
                .toList();
    }

    @Override
    public int countActiveTeachers(UUID studentId) {
        return (int)
                listTeachersOfStudent(studentId).stream().filter(this::isActiveTeacher).count();
    }

    private boolean isActiveTeacher(UUID teacherId) {
        if (teachers == null) {
            return true;
        }
        return teachers.searchById(teacherId).map(Teacher::active).orElse(false);
    }
}
