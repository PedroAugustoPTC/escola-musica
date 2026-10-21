package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.report.OpenReport;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consultas de leitura sobre relatórios em aberto e aulas (FR-012, FR-034, FR-035). Só entram
 * relatórios de professor ativo e de aluno ainda associado a ele: os demais ficam congelados, sem
 * alteração, e voltam quando a associação ou o professor voltam (R-23).
 */
public interface OpenReportsQueryPort {

    /** Relatórios em aberto do professor, do mais recente ao mais antigo. */
    List<OpenReport> listByTeacher(UUID teacherId);

    /** Relatório de aula (ou registro em transcrição) em aberto do professor para o aluno. */
    Optional<OpenReport> searchByStudent(UUID teacherId, UUID studentId);

    /** Data da última aula de cada aluno do professor. */
    Map<UUID, LocalDate> lastLessonByStudent(UUID teacherId);

    /**
     * Quantos relatórios em aberto do professor existem para o aluno (prévia da retirada de
     * associação: eles ficam congelados, nunca alterados, R-23).
     */
    int countOpenReports(UUID teacherId, UUID studentId);
}
