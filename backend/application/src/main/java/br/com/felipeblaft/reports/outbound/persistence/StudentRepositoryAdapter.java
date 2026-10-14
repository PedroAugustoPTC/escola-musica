package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.registry.Student;
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
}
