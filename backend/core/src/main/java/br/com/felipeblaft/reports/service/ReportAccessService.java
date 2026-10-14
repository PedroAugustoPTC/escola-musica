package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import java.util.Objects;
import java.util.UUID;

/**
 * Carrega relatórios só para o dono (FR-009, FR-048): relatório de outro professor, ou de aluno que
 * deixou de estar associado, é tratado como inexistente.
 */
public class ReportAccessService {

    private final LessonReportRepository reports;
    private final AssignmentRepository assignments;

    public ReportAccessService(LessonReportRepository reports, AssignmentRepository assignments) {
        this.reports = Objects.requireNonNull(reports);
        this.assignments = Objects.requireNonNull(assignments);
    }

    public LessonReport lessonReport(UUID teacherId, UUID reportId) {
        return ofOwner(reports.searchById(reportId), teacherId);
    }

    /** Como {@link #lessonReport}, com a linha bloqueada para a atualização. */
    public LessonReport lessonReportForUpdate(UUID teacherId, UUID reportId) {
        return ofOwner(reports.searchForUpdate(reportId), teacherId);
    }

    private LessonReport ofOwner(java.util.Optional<LessonReport> report, UUID teacherId) {
        return report.filter(r -> r.teacherId().equals(teacherId))
                .filter(r -> assignments.exists(teacherId, r.studentId()))
                .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
