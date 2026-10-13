package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Semestral só do próprio professor e de aluno associado; outro caso é RECURSO_NAO_ENCONTRADO. */
public class SemesterAccessService {

    private final SemesterReportRepository semester;
    private final AssignmentRepository assignments;

    public SemesterAccessService(
            SemesterReportRepository semester, AssignmentRepository assignments) {
        this.semester = Objects.requireNonNull(semester);
        this.assignments = Objects.requireNonNull(assignments);
    }

    public SemesterReport report(UUID teacherId, UUID reportId) {
        return ofOwner(semester.searchById(reportId), teacherId);
    }

    public SemesterReport reportForUpdate(UUID teacherId, UUID reportId) {
        return ofOwner(semester.searchForUpdate(reportId), teacherId);
    }

    private SemesterReport ofOwner(Optional<SemesterReport> report, UUID teacherId) {
        return report.filter(r -> r.teacherId().equals(teacherId))
                .filter(r -> assignments.exists(teacherId, r.studentId()))
                .orElseThrow(() -> new DomainException(ErrorCode.RECURSO_NAO_ENCONTRADO));
    }
}
