package br.com.felipeblaft.reports.usecase.semester;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.ReportInteractionRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.service.DocumentGenerationService;
import br.com.felipeblaft.reports.service.SemesterAccessService;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.DocumentSource;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Estado e conteúdo vigente do semestral, iguais nos dois canais (FR-002), e a fonte dos PDFs do
 * semestral para {@code ObterDocumentoRelatorioCasoDeUso} (sempre depois de conferir o dono).
 */
public class GetSemesterReportUseCase implements DocumentSource {

    public record SemesterReportView(
            SemesterReport report,
            Optional<ReportInteraction> lastInteraction,
            Optional<ProcessingJob> jobInProgress,
            boolean pdfAvailable) {}

    private final SemesterAccessService access;
    private final ReportInteractionRepository interactions;
    private final ReportVersionRepository versions;
    private final PdfDocumentRepository documents;
    private final DocumentGenerationService generation;
    private final JobQueuePort queue;

    public GetSemesterReportUseCase(
            SemesterAccessService access,
            ReportInteractionRepository interactions,
            ReportVersionRepository versions,
            PdfDocumentRepository documents,
            DocumentGenerationService generation,
            JobQueuePort queue) {
        this.access = Objects.requireNonNull(access);
        this.interactions = Objects.requireNonNull(interactions);
        this.versions = Objects.requireNonNull(versions);
        this.documents = Objects.requireNonNull(documents);
        this.generation = Objects.requireNonNull(generation);
        this.queue = Objects.requireNonNull(queue);
    }

    public SemesterReportView run(UUID teacherId, UUID reportId) {
        SemesterReport report = access.report(teacherId, reportId);
        boolean pdf =
                document(report.id(), report.currentVersion(), DocumentType.REVIEW).isPresent();
        return new SemesterReportView(
                report,
                interactions.last(ReportType.SEMESTER, report.id()),
                queue.searchInProgressByReport(ReportType.SEMESTER, report.id()),
                pdf);
    }

    @Override
    public ReportType type() {
        return ReportType.SEMESTER;
    }

    @Override
    public ObtainedDocument review(UUID teacherId, UUID reportId, int number) {
        SemesterReport report = access.report(teacherId, reportId);
        return read(report.id(), number, DocumentType.REVIEW, name(report, "-v" + number));
    }

    @Override
    public ObtainedDocument official(UUID teacherId, UUID reportId) {
        SemesterReport report = access.report(teacherId, reportId);
        if (report.status() != ReportStatus.APPROVED) {
            throw notFound();
        }
        return read(report.id(), report.approvedVersion(), DocumentType.OFFICIAL, name(report, ""));
    }

    private static String name(SemesterReport report, String suffix) {
        return "relatorio-semestral-" + report.period().key() + suffix + ".pdf";
    }

    private Optional<PdfDocument> document(UUID reportId, int number, DocumentType type) {
        return versions.search(ReportType.SEMESTER, reportId, number)
                .flatMap(v -> documents.search(v.id(), type));
    }

    private ObtainedDocument read(UUID reportId, int number, DocumentType type, String name) {
        byte[] bytes =
                document(reportId, number, type)
                        .flatMap(generation::open)
                        .orElseThrow(GetSemesterReportUseCase::notFound);
        return new ObtainedDocument(bytes, name);
    }

    private static DomainException notFound() {
        return new DomainException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
