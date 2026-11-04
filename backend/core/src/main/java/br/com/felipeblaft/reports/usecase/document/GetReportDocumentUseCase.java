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
import br.com.felipeblaft.reports.service.TeacherReportAuditService;
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
    private final TeacherReportAuditService audit;
    private DocumentSource semester;

    public GetReportDocumentUseCase(
            ReportAccessService access,
            LessonReportRepository reports,
            ReportVersionRepository versions,
            PdfDocumentRepository documents,
            DocumentGenerationService generation,
            OfficialDocumentService officialDocument,
            TeacherReportAuditService audit) {
        this.access = Objects.requireNonNull(access);
        this.reports = Objects.requireNonNull(reports);
        this.versions = Objects.requireNonNull(versions);
        this.documents = Objects.requireNonNull(documents);
        this.generation = Objects.requireNonNull(generation);
        this.officialDocument = Objects.requireNonNull(officialDocument);
        this.audit = Objects.requireNonNull(audit);
    }

    public void registerSemesterSource(DocumentSource font) {
        this.semester = font;
    }

    public ObtainedDocument review(UUID teacherId, ReportType type, UUID reportId, int number) {
        return review(teacherId, type, reportId, number, false);
    }

    /** {@code download} diferencia baixar de abrir na Web; no Telegram o evento é de envio. */
    public ObtainedDocument review(
            UUID teacherId, ReportType type, UUID reportId, int number, boolean download) {
        ObtainedDocument document = prepareReview(teacherId, type, reportId, number);
        audit.documentRead(teacherId, type, reportId, number, download);
        return document;
    }

    /**
     * Lê o PDF de conferência sem gravar evento: o Telegram o monta ao responder, mas só a entrega
     * do documento (resgate do token) é o envio que a trilha registra, uma vez.
     */
    public ObtainedDocument prepareReview(
            UUID teacherId, ReportType type, UUID reportId, int number) {
        if (type == ReportType.SEMESTER) {
            return semesterSource().review(teacherId, reportId, number);
        }
        LessonReport report = access.lessonReport(teacherId, reportId);
        String name = lessonName(report, "-v" + number);
        return read(report.id(), number, DocumentType.REVIEW, name);
    }

    public ObtainedDocument official(UUID teacherId, ReportType type, UUID reportId) {
        return official(teacherId, type, reportId, false);
    }

    /** {@code download} diferencia baixar de abrir na Web; no Telegram o evento é de envio. */
    public ObtainedDocument official(
            UUID teacherId, ReportType type, UUID reportId, boolean download) {
        OfficialRead read = readOfficial(teacherId, type, reportId);
        audit.documentRead(teacherId, type, reportId, read.version(), download);
        return read.document();
    }

    /** Lê o PDF oficial sem gravar evento (ver {@link #prepareReview}). */
    public ObtainedDocument prepareOfficial(UUID teacherId, ReportType type, UUID reportId) {
        return readOfficial(teacherId, type, reportId).document();
    }

    private record OfficialRead(ObtainedDocument document, Integer version) {}

    private OfficialRead readOfficial(UUID teacherId, ReportType type, UUID reportId) {
        if (type == ReportType.SEMESTER) {
            return new OfficialRead(semesterSource().official(teacherId, reportId), null);
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
        return new OfficialRead(
                new ObtainedDocument(bytes, OfficialDocumentService.lessonFileName(lessonDate)),
                report.approvedVersion());
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
