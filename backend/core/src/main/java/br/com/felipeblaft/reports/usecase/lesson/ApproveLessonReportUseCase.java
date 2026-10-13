package br.com.felipeblaft.reports.usecase.lesson;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.service.DocumentGenerationService;
import br.com.felipeblaft.reports.service.ReportAccessService;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aprovação explícita da versão apresentada (FR-028 a FR-030): irreversível; grava data, hora,
 * versão e canal e gera o PDF oficial. Numa aprovação concorrente, a linha bloqueada faz a segunda
 * ver o relatório já aprovado ({@code RELATORIO_JA_APROVADO}).
 */
public class ApproveLessonReportUseCase {

    public record Approval(UUID reportId, int approvedVersion, Instant approvedAt) {}

    private final ReportAccessService access;
    private final LessonReportRepository reports;
    private final ReportVersionRepository versions;
    private final DocumentGenerationService documents;
    private final ClockPort clock;

    public ApproveLessonReportUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            ReportVersionRepository versions,
            DocumentGenerationService documents,
            ClockPort clock) {
        this.access = Objects.requireNonNull(access);
        this.reports = Objects.requireNonNull(reports);
        this.versions = Objects.requireNonNull(versions);
        this.documents = Objects.requireNonNull(documents);
        this.clock = Objects.requireNonNull(clock);
    }

    public Approval run(UUID teacherId, UUID reportId, int confirmedVersion, Channel channel) {
        LessonReport report = access.lessonReportForUpdate(teacherId, reportId);
        report.approve(confirmedVersion, channel, clock.now());
        reports.save(report);
        ReportVersion version =
                versions.search(ReportType.AULA, report.id(), report.approvedVersion())
                        .orElseThrow();
        documents.generateOfficial(report, version);
        return new Approval(report.id(), report.approvedVersion(), report.approvedAt());
    }
}
