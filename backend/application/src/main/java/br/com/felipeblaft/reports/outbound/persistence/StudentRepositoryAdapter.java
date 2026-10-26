package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.port.StudentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class StudentRepositoryAdapter implements StudentRepository {

    private final StudentJpa jpa;

    StudentRepositoryAdapter(StudentJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Student> searchById(UUID id) {
        return jpa.findById(id).map(RegistryMapper::toDomain);
    }

    @Override
    public Optional<Student> searchByCpfHash(String cpfHash) {
        return jpa.findByCpfHash(cpfHash).map(RegistryMapper::toDomain);
    }

    @Override
    public List<Student> listByTeacher(UUID teacherId) {
        return jpa.listByTeacher(teacherId).stream().map(RegistryMapper::toDomain).toList();
    }

    @Override
    public void save(Student student) {
        jpa.save(RegistryMapper.toEntity(student));
    }

    @Override
    public Optional<Student> findVisible(StudentScope scope, UUID id) {
        Optional<StudentEntity> found =
                scope.unrestricted()
                        ? jpa.findById(id)
                        : jpa.findVisibleToTeacher(id, scope.teacherId());
        return found.map(RegistryMapper::toDomain);
    }

    @Override
    public List<Student> list(StudentScope scope, StudentStatusFilter status) {
        List<Boolean> states =
                switch (status) {
                    case ACTIVE -> List.of(true);
                    case INACTIVE -> List.of(false);
                    case ALL -> List.of(true, false);
                };
        List<StudentEntity> entities =
                scope.unrestricted()
                        ? jpa.listByState(states)
                        : jpa.listByTeacherAndState(scope.teacherId(), states);
        return entities.stream().map(RegistryMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(Student student) {
        return jpa.insertIfNewCpf(
                        student.id(),
                        student.name(),
                        student.birthDate(),
                        student.encryptedCpf(),
                        student.cpfHash(),
                        student.guardianName(),
                        student.guardianPhone(),
                        student.guardianEmail(),
                        student.instrument(),
                        student.active(),
                        student.createdAt(),
                        student.updatedAt())
                > 0;
    }
}
