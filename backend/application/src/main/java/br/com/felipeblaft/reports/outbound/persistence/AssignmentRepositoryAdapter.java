package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class AssignmentRepositoryAdapter implements AssignmentRepository {

    private final TeacherStudentJpa jpa;

    AssignmentRepositoryAdapter(TeacherStudentJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean exists(UUID teacherId, UUID studentId) {
        return jpa.existsByTeacherIdAndStudentId(teacherId, studentId);
    }

    @Override
    public void save(TeacherStudent assignment) {
        jpa.save(RegistryMapper.toEntity(assignment));
    }

    @Override
    @Transactional
    public boolean delete(UUID teacherId, UUID studentId) {
        return jpa.deleteByPair(teacherId, studentId) > 0;
    }

    @Override
    public List<UUID> listTeachersOfStudent(UUID studentId) {
        return jpa.listTeachersOfStudent(studentId);
    }

    @Override
    public int countActiveTeachers(UUID studentId) {
        return jpa.countActiveTeachers(studentId);
    }

    @Override
    @Transactional
    public int deleteAllByStudent(UUID studentId) {
        return jpa.deleteAllByStudent(studentId);
    }
}
