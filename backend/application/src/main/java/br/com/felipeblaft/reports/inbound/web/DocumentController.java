package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedTeacher;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrega autorizada dos PDFs (R-11, FR-048): sempre por rota do relatório, depois de conferir dono
 * e associação; o nome sugerido não revela identificadores internos e nada fica em cache.
 */
@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final GetReportDocumentUseCase getDocument;

    public DocumentController(GetReportDocumentUseCase getDocument) {
        this.getDocument = getDocument;
    }

    @GetMapping("/lesson-reports/{reportId}/versions/{number}/pdf")
    public ResponseEntity<byte[]> lessonReview(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID reportId,
            @PathVariable int number) {
        return pdf(getDocument.review(teacher.id(), ReportType.LESSON, reportId, number));
    }

    @GetMapping("/lesson-reports/{reportId}/official-pdf")
    public ResponseEntity<byte[]> lessonOfficial(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID reportId) {
        return pdf(getDocument.official(teacher.id(), ReportType.LESSON, reportId));
    }

    @GetMapping("/semester-reports/{reportId}/versions/{number}/pdf")
    public ResponseEntity<byte[]> semesterReview(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID reportId,
            @PathVariable int number) {
        return pdf(getDocument.review(teacher.id(), ReportType.SEMESTER, reportId, number));
    }

    @GetMapping("/semester-reports/{reportId}/official-pdf")
    public ResponseEntity<byte[]> semesterOfficial(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID reportId) {
        return pdf(getDocument.official(teacher.id(), ReportType.SEMESTER, reportId));
    }

    static ResponseEntity<byte[]> pdf(ObtainedDocument document) {
        return PdfResponses.inline(document);
    }
}
