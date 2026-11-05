package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemesterReportRepository {

    /**
     * @throws ConcurrencyConflictException se outro canal gravou antes (bloqueio otimista) ou se já
     *     existe semestral aprovado ou em aberto para o trio professor–aluno–período
     */
    void save(SemesterReport report);

    Optional<SemesterReport> searchById(UUID id);

    /** Lê bloqueando a linha até o fim da transação (ações concorrentes dos dois canais). */
    Optional<SemesterReport> searchForUpdate(UUID id);

    /** Como a dos relatórios de aula: ids a reavaliar pela retenção, em ordem de id. */
    List<UUID> findRetentionCandidates(
            RetentionPolicy policy, Instant now, int limit, UUID afterId);

    Optional<SemesterReport> searchApproved(UUID teacherId, UUID studentId, String periodKey);

    Optional<SemesterReport> searchOpen(UUID teacherId, UUID studentId, String periodKey);

    /**
     * Semestrais aprovados do professor para o aluno (histórico), do mais recente ao mais antigo.
     */
    List<SemesterReport> listApproved(UUID teacherId, UUID studentId);
}
