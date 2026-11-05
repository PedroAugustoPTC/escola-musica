package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonReportRepository {

    void saveLesson(Lesson lesson);

    Optional<Lesson> searchLesson(UUID lessonId);

    /**
     * @throws ConcurrencyConflictException se outro canal gravou o relatório antes (bloqueio
     *     otimista) ou se já existe relatório em aberto para o par (índice único parcial)
     */
    void save(LessonReport report);

    Optional<LessonReport> searchById(UUID id);

    /**
     * Lê o relatório bloqueando a linha até o fim da transação: ações concorrentes dos dois canais
     * (ex.: aprovação dupla) são serializadas e a segunda vê o estado já gravado.
     */
    Optional<LessonReport> searchForUpdate(UUID id);

    /** Relatório de aula em aberto do professor para o aluno (FR-034). */
    Optional<LessonReport> searchOpenByStudent(UUID teacherId, UUID studentId);

    /** Relatórios de aula aprovados do professor para o aluno com aula no período (com a data). */
    List<ApprovedLesson> listApprovedInPeriod(
            UUID teacherId, UUID studentId, LocalDate start, LocalDate end);

    /** Quantos relatórios de aula do par seguem em aberto com aula no período. */
    int countOpenInPeriod(UUID teacherId, UUID studentId, LocalDate start, LocalDate end);

    /** Todos os relatórios de aula aprovados do par (histórico), em qualquer ordem. */
    List<ApprovedLesson> listApproved(UUID teacherId, UUID studentId);

    /**
     * Ids de relatórios com algo a tratar pela retenção, em ordem de id, depois de {@code afterId}
     * (nulo = do início). Seleção grossa: o caso de uso reavalia cada um sob bloqueio (FR-011).
     */
    List<UUID> findRetentionCandidates(
            RetentionPolicy policy, Instant now, int limit, UUID afterId);

    /** Data da primeira aula do aluno com o professor, se houver. */
    Optional<LocalDate> lessonFirstDate(UUID teacherId, UUID studentId);

    /** Relatório de aula aprovado com a data da aula e o conteúdo aprovado. */
    record ApprovedLesson(
            UUID reportId, LocalDate lessonDate, LessonReportContent content, Instant approvedAt) {}
}
