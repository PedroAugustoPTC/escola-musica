package br.com.felipeblaft.reports.outbound.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AdministratorJpa extends JpaRepository<AdministratorEntity, UUID> {

    Optional<AdministratorEntity> findByEmail(String email);

    List<AdministratorEntity> findByActive(boolean active);

    /**
     * Bloqueia, na transação corrente, todos os administradores ativos em ordem fixa de id (mesma
     * ordem para todos, sem impasse). Quem espera revê a condição {@code active} depois que o outro
     * confirma, então não enxerga quem acabou de ser inativado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdministratorEntity a where a.active = true order by a.id")
    List<AdministratorEntity> lockAllActive();

    /** Serializa a criação do primeiro administrador entre transações (liberado no fim delas). */
    @Query(
            value = "select 1 from (select pg_advisory_xact_lock(:key)) as acquired",
            nativeQuery = true)
    int lockFirstAdministratorCreation(long key);

    /** Insere; devolve 0 se o e-mail já existia, sem abortar a transação. */
    @Modifying
    @Query(
            value =
                    "insert into administrator (id, name, email, active, password_hash,"
                            + " password_provisional, password_provisional_expires_at,"
                            + " activation_code_hash, activation_code_expires_at, activated_at,"
                            + " created_at, updated_at)"
                            + " values (:id, :name, :email, :active, :passwordHash,"
                            + " :passwordProvisional, :passwordProvisionalExpiresAt,"
                            + " :activationCodeHash, :activationCodeExpiresAt, :activatedAt,"
                            + " :createdAt, :updatedAt)"
                            + " on conflict (email) do nothing",
            nativeQuery = true)
    @SuppressWarnings("java:S107")
    int insertIfAbsent(
            UUID id,
            String name,
            String email,
            boolean active,
            String passwordHash,
            boolean passwordProvisional,
            Instant passwordProvisionalExpiresAt,
            String activationCodeHash,
            Instant activationCodeExpiresAt,
            Instant activatedAt,
            Instant createdAt,
            Instant updatedAt);
}
