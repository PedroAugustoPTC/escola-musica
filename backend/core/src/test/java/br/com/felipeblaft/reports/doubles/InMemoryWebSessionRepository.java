package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.WebSession;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryWebSessionRepository implements WebSessionRepository {

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
    public int removeExpired(Instant now) {
        List<UUID> expired =
                data.values().stream().filter(s -> !s.validAt(now)).map(WebSession::id).toList();
        expired.forEach(data::remove);
        return expired.size();
    }

    public List<WebSession> all() {
        return new ArrayList<>(data.values());
    }

    public int recordings() {
        return recordings;
    }
}
