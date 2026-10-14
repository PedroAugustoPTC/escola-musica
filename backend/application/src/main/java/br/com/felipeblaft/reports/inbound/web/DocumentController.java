package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedTeacher;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    @GetMapping("/relatorios-aula/{relatorioId}/versoes/{numero}/pdf")
    public ResponseEntity<byte[]> lessonReview(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID relatorioId,
            @PathVariable int numero) {
        return pdf(getDocument.review(teacher.id(), ReportType.AULA, relatorioId, numero));
    }

    @GetMapping("/relatorios-aula/{relatorioId}/pdf-oficial")
    public ResponseEntity<byte[]> lessonOfficial(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID relatorioId) {
        return pdf(getDocument.official(teacher.id(), ReportType.AULA, relatorioId));
    }

    @GetMapping("/relatorios-semestrais/{relatorioId}/versoes/{numero}/pdf")
    public ResponseEntity<byte[]> semesterReview(
            @AuthenticationPrincipal AuthenticatedTeacher teacher,
            @PathVariable UUID relatorioId,
            @PathVariable int numero) {
        return pdf(getDocument.review(teacher.id(), ReportType.SEMESTRAL, relatorioId, numero));
    }

    @GetMapping("/relatorios-semestrais/{relatorioId}/pdf-oficial")
    public ResponseEntity<byte[]> semesterOfficial(
            @AuthenticationPrincipal AuthenticatedTeacher teacher, @PathVariable UUID relatorioId) {
        return pdf(getDocument.official(teacher.id(), ReportType.SEMESTRAL, relatorioId));
    }

    static ResponseEntity<byte[]> pdf(ObtainedDocument document) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(document.fileName())
                                .build()
                                .toString())
                .body(document.content());
    }
}
