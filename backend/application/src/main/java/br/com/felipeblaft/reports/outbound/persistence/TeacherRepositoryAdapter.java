package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.TeacherRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Professores no PostgreSQL. Um professor inativo nunca guarda código de acesso: a gravação anula
 * hash, validade e renovação mesmo que o objeto ainda os traga (FR-016).
 */
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
        jpa.save(entityOf(teacher));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Teacher> list(String search, Boolean active, PageRequest page) {
        List<TeacherEntity> rows = active == null ? jpa.findAll() : jpa.findByActive(active);
        return NameAndEmailListing.page(
                rows.stream().map(RegistryMapper::toDomain).toList(),
                search,
                Teacher::name,
                Teacher::email,
                page);
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(Teacher teacher) {
        TeacherEntity e = entityOf(teacher);
        return jpa.insertIfAbsent(
                        e.getId(),
                        e.getName(),
                        e.getEmail(),
                        e.isActive(),
                        e.getAccessCodeHash(),
                        e.getAccessCodeExpiresAt(),
                        e.getAccessCodeRenewedAt(),
                        e.getCreatedAt(),
                        e.getUpdatedAt())
                > 0;
    }

    private static TeacherEntity entityOf(Teacher t) {
        boolean keepCode = t.active();
        return new TeacherEntity(
                t.id(),
                t.name(),
                t.email(),
                t.active(),
                keepCode ? t.accessCodeHash() : null,
                keepCode ? t.accessCodeExpiresAt() : null,
                keepCode ? t.accessCodeRenewedAt() : null,
                t.createdAt(),
                t.updatedAt());
    }
}
