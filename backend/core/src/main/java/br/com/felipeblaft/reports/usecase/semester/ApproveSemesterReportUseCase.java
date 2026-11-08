package br.com.felipeblaft.reports.usecase.semester;

import br.com.felipeblaft.reports.domain.audit.AuditEventType;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.service.DocumentGenerationService;
import br.com.felipeblaft.reports.service.SemesterAccessService;
import br.com.felipeblaft.reports.service.TeacherReportAuditService;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase.Approval;
import java.util.Objects;
import java.util.UUID;

/** Aprovação do semestral na versão apresentada (FR-043, FR-046): gera o PDF oficial. */
public class ApproveSemesterReportUseCase {

    private final SemesterAccessService access;
    private final SemesterReportRepository semester;
    private final ReportVersionRepository versions;
    private final DocumentGenerationService documents;
    private final ClockPort clock;
    private final TeacherReportAuditService audit;

    public ApproveSemesterReportUseCase(
            SemesterAccessService access,
            SemesterReportRepository semester,
            ReportVersionRepository versions,
            DocumentGenerationService documents,
            ClockPort clock,
            TeacherReportAuditService audit) {
        this.access = Objects.requireNonNull(access);
        this.semester = Objects.requireNonNull(semester);
        this.versions = Objects.requireNonNull(versions);
        this.documents = Objects.requireNonNull(documents);
        this.clock = Objects.requireNonNull(clock);
        this.audit = Objects.requireNonNull(audit);
    }

    public Approval run(UUID teacherId, UUID reportId, int confirmedVersion, Channel channel) {
        SemesterReport report = access.reportForUpdate(teacherId, reportId);
        report.approve(confirmedVersion, channel, clock.now());
        semester.save(report);
        ReportVersion version =
                versions.search(ReportType.SEMESTER, report.id(), report.approvedVersion())
                        .orElseThrow();
        documents.generateOfficial(report, version);
        audit.reportDecided(
                teacherId,
                AuditEventType.SEMESTER_REPORT_APPROVE,
                ReportType.SEMESTER,
                report.id(),
                report.approvedVersion());
        return new Approval(report.id(), report.approvedVersion(), report.approvedAt());
    }
}
