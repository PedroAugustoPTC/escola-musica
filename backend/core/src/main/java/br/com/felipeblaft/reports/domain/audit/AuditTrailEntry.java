package br.com.felipeblaft.reports.domain.audit;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Role;
import java.time.Instant;
import java.util.UUID;

/**
 * Um evento da trilha como lido pela consulta (FR-045). Os rótulos (nome atual do autor e do alvo)
 * são resolvidos na leitura e nunca gravados na trilha; {@code targetErased} marca o aluno
 * eliminado, que a tela mostra como "Aluno removido".
 */
public record AuditTrailEntry(
        UUID id,
        long sequence,
        Instant occurredAt,
        UUID authorId,
        Role authorRole,
        String authorLabel,
        ActionOrigin origin,
        String operation,
        String targetType,
        UUID targetId,
        String targetLabel,
        boolean targetErased,
        Integer documentVersion,
        Integer itemCount,
        AuthorshipResult result,
        String errorCode,
        boolean hasOriginHash) {

    /** Não revela os rótulos (nomes). */
    @Override
    public String toString() {
        return "AuditTrailEntry[sequence=" + sequence + ", operation=" + operation + "]";
    }
}
