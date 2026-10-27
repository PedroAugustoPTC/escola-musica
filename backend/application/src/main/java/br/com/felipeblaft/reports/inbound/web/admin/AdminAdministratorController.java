package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminAdministratorDtos.AdminAdministratorRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminAdministratorDtos.AdminAdministratorResponse;
import br.com.felipeblaft.reports.inbound.web.admin.AdminAdministratorDtos.AdminAdministratorWithCodeResponse;
import br.com.felipeblaft.reports.inbound.web.security.AdminSessionFilter;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedAdministrator;
import br.com.felipeblaft.reports.usecase.administrator.CreateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.InactivateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.ListAdministratorsUseCase;
import br.com.felipeblaft.reports.usecase.administrator.ReactivateAdministratorUseCase;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administradores na área administrativa (FR-007, FR-032, FR-033). Sem regra de negócio: o "último
 * administrador ativo" e a revogação das sessões são do núcleo; o código de ativação só sai na
 * resposta da operação que o gerou.
 */
@RestController
@RequestMapping("/api/v1/admin/administrators")
public class AdminAdministratorController {

    private static final String CORRELATION = AdminSessionFilter.CORRELATION_ATTRIBUTE;

    private final ListAdministratorsUseCase list;
    private final CreateAdministratorUseCase create;
    private final InactivateAdministratorUseCase inactivate;
    private final ReactivateAdministratorUseCase reactivate;

    public AdminAdministratorController(
            ListAdministratorsUseCase list,
            CreateAdministratorUseCase create,
            InactivateAdministratorUseCase inactivate,
            ReactivateAdministratorUseCase reactivate) {
        this.list = list;
        this.create = create;
        this.inactivate = inactivate;
        this.reactivate = reactivate;
    }

    @GetMapping
    public AdminPageResponse<AdminAdministratorResponse> list(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return AdminAdministratorDtos.page(
                list.run(
                        admin.actor(),
                        new ListAdministratorsUseCase.Command(
                                null, active, PageRequest.of(page, size))));
    }

    @PostMapping
    public ResponseEntity<AdminAdministratorWithCodeResponse> create(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestBody AdminAdministratorRequest request,
            @RequestAttribute(CORRELATION) String correlationId) {
        var result =
                create.run(
                        admin.actor(),
                        new CreateAdministratorUseCase.Command(
                                request.name(), request.email(), correlationId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminAdministratorDtos.withCode(result));
    }

    @PostMapping("/{administratorId}/inactivation")
    public AdminAdministratorResponse inactivate(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID administratorId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return AdminAdministratorDtos.administrator(
                inactivate.run(
                        admin.actor(),
                        new InactivateAdministratorUseCase.Command(
                                administratorId, correlationId)));
    }

    @PostMapping("/{administratorId}/reactivation")
    public AdminAdministratorWithCodeResponse reactivate(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID administratorId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return AdminAdministratorDtos.withCode(
                reactivate.run(
                        admin.actor(),
                        new ReactivateAdministratorUseCase.Command(
                                administratorId, correlationId)));
    }
}
