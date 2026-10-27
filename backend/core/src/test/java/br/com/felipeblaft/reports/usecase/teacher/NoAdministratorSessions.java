package br.com.felipeblaft.reports.usecase.teacher;

import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessões de administrador que só registram as revogações pedidas; serve aos testes de professor,
 * que não criam sessões de administrador.
 */
public class NoAdministratorSessions implements AdministratorSessionRepository {

    /** Revogação pedida. */
    public record Call(UUID administratorId, SessionRevocationReason reason, Instant at) {}

    private final List<Call> calls = new ArrayList<>();
    private int toRevoke = 2;

    @Override
    public Optional<Found> searchByTokenHash(String tokenHash) {
        return Optional.empty();
    }

    @Override
    public void save(AdministratorSession session) {}

    @Override
    public void remove(UUID id) {}

    @Override
    public int revokeAllOf(UUID administratorId, SessionRevocationReason reason, Instant now) {
        calls.add(new Call(administratorId, reason, now));
        return toRevoke;
    }

    @Override
    public int removeExpired(Instant now) {
        return 0;
    }

    public List<Call> calls() {
        return calls;
    }

    /** Quantas sessões a próxima revogação diz ter marcado. */
    public void revokesPerCall(int quantity) {
        this.toRevoke = quantity;
    }
}
