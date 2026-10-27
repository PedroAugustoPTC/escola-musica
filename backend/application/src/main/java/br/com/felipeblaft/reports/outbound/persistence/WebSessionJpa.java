package br.com.felipeblaft.reports.outbound.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface WebSessionJpa extends JpaRepository<WebSessionEntity, UUID> {

    Optional<WebSessionEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from WebSessionEntity s where s.expiresAt <= :now")
    int removeExpired(Instant now);

    /**
     * Marca como revogadas as sessões ainda válidas; quem já foi revogada mantém o primeiro motivo.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "update web_session set revoked_at = :now, revocation_reason = :reason"
                            + " where teacher_id = :teacherId and revoked_at is null"
                            + " and expires_at > :now",
            nativeQuery = true)
    int revokeAllOf(UUID teacherId, String reason, Instant now);
}
