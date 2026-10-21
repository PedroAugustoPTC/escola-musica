package br.com.felipeblaft.reports.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TeacherJpa extends JpaRepository<TeacherEntity, UUID> {

    Optional<TeacherEntity> findByEmail(String email);

    Optional<TeacherEntity> findByAccessCodeHash(String accessCodeHash);

    List<TeacherEntity> findByActive(boolean active);

    /** Insere; devolve 0 se o e-mail já existia, sem abortar a transação (FR-041). */
    @Modifying
    @Query(
            value =
                    "insert into teacher (id, name, email, active, access_code_hash,"
                            + " access_code_expires_at, access_code_renewed_at, created_at,"
                            + " updated_at)"
                            + " values (:id, :name, :email, :active, :accessCodeHash,"
                            + " :accessCodeExpiresAt, :accessCodeRenewedAt, :createdAt, :updatedAt)"
                            + " on conflict (email) do nothing",
            nativeQuery = true)
    @SuppressWarnings("java:S107")
    int insertIfAbsent(
            UUID id,
            String name,
            String email,
            boolean active,
            String accessCodeHash,
            Instant accessCodeExpiresAt,
            Instant accessCodeRenewedAt,
            Instant createdAt,
            Instant updatedAt);
}
