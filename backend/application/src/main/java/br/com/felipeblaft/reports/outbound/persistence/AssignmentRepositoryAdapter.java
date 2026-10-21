package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

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
    public boolean delete(UUID teacherId, UUID studentId) {
        throw PendingImplementation.of("W-G");
    }

    @Override
    public List<UUID> listTeachersOfStudent(UUID studentId) {
        throw PendingImplementation.of("W-G");
    }

    @Override
    public int countActiveTeachers(UUID studentId) {
        throw PendingImplementation.of("W-G");
    }
}
