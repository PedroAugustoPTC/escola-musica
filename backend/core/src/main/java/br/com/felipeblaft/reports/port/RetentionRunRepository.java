package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.privacy.RetentionItemType;
import br.com.felipeblaft.reports.domain.privacy.RetentionRunStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Execuções da retenção e seus itens, sem conteúdo (FR-015). */
public interface RetentionRunRepository {

    /**
     * Abre uma execução. Uma execução {@code RUNNING} iniciada há mais que {@code staleAfter} vira
     * {@code INTERRUPTED}; se ainda houver uma {@code RUNNING} recente, não abre e devolve vazio.
     */
    Optional<UUID> start(Instant now, Map<String, Integer> policySnapshot, Duration staleAfter);

    void addItem(UUID runId, RetentionItemType type, UUID targetId, int count);

    void finish(UUID runId, Instant now, RetentionRunStatus status, String errorCode);

    /** Remove as execuções iniciadas antes do corte (com os itens); devolve quantas saíram. */
    int purgeStartedBefore(Instant cutoff);
}
