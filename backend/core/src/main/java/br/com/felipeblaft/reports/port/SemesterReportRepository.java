package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.semester.SemesterReport;
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

    Optional<SemesterReport> searchApproved(UUID teacherId, UUID studentId, String periodKey);

    Optional<SemesterReport> searchOpen(UUID teacherId, UUID studentId, String periodKey);

    /**
     * Semestrais aprovados do professor para o aluno (histórico), do mais recente ao mais antigo.
     */
    List<SemesterReport> listApproved(UUID teacherId, UUID studentId);
}
