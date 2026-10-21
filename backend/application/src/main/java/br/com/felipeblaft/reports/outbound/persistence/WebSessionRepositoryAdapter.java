package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.access.WebSession;
import br.com.felipeblaft.reports.outbound.PendingImplementation;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class WebSessionRepositoryAdapter implements WebSessionRepository {

    private final WebSessionJpa jpa;

    WebSessionRepositoryAdapter(WebSessionJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<WebSession> searchByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash)
                .map(
                        e ->
                                new WebSession(
                                        e.getId(),
                                        e.getTeacherId(),
                                        e.getTokenHash(),
                                        e.getCreatedAt(),
                                        e.getLastUsedAt(),
                                        e.getExpiresAt()));
    }

    @Override
    public void save(WebSession s) {
        jpa.save(
                new WebSessionEntity(
                        s.id(),
                        s.teacherId(),
                        s.tokenHash(),
                        s.createdAt(),
                        s.lastUsedAt(),
                        s.expiresAt()));
    }

    @Override
    public void remove(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public int revokeAllOf(UUID teacherId, SessionRevocationReason reason, Instant now) {
        throw PendingImplementation.of("W-E");
    }

    @Override
    @Transactional
    public int removeExpired(Instant now) {
        return jpa.removeExpired(now);
    }
}
