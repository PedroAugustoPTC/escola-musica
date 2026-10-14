package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.port.AssignmentRepository;
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
}
