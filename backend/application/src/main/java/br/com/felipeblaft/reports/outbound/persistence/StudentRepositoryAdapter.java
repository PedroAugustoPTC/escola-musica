package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.StudentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

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
        throw PendingImplementation.of("W-G");
    }

    @Override
    public List<Student> list(StudentScope scope, StudentStatusFilter status) {
        throw PendingImplementation.of("W-G");
    }

    @Override
    public boolean insertIfAbsent(Student student) {
        throw PendingImplementation.of("W-G");
    }
}
