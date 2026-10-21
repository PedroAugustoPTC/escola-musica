package br.com.felipeblaft.reports.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AdministratorSessionJpa extends JpaRepository<AdministratorSessionEntity, UUID> {

    /** Sessão e conta numa só leitura: cada linha é {@code [sessão, conta]}. */
    @Query(
            "select s, a from AdministratorSessionEntity s join AdministratorEntity a"
                    + " on a.id = s.administratorId where s.tokenHash = :tokenHash")
    List<Object[]> findWithAccountByTokenHash(String tokenHash);

    /**
     * Marca como revogadas as sessões ainda válidas; quem já foi revogada mantém o primeiro motivo.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "update administrator_session set revoked_at = :now, revocation_reason = :reason"
                            + " where administrator_id = :administratorId and revoked_at is null"
                            + " and expires_at > :now",
            nativeQuery = true)
    int revokeAllOf(UUID administratorId, String reason, Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AdministratorSessionEntity s where s.expiresAt <= :now")
    int removeExpired(Instant now);
}
