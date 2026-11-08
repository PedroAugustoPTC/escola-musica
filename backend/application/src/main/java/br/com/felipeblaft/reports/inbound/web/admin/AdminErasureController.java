package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.inbound.web.admin.AdminErasureDtos.ConfirmRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminErasureDtos.PreviewBody;
import br.com.felipeblaft.reports.inbound.web.admin.AdminErasureDtos.ReceiptBody;
import br.com.felipeblaft.reports.inbound.web.admin.AdminErasureDtos.RegisterRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminErasureDtos.RequestBody;
import br.com.felipeblaft.reports.inbound.web.admin.AdminErasureDtos.RequestPage;
import br.com.felipeblaft.reports.inbound.web.security.AdminSessionFilter;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedAdministrator;
import br.com.felipeblaft.reports.usecase.privacy.ConfirmErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.GetErasureReceiptUseCase;
import br.com.felipeblaft.reports.usecase.privacy.ListErasureRequestsUseCase;
import br.com.felipeblaft.reports.usecase.privacy.PreviewErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RegisterErasureRequestUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Eliminação de dados a pedido (feature 007, US4; somente administrador na Web). Adaptador fino:
 * autorização, validação, atomicidade e eventos da trilha são do núcleo. Toda resposta leva {@code
 * Cache-Control: no-store, private}.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminErasureController {

    private static final String CORRELATION = AdminSessionFilter.CORRELATION_ATTRIBUTE;

    private final RegisterErasureRequestUseCase register;
    private final ListErasureRequestsUseCase list;
    private final PreviewErasureUseCase preview;
    private final ConfirmErasureUseCase confirm;
    private final GetErasureReceiptUseCase receipt;

    public AdminErasureController(
            RegisterErasureRequestUseCase register,
            ListErasureRequestsUseCase list,
            PreviewErasureUseCase preview,
            ConfirmErasureUseCase confirm,
            GetErasureReceiptUseCase receipt) {
        this.register = register;
        this.list = list;
        this.preview = preview;
        this.confirm = confirm;
        this.receipt = receipt;
    }

    @PostMapping("/students/{studentId}/erasure-requests")
    public ResponseEntity<RequestBody> register(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID studentId,
            @org.springframework.web.bind.annotation.RequestBody RegisterRequest body,
            @RequestAttribute(CORRELATION) String correlationId) {
        var created =
                register.run(
                        admin.actor(),
                        new RegisterErasureRequestUseCase.Command(
                                studentId,
                                body.requesterRole(),
                                body.requesterName(),
                                AdminConsentDtos.date(body.requestedOn(), "requestedOn"),
                                body.receivedVia(),
                                body.protocolReference(),
                                correlationId));
        return noStore(
                ResponseEntity.status(HttpStatus.CREATED)
                        .body(AdminErasureDtos.request(created, null)));
    }

    @GetMapping("/erasure-requests")
    public ResponseEntity<RequestPage> list(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestParam(required = false) ErasureStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageRequest paging = PageRequest.of(page, size);
        List<RequestBody> all =
                list.list(admin.actor(), status).stream().map(AdminErasureDtos::request).toList();
        int from = (int) Math.min(paging.offset(), all.size());
        int to = Math.min(from + paging.size(), all.size());
        return noStore(
                ResponseEntity.ok(
                        new RequestPage(
                                all.subList(from, to), paging.page(), paging.size(), all.size())));
    }

    @GetMapping("/erasure-requests/{requestId}")
    public ResponseEntity<RequestBody> get(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID requestId) {
        return noStore(
                ResponseEntity.ok(AdminErasureDtos.request(list.get(admin.actor(), requestId))));
    }

    @PostMapping("/erasure-requests/{requestId}/preview")
    public ResponseEntity<PreviewBody> preview(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID requestId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return noStore(
                ResponseEntity.ok(
                        AdminErasureDtos.preview(preview.run(admin.actor(), requestId, correlationId))));
    }

    @PostMapping("/erasure-requests/{requestId}/confirmation")
    public ResponseEntity<RequestBody> confirm(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID requestId,
            @org.springframework.web.bind.annotation.RequestBody ConfirmRequest body,
            @RequestAttribute(CORRELATION) String correlationId) {
        var done =
                confirm.run(
                        admin.actor(),
                        new ConfirmErasureUseCase.Command(
                                requestId,
                                body.confirmationToken(),
                                body.confirmationWord(),
                                correlationId));
        return noStore(ResponseEntity.ok(AdminErasureDtos.request(done, null)));
    }

    @GetMapping("/erasure-requests/{requestId}/receipt")
    public ResponseEntity<ReceiptBody> receipt(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID requestId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return noStore(
                ResponseEntity.ok(
                        AdminErasureDtos.receipt(receipt.run(admin.actor(), requestId, correlationId))));
    }

    private static <T> ResponseEntity<T> noStore(ResponseEntity<T> response) {
        return ResponseEntity.status(response.getStatusCode())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(response.getBody());
    }
}
