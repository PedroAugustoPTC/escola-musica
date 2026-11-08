package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.audit.AuditAnchor;
import br.com.felipeblaft.reports.domain.audit.AuditChain;
import br.com.felipeblaft.reports.domain.audit.AuditTrailEntry;
import br.com.felipeblaft.reports.domain.audit.AuditTrailFilter;
import br.com.felipeblaft.reports.domain.audit.AuditVerification;
import br.com.felipeblaft.reports.domain.common.PageResult;
import java.util.List;
import java.util.Optional;

/**
 * Leitura da trilha de auditoria (FR-045): consulta paginada, do mais recente ao mais antigo, e as
 * leituras que a verificação da cadeia usa. Não há método de escrita: a gravação é da {@link
 * AuthorshipRecorderPort}.
 */
public interface AuditTrailQueryPort {

    /**
     * Página de eventos. Filtrar por um aluno ({@code targetId}) devolve também os eventos cujo
     * alvo é um relatório ou consentimento dele (SC-007); os rótulos são resolvidos aqui, na
     * leitura.
     */
    PageResult<AuditTrailEntry> query(AuditTrailFilter filter);

    /** Os eventos com {@code sequence} maior que o dado, em ordem de sequência, até o lote. */
    List<AuditChain.Link> chainFrom(long afterSequence, int batchSize);

    /** A âncora mais recente do expurgo, se já houve expurgo. */
    Optional<AuditAnchor> latestAnchor();

    /** O resultado da última verificação (o último evento {@code AUDIT_TRAIL_VERIFY}). */
    Optional<AuditVerification> lastVerification();
}
