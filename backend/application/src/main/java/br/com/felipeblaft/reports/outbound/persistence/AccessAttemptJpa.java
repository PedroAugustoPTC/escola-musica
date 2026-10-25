package br.com.felipeblaft.reports.outbound.persistence;

import br.com.felipeblaft.reports.domain.access.AttemptScope;
import br.com.felipeblaft.reports.domain.common.Channel;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AccessAttemptJpa extends JpaRepository<AccessAttemptEntity, UUID> {

    Optional<AccessAttemptEntity> findByScopeAndChannelAndSourceHash(
            AttemptScope scope, Channel channel, String originHash);

    /** Lê a linha com bloqueio de escrita ({@code SELECT ... FOR UPDATE}): serializa a contagem. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select e from AccessAttemptEntity e where e.scope = :scope and e.channel = :channel"
                    + " and e.sourceHash = :originHash")
    Optional<AccessAttemptEntity> lockByKey(
            @Param("scope") AttemptScope scope,
            @Param("channel") Channel channel,
            @Param("originHash") String originHash);

    /** Cria a linha vazia se ainda não existe; a corrida entre criadores termina no conflito. */
    @Modifying
    @Query(
            value =
                    "insert into access_attempt (id, scope, channel, source_hash, failed_attempts,"
                            + " updated_at) values (:id, :scope, :channel, :originHash, 0, :now)"
                            + " on conflict (scope, channel, source_hash) do nothing",
            nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("scope") String scope,
            @Param("channel") String channel,
            @Param("originHash") String originHash,
            @Param("now") Instant now);
}
