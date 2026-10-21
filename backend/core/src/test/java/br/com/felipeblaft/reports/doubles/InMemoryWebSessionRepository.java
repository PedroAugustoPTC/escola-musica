package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.access.WebSession;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessões da Web em memória. A revogação marca a sessão sem removê-la (R-08): o motivo e o instante
 * ficam num registro à parte, consultável por {@link #revocationOf(UUID)}, porque {@link
 * WebSession} ainda não carrega o estado de revogação.
 */
public class InMemoryWebSessionRepository implements WebSessionRepository {

    /** Marca de revogação de uma sessão. */
    public record Revocation(SessionRevocationReason reason, Instant at) {}

    private final Map<UUID, WebSession> data = new LinkedHashMap<>();
    private final Map<UUID, Revocation> revocations = new HashMap<>();
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
        revocations.remove(id);
    }

    @Override
    public int revokeAllOf(UUID teacherId, SessionRevocationReason reason, Instant now) {
        List<WebSession> toRevoke =
                data.values().stream()
                        .filter(s -> s.teacherId().equals(teacherId))
                        .filter(s -> s.validAt(now))
                        .filter(s -> !revocations.containsKey(s.id()))
                        .toList();
        toRevoke.forEach(s -> revocations.put(s.id(), new Revocation(reason, now)));
        return toRevoke.size();
    }

    @Override
    public int removeExpired(Instant now) {
        List<UUID> expired =
                data.values().stream().filter(s -> !s.validAt(now)).map(WebSession::id).toList();
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
        return Optional.ofNullable(revocations.get(sessionId));
    }

    public boolean isRevoked(UUID sessionId) {
        return revocations.containsKey(sessionId);
    }
}
