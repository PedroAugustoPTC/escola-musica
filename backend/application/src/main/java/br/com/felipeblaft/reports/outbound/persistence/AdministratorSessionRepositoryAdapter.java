package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessões do administrador no PostgreSQL (feature 005, migração V9). A consulta por token traz a
 * conta na mesma leitura, para decidir a validade a cada requisição, sem cache de autorização
 * (FR-047). A revogação marca a sessão e nunca a remove.
 */
@Repository
class AdministratorSessionRepositoryAdapter implements AdministratorSessionRepository {

    private final AdministratorSessionJpa jpa;

    AdministratorSessionRepositoryAdapter(AdministratorSessionJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Found> searchByTokenHash(String tokenHash) {
        List<Object[]> rows = jpa.findWithAccountByTokenHash(tokenHash);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] row = rows.getFirst();
        return Optional.of(
                new Found(
                        AdministratorMapper.toDomain((AdministratorSessionEntity) row[0]),
                        AdministratorMapper.toDomain((AdministratorEntity) row[1])));
    }

    @Override
    @Transactional
    public void save(AdministratorSession session) {
        jpa.saveAndFlush(AdministratorMapper.toEntity(session));
    }

    @Override
    @Transactional
    public void remove(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    @Transactional
    public int revokeAllOf(UUID administratorId, SessionRevocationReason reason, Instant now) {
        return jpa.revokeAllOf(administratorId, reason.name(), now);
    }

    @Override
    @Transactional
    public int removeExpired(Instant now) {
        return jpa.removeExpired(now);
    }
}
