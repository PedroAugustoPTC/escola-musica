package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.domain.access.WebSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WebSessionRepository {

    Optional<WebSession> searchByTokenHash(String tokenHash);

    void save(WebSession session);

    void remove(UUID id);

    /**
     * Marca como revogadas, com o motivo, todas as sessões ainda válidas do professor (R-08);
     * devolve quantas.
     */
    int revokeAllOf(UUID teacherId, SessionRevocationReason reason, Instant now);

    /** Varredura diária de sessões vencidas; devolve quantas foram apagadas. */
    int removeExpired(Instant now);
}
