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
}
