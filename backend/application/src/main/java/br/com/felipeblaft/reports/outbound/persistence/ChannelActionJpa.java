package br.com.felipeblaft.reports.outbound.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ChannelActionJpa extends JpaRepository<ChannelActionEntity, String> {

    /** Insere a ação; devolve 0 se o id já existia (nunca sobrescreve, FR-014). */
    @Modifying
    @Query(
            value =
                    "insert into channel_action (id, batch_id, channel, origin, update_id, chat_id,"
                            + " teacher_id, job_id, type, payload, compensation, state,"
                            + " attempts, next_attempt_at, deadline_at, version, created_at,"
                            + " delivered_at, finalized_at, reason_code)"
                            + " values (:id, :batchId, :channel, :origin, :updateId, :chatId,"
                            + " :teacherId, :jobId, :type, cast(:payload as jsonb),"
                            + " cast(:compensation as jsonb), :state, :attempts,"
                            + " :nextAttemptAt, :deadlineAt, :version, :createdAt, :deliveredAt,"
                            + " :finalizedAt, :reasonCode)"
                            + " on conflict (id) do nothing",
            nativeQuery = true)
    @SuppressWarnings("java:S107")
    int insertIfNew(
            String id,
            String batchId,
            String channel,
            String origin,
            Long updateId,
            long chatId,
            UUID teacherId,
            UUID jobId,
            String type,
            String payload,
            String compensation,
            String state,
            int attempts,
            Instant nextAttemptAt,
            Instant deadlineAt,
            int version,
            Instant createdAt,
            Instant deliveredAt,
            Instant finalizedAt,
            String reasonCode);

    List<ChannelActionEntity> findByUpdateIdOrderBySeq(Long updateId);

    List<ChannelActionEntity> findByBatchIdOrderBySeq(String batchId);

    /**
     * Primeira ação devida que seja a primeira pendente do seu lote (um despachante nunca pega o
     * meio de um lote que outro está reservando) e que não esteja atrás, no mesmo chat, de ação de
     * outro lote ainda {@code PENDENTE} ou {@code ENTREGUE} há menos que a espera máxima (portão de
     * ordem, FR-016). {@code skip locked} faz dois despachantes nunca disputarem a mesma linha.
     */
    @Query(
            value =
                    "select a.batch_id from channel_action a"
                            + " where a.state = 'PENDING' and a.next_attempt_at <= :now"
                            + " and a.seq = (select min(p.seq) from channel_action p"
                            + "              where p.batch_id = a.batch_id and p.state = 'PENDING')"
                            + " and not exists (select 1 from channel_action b"
                            + "   where b.chat_id = a.chat_id and b.seq < a.seq"
                            + "     and b.batch_id <> a.batch_id"
                            + "     and (b.state = 'PENDING'"
                            + "          or (b.state = 'DELIVERED' and b.delivered_at > :confirmationLimit)))"
                            + " order by a.seq limit 1 for update of a skip locked",
            nativeQuery = true)
    Optional<String> nextDueBatch(Instant now, Instant confirmationLimit);

    /** Lease: empurra a próxima tentativa do lote para o futuro e avança a versão. */
    @Modifying
    @Query(
            value =
                    "update channel_action set next_attempt_at = :leaseAte, version = version + 1"
                            + " where batch_id = :batchId and state = 'PENDING'",
            nativeQuery = true)
    int reserveBatch(String batchId, Instant leaseAte);

    /** Grava o novo estado só se a versão ainda for a esperada (controle otimista). */
    @Modifying
    @Query(
            value =
                    "update channel_action set state = :state, attempts = :attempts,"
                            + " next_attempt_at = :nextAttemptAt, delivered_at = :deliveredAt,"
                            + " finalized_at = :finalizedAt, reason_code = :reasonCode,"
                            + " payload = cast(:payload as jsonb),"
                            + " compensation = cast(:compensation as jsonb), version = version + 1"
                            + " where id = :id and version = :expectedVersion",
            nativeQuery = true)
    @SuppressWarnings("java:S107")
    int updateIfVersion(
            String id,
            int expectedVersion,
            String state,
            int attempts,
            Instant nextAttemptAt,
            Instant deliveredAt,
            Instant finalizedAt,
            String reasonCode,
            String payload,
            String compensation);

    /** Entregues cujo prazo venceu sem confirmação: estado final {@code SEM_CONFIRMACAO}. */
    @Modifying
    @Query(
            value =
                    "update channel_action set state = 'PERMANENTLY_FAILED',"
                            + " reason_code = 'NO_CONFIRMATION', finalized_at = :now,"
                            + " next_attempt_at = null, version = version + 1"
                            + " where state = 'DELIVERED' and deadline_at <= :now",
            nativeQuery = true)
    int expireOverdueDelivered(Instant now);

    @Modifying
    @Query(
            value =
                    "update channel_action set payload = null, compensation = null, version = version + 1"
                            + " where finalized_at is not null and finalized_at < :limit"
                            + " and (payload is not null or compensation is not null)",
            nativeQuery = true)
    int clearFinalizedContentUntil(Instant limit);

    @Modifying
    @Query(
            value =
                    "delete from channel_action where finalized_at is not null"
                            + " and finalized_at < :limit",
            nativeQuery = true)
    int removeFinalizedUntil(Instant limit);

    @Query("select count(a) from ChannelActionEntity a where a.state = 'PENDING'")
    long countPendings();

    @Query("select min(a.createdAt) from ChannelActionEntity a where a.state = 'PENDING'")
    Optional<Instant> oldestPendingCreated();

    /** Ações entregues antes do limite e ainda sem confirmação. */
    @Query(
            "select count(a) from ChannelActionEntity a where a.state = 'DELIVERED'"
                    + " and a.deliveredAt < :limit")
    long countDeliveredSince(Instant limit);
}
