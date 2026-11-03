package br.com.felipeblaft.reports.usecase.document;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.service.DocumentGenerationService;
import br.com.felipeblaft.reports.service.OfficialDocumentService;
import br.com.felipeblaft.reports.service.ReportAccessService;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Entrega autorizada de PDFs (FR-048, R-11): primeiro verifica o dono e a associação, depois lê os
 * bytes pela chave interna. O nome sugerido não revela identificadores internos.
 */
public class GetReportDocumentUseCase {

    public record ObtainedDocument(byte[] content, String fileName) {}

    /** Fonte dos documentos de outros tipos de relatório (ex.: semestral). */
    public interface DocumentSource {
        ReportType type();

        ObtainedDocument review(UUID teacherId, UUID reportId, int number);

        ObtainedDocument official(UUID teacherId, UUID reportId);
    }

    private final ReportAccessService access;
    private final LessonReportRepository reports;
    private final ReportVersionRepository versions;
    private final PdfDocumentRepository documents;
    private final DocumentGenerationService generation;
    private final OfficialDocumentService officialDocument;
    private DocumentSource semester;

    public GetReportDocumentUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            ReportVersionRepository versions,
            PdfDocumentRepository documents,
            DocumentGenerationService generation,
            OfficialDocumentService officialDocument) {
        this.access = Objects.requireNonNull(access);
        this.reports = Objects.requireNonNull(reports);
        this.versions = Objects.requireNonNull(versions);
        this.documents = Objects.requireNonNull(documents);
        this.generation = Objects.requireNonNull(generation);
        this.officialDocument = Objects.requireNonNull(officialDocument);
    }

    public void registerSemesterSource(DocumentSource font) {
        this.semester = font;
    }

    public ObtainedDocument review(UUID teacherId, ReportType type, UUID reportId, int number) {
        if (type == ReportType.SEMESTER) {
            return semesterSource().review(teacherId, reportId, number);
        }
        LessonReport report = access.lessonReport(teacherId, reportId);
        String name = lessonName(report, "-v" + number);
        return read(report.id(), number, DocumentType.REVIEW, name);
    }

    public ObtainedDocument official(UUID teacherId, ReportType type, UUID reportId) {
        if (type == ReportType.SEMESTER) {
            return semesterSource().official(teacherId, reportId);
        }
        LessonReport report = access.lessonReport(teacherId, reportId);
        if (report.status() != ReportStatus.APPROVED) {
            throw notFound();
        }
        LocalDate lessonDate = reports.searchLesson(report.lessonId()).orElseThrow().lessonDate();
        byte[] bytes =
                officialDocument
                        .readOfficial(ReportType.LESSON, report.id(), report.approvedVersion())
                        .orElseThrow(GetReportDocumentUseCase::notFound);
        return new ObtainedDocument(bytes, OfficialDocumentService.lessonFileName(lessonDate));
    }

    private String lessonName(LessonReport report, String suffix) {
        LocalDate data = reports.searchLesson(report.lessonId()).orElseThrow().lessonDate();
        return "relatorio-aula-" + data + suffix + ".pdf";
    }

    private ObtainedDocument read(UUID reportId, int number, DocumentType type, String name) {
        Optional<PdfDocument> document =
                versions.search(ReportType.LESSON, reportId, number)
                        .flatMap(v -> documents.search(v.id(), type));
        byte[] bytes =
                document.flatMap(generation::open).orElseThrow(GetReportDocumentUseCase::notFound);
        return new ObtainedDocument(bytes, name);
    }

    private DocumentSource semesterSource() {
        if (semester == null) {
            throw notFound();
        }
        return semester;
    }

    private static DomainException notFound() {
        return new DomainException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
