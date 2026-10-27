package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administradores no PostgreSQL (feature 005, migração V9). A inserção do primeiro administrador é
 * serializada por um bloqueio consultivo da transação; a de e-mail novo usa {@code ON CONFLICT DO
 * NOTHING}, que não aborta a transação; e a base do invariante de nunca ficar sem administrador
 * ativo é o bloqueio de todos os ativos, em ordem fixa de id.
 */
@Repository
class AdministratorRepositoryAdapter implements AdministratorRepository {

    /** Chave do bloqueio consultivo que serializa a criação do primeiro administrador. */
    private static final long FIRST_ADMINISTRATOR_LOCK = 2_005_000_001L;

    private final AdministratorJpa jpa;

    AdministratorRepositoryAdapter(AdministratorJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Administrator> searchById(UUID id) {
        return jpa.findById(id).map(AdministratorMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Administrator> searchByEmail(String normalizedEmail) {
        return jpa.findByEmail(normalizedEmail).map(AdministratorMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpa.count();
    }

    @Override
    @Transactional
    public boolean insertIfNoneExists(Administrator administrator) {
        jpa.lockFirstAdministratorCreation(FIRST_ADMINISTRATOR_LOCK);
        if (jpa.count() > 0) {
            return false;
        }
        jpa.saveAndFlush(AdministratorMapper.toEntity(administrator));
        return true;
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(Administrator a) {
        return jpa.insertIfAbsent(
                        a.id(),
                        a.name(),
                        a.email(),
                        a.active(),
                        a.passwordHash(),
                        a.passwordProvisional(),
                        a.passwordProvisionalExpiresAt(),
                        a.activationCodeHash(),
                        a.activationCodeExpiresAt(),
                        a.activatedAt(),
                        a.createdAt(),
                        a.updatedAt())
                > 0;
    }

    @Override
    @Transactional
    public void save(Administrator administrator) {
        jpa.save(AdministratorMapper.toEntity(administrator));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Administrator> list(String search, Boolean active, PageRequest page) {
        List<AdministratorEntity> rows = active == null ? jpa.findAll() : jpa.findByActive(active);
        return NameAndEmailListing.page(
                rows.stream().map(AdministratorMapper::toDomain).toList(),
                search,
                Administrator::name,
                Administrator::email,
                page);
    }

    @Override
    @Transactional
    public List<Administrator> lockAllActiveForUpdate() {
        return jpa.lockAllActive().stream().map(AdministratorMapper::toDomain).toList();
    }
}
