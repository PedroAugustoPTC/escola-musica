package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.access.AdministratorSession;
import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessões do administrador. A validação é feita a cada requisição, com uma única consulta que traz
 * a sessão e a conta (sem cache de autorização, FR-047).
 */
public interface AdministratorSessionRepository {

    /** Sessão acompanhada da conta, para decidir validade e situação numa só leitura. */
    record Found(AdministratorSession session, Administrator administrator) {}

    Optional<Found> searchByTokenHash(String tokenHash);

    void save(AdministratorSession session);

    /** "Sair": apaga só aquela sessão. */
    void remove(UUID id);

    /** Marca como revogadas todas as sessões ainda válidas do administrador; devolve quantas. */
    int revokeAllOf(UUID administratorId, SessionRevocationReason reason, Instant now);

    /** Varredura diária de sessões vencidas; devolve quantas foram apagadas. */
    int removeExpired(Instant now);
}
