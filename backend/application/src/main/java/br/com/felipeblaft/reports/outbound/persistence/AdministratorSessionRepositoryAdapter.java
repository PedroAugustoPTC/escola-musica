package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Sessões do administrador no PostgreSQL. Stub da Fundação (feature 005): a tabela e o adaptador
 * real chegam com o fluxo W-E.
 */
@Repository
class AdministratorSessionRepositoryAdapter implements AdministratorSessionRepository {

    @Override
    public Optional<Found> searchByTokenHash(String tokenHash) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public void save(AdministratorSession session) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public void remove(UUID id) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public int revokeAllOf(UUID administratorId, SessionRevocationReason reason, Instant now) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    public int removeExpired(Instant now) {
        throw PendingImplementation.of("W-E");
    }
}
