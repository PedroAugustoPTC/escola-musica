package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.access.WebSession;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessões da Web em memória. A revogação marca a própria sessão (R-08), por {@link
 * WebSession#revoke}, sem removê-la; {@link #revocationOf(UUID)} lê o motivo e o instante dela.
 */
public class InMemoryWebSessionRepository implements WebSessionRepository {

    /** Marca de revogação de uma sessão. */
    public record Revocation(SessionRevocationReason reason, Instant at) {}

    private final Map<UUID, WebSession> data = new LinkedHashMap<>();
    private int recordings;

    @Override
    public Optional<WebSession> searchByTokenHash(String tokenHash) {
        return data.values().stream().filter(s -> s.tokenHash().equals(tokenHash)).findFirst();
    }

    @Override
    public void save(WebSession session) {
        recordings++;
        data.put(session.id(), session);
    }

    @Override
    public void remove(UUID id) {
        data.remove(id);
    }

    @Override
    public int revokeAllOf(UUID teacherId, SessionRevocationReason reason, Instant now) {
        List<WebSession> toRevoke =
                data.values().stream()
                        .filter(s -> s.teacherId().equals(teacherId))
                        .filter(s -> s.validAt(now))
                        .toList();
        toRevoke.forEach(s -> s.revoke(reason, now));
        return toRevoke.size();
    }

    @Override
    public int removeExpired(Instant now) {
        List<UUID> expired =
                data.values().stream()
                        .filter(s -> !now.isBefore(s.expiresAt()))
                        .map(WebSession::id)
                        .toList();
        expired.forEach(this::remove);
        return expired.size();
    }

    public List<WebSession> all() {
        return new ArrayList<>(data.values());
    }

    public int recordings() {
        return recordings;
    }

    public Optional<Revocation> revocationOf(UUID sessionId) {
        return Optional.ofNullable(data.get(sessionId))
                .filter(WebSession::revoked)
                .map(s -> new Revocation(s.revocationReason(), s.revokedAt()));
    }

    public boolean isRevoked(UUID sessionId) {
        return Optional.ofNullable(data.get(sessionId)).map(WebSession::revoked).orElse(false);
    }
}
