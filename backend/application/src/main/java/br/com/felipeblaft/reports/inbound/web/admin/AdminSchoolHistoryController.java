package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.history.SchoolHistoryFilter;
import br.com.felipeblaft.reports.inbound.web.PdfResponses;
import br.com.felipeblaft.reports.inbound.web.admin.AdminSchoolHistoryDtos.SchoolHistoryPage;
import br.com.felipeblaft.reports.inbound.web.security.AdminSessionFilter;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedAdministrator;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import br.com.felipeblaft.reports.usecase.document.GetSchoolReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.document.GetSchoolReportDocumentUseCase.Disposition;
import br.com.felipeblaft.reports.usecase.history.QuerySchoolHistoryUseCase;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Visão da escola na área administrativa (feature 006, somente leitura): relatórios aprovados de
 * qualquer professor e aluno e o PDF oficial da versão aprovada. Sem regra de negócio: autorização,
 * consulta e registro de autoria são do núcleo. Não existe rota de escrita nem por número de
 * versão. Toda resposta leva {@code Cache-Control: no-store, private}.
 */
@RestController
@RequestMapping("/api/v1/admin/school-history")
public class AdminSchoolHistoryController {

    private static final String CORRELATION = AdminSessionFilter.CORRELATION_ATTRIBUTE;

    private final QuerySchoolHistoryUseCase query;
    private final GetSchoolReportDocumentUseCase document;
    private final MessageFormatting formatting;

    public AdminSchoolHistoryController(
            QuerySchoolHistoryUseCase query,
            GetSchoolReportDocumentUseCase document,
            MessageFormatting formatting) {
        this.query = query;
        this.document = document;
        this.formatting = formatting;
    }

    @GetMapping
    public ResponseEntity<SchoolHistoryPage> history(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestAttribute(CORRELATION) String correlationId) {
        // forma dos valores aqui (data, tipo, página); a recusa vai à autoria pelo caso de uso, que
        // também confere a ordem do período
        var actor = admin.actor();
        SchoolHistoryFilter.Syntax filter;
        try {
            filter =
                    SchoolHistoryFilter.parseSyntax(
                            studentId, teacherId, type, from, to, page, size);
        } catch (DomainException refusal) {
            query.recordSyntaxRefusal(actor, studentId, correlationId);
            throw refusal;
        }
        var result =
                query.run(
                        actor,
                        new QuerySchoolHistoryUseCase.Command(
                                filter.studentId(),
                                filter.teacherId(),
                                filter.type(),
                                filter.from(),
                                filter.to(),
                                filter.page(),
                                correlationId));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(AdminSchoolHistoryDtos.page(result, formatting));
    }

    @GetMapping("/lesson-reports/{reportId}/pdf")
    public ResponseEntity<byte[]> openLesson(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID reportId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return PdfResponses.inline(
                deliver(admin, ReportType.LESSON, reportId, Disposition.OPEN, correlationId));
    }

    @GetMapping("/lesson-reports/{reportId}/pdf/download")
    public ResponseEntity<byte[]> downloadLesson(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID reportId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return PdfResponses.attachment(
                deliver(admin, ReportType.LESSON, reportId, Disposition.DOWNLOAD, correlationId));
    }

    @GetMapping("/semester-reports/{reportId}/pdf")
    public ResponseEntity<byte[]> openSemester(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID reportId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return PdfResponses.inline(
                deliver(admin, ReportType.SEMESTER, reportId, Disposition.OPEN, correlationId));
    }

    @GetMapping("/semester-reports/{reportId}/pdf/download")
    public ResponseEntity<byte[]> downloadSemester(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID reportId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return PdfResponses.attachment(
                deliver(admin, ReportType.SEMESTER, reportId, Disposition.DOWNLOAD, correlationId));
    }

    private ObtainedDocument deliver(
            AuthenticatedAdministrator admin,
            ReportType type,
            UUID reportId,
            Disposition disposition,
            String correlationId) {
        return document.run(
                admin.actor(),
                new GetSchoolReportDocumentUseCase.Command(
                        type, reportId, disposition, correlationId));
    }
}
