package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessões de administrador em memória. A busca devolve a sessão com a conta, como a consulta única
 * do adaptador (FR-047), inclusive a sessão revogada ou vencida: quem decide é o caso de uso. A
 * revogação marca a sessão sem removê-la; "Sair" a remove.
 */
public class InMemoryAdministratorSessionRepository implements AdministratorSessionRepository {

    private final InMemoryAdministratorRepository administrators;
    private final Map<UUID, AdministratorSession> data = new LinkedHashMap<>();
    private int recordings;

    public InMemoryAdministratorSessionRepository(InMemoryAdministratorRepository administrators) {
        this.administrators = Objects.requireNonNull(administrators);
    }

    @Override
    public Optional<Found> searchByTokenHash(String tokenHash) {
        return data.values().stream()
                .filter(session -> session.tokenHash().equals(tokenHash))
                .findFirst()
                .flatMap(
                        session ->
                                administrators
                                        .searchById(session.administratorId())
                                        .map(account -> new Found(session, account)));
    }

    @Override
    public void save(AdministratorSession session) {
        recordings++;
        data.put(session.id(), session);
    }

    @Override
    public void remove(UUID id) {
        data.remove(id);
    }

    @Override
    public int revokeAllOf(UUID administratorId, SessionRevocationReason reason, Instant now) {
        List<AdministratorSession> toRevoke =
                data.values().stream()
                        .filter(session -> session.administratorId().equals(administratorId))
                        .filter(session -> !session.revoked())
                        .filter(session -> !session.expiredAt(now))
                        .toList();
        toRevoke.forEach(session -> data.put(session.id(), session.revoke(reason, now)));
        return toRevoke.size();
    }

    @Override
    public int removeExpired(Instant now) {
        List<UUID> expired =
                data.values().stream()
                        .filter(session -> session.expiredAt(now))
                        .map(AdministratorSession::id)
                        .toList();
        expired.forEach(data::remove);
        return expired.size();
    }

    public List<AdministratorSession> all() {
        return new ArrayList<>(data.values());
    }

    public List<AdministratorSession> ofAdministrator(UUID administratorId) {
        return data.values().stream()
                .filter(session -> session.administratorId().equals(administratorId))
                .toList();
    }

    public Optional<AdministratorSession> byId(UUID id) {
        return Optional.ofNullable(data.get(id));
    }

    public int recordings() {
        return recordings;
    }
}
