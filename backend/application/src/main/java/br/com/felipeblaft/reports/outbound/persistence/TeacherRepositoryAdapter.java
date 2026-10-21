package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class TeacherRepositoryAdapter implements TeacherRepository {

    private final TeacherJpa jpa;

    TeacherRepositoryAdapter(TeacherJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Teacher> searchById(UUID id) {
        return jpa.findById(id).map(RegistryMapper::toDomain);
    }

    @Override
    public Optional<Teacher> searchByEmail(String normalizedEmail) {
        return jpa.findByEmail(normalizedEmail).map(RegistryMapper::toDomain);
    }

    @Override
    public Optional<Teacher> searchByAccessCodeHash(String hash) {
        return jpa.findByAccessCodeHash(hash).map(RegistryMapper::toDomain);
    }

    @Override
    public void save(Teacher teacher) {
        jpa.save(RegistryMapper.toEntity(teacher));
    }

    @Override
    public PageResult<Teacher> list(String search, Boolean active, PageRequest page) {
        throw PendingImplementation.of("W-B");
    }

    @Override
    public boolean insertIfAbsent(Teacher teacher) {
        throw PendingImplementation.of("W-B");
    }
}
