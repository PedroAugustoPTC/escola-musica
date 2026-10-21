package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Administradores no PostgreSQL. Stub da Fundação (feature 005): a entidade, o repositório JPA e a
 * migração {@code V9} chegam com o fluxo W-E.
 */
@Repository
class AdministratorRepositoryAdapter implements AdministratorRepository {

    @Override
    public Optional<Administrator> searchById(UUID id) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public Optional<Administrator> searchByEmail(String normalizedEmail) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public long count() {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public boolean insertIfNoneExists(Administrator administrator) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public boolean insertIfAbsent(Administrator administrator) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public void save(Administrator administrator) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public PageResult<Administrator> list(String search, Boolean active, PageRequest page) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public List<Administrator> lockAllActiveForUpdate() {
        throw PendingImplementation.of("W-E");
    }
}
