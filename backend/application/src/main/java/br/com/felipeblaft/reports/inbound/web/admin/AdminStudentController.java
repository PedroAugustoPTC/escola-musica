package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.inbound.web.admin.AdminStudentDtos.AdminStudentRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminStudentDtos.AdminStudentResponse;
import br.com.felipeblaft.reports.inbound.web.security.AdminSessionFilter;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedAdministrator;
import br.com.felipeblaft.reports.usecase.registry.GetStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.InactivateStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.ListStudentsUseCase;
import br.com.felipeblaft.reports.usecase.registry.ReactivateStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.RegisterStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.UpdateStudentUseCase;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alunos de toda a escola na área administrativa (FR-017 a FR-023). Sem regra de negócio: validação
 * de CPF, datas, responsável e unicidade são do núcleo; o CPF só entra no corpo do pedido e nunca
 * volta, nem em URL, cabeçalho ou log.
 */
@RestController
@RequestMapping("/api/v1/admin/students")
public class AdminStudentController {

    private static final String CORRELATION = AdminSessionFilter.CORRELATION_ATTRIBUTE;

    private final ListStudentsUseCase list;
    private final GetStudentUseCase get;
    private final RegisterStudentUseCase register;
    private final UpdateStudentUseCase update;
    private final InactivateStudentUseCase inactivate;
    private final ReactivateStudentUseCase reactivate;

    public AdminStudentController(
            ListStudentsUseCase list,
            GetStudentUseCase get,
            RegisterStudentUseCase register,
            UpdateStudentUseCase update,
            InactivateStudentUseCase inactivate,
            ReactivateStudentUseCase reactivate) {
        this.list = list;
        this.get = get;
        this.register = register;
        this.update = update;
        this.inactivate = inactivate;
        this.reactivate = reactivate;
    }

    @GetMapping
    public AdminPageResponse<AdminStudentResponse> list(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(required = false) StudentStatusFilter status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return AdminStudentDtos.page(
                list.run(
                        admin.actor(),
                        new ListStudentsUseCase.Command(
                                search,
                                status == null ? StudentStatusFilter.ACTIVE : status,
                                PageRequest.of(page, size))));
    }

    @PostMapping
    public ResponseEntity<AdminStudentResponse> register(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestBody AdminStudentRequest request,
            @RequestAttribute(CORRELATION) String correlationId) {
        List<UUID> teacherIds = request.teacherIds() == null ? List.of() : request.teacherIds();
        var student =
                register.run(
                        admin.actor(),
                        new RegisterStudentUseCase.Command(
                                AdminStudentDtos.input(request), teacherIds, correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminStudentDtos.student(student));
    }

    @GetMapping("/{studentId}")
    public AdminStudentResponse get(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID studentId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return AdminStudentDtos.student(
                get.run(admin.actor(), new GetStudentUseCase.Command(studentId, correlationId)));
    }

    @PatchMapping("/{studentId}")
    public AdminStudentResponse update(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID studentId,
            @RequestBody AdminStudentRequest request,
            @RequestAttribute(CORRELATION) String correlationId) {
        return AdminStudentDtos.student(
                update.run(
                        admin.actor(),
                        new UpdateStudentUseCase.Command(
                                studentId, AdminStudentDtos.input(request), correlationId)));
    }

    @PostMapping("/{studentId}/inactivation")
    public AdminStudentResponse inactivate(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID studentId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return AdminStudentDtos.student(
                inactivate.run(
                        admin.actor(),
                        new InactivateStudentUseCase.Command(studentId, correlationId)));
    }

    @PostMapping("/{studentId}/reactivation")
    public AdminStudentResponse reactivate(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID studentId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return AdminStudentDtos.student(
                reactivate.run(
                        admin.actor(),
                        new ReactivateStudentUseCase.Command(studentId, correlationId)));
    }
}
