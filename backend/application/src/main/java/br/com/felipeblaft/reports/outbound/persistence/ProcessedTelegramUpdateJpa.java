package br.com.felipeblaft.reports.outbound.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ProcessedTelegramUpdateJpa extends JpaRepository<ProcessedTelegramUpdateEntity, Long> {

    /** Insere o {@code update_id} com a origem; devolve 0 se ele já foi processado. */
    @Modifying
    @Query(
            value =
                    "insert into processed_telegram_update (update_id, received_at, origin)"
                            + " values (:updateId, :now, :origin) on conflict (update_id) do nothing",
            nativeQuery = true)
    int registerIfNewWithOrigin(long updateId, Instant now, String origin);

    @Modifying
    @Query(
            value =
                    "update processed_telegram_update set total_actions = :totalActions"
                            + " where update_id = :updateId",
            nativeQuery = true)
    int complete(long updateId, int totalActions);

    @Modifying
    @Query("delete from ProcessedTelegramUpdateEntity u where u.receivedAt < :limit")
    int removeOld(Instant limit);
}
