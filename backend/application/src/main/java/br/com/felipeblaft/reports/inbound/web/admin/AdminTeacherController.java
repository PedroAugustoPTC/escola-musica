package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminTeacherDtos.AdminReissueCodeRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminTeacherDtos.AdminTeacherRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminTeacherDtos.AdminTeacherResponse;
import br.com.felipeblaft.reports.inbound.web.admin.AdminTeacherDtos.AdminTeacherWithCodeResponse;
import br.com.felipeblaft.reports.inbound.web.security.AdminSessionFilter;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedAdministrator;
import br.com.felipeblaft.reports.usecase.teacher.GetTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.InactivateTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.ListTeachersUseCase;
import br.com.felipeblaft.reports.usecase.teacher.ReactivateTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.RegisterTeacherUseCase;
import br.com.felipeblaft.reports.usecase.teacher.ReissueAccessCodeUseCase;
import br.com.felipeblaft.reports.usecase.teacher.UnlinkTeacherTelegramUseCase;
import br.com.felipeblaft.reports.usecase.teacher.UpdateTeacherUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Professores na área administrativa (FR-010 a FR-015, FR-030, FR-031). Sem regra de negócio: o
 * ator vem da sessão, cada rota chama um caso de uso e o código de acesso só sai na resposta da
 * operação que o gerou.
 */
@RestController
@RequestMapping("/api/v1/admin/teachers")
public class AdminTeacherController {

    private static final String CORRELATION = AdminSessionFilter.CORRELATION_ATTRIBUTE;

    private final ListTeachersUseCase list;
    private final GetTeacherUseCase get;
    private final RegisterTeacherUseCase register;
    private final UpdateTeacherUseCase update;
    private final InactivateTeacherUseCase inactivate;
    private final ReactivateTeacherUseCase reactivate;
    private final ReissueAccessCodeUseCase reissue;
    private final UnlinkTeacherTelegramUseCase unlink;

    @SuppressWarnings("java:S107")
    public AdminTeacherController(
            ListTeachersUseCase list,
            GetTeacherUseCase get,
            RegisterTeacherUseCase register,
            UpdateTeacherUseCase update,
            InactivateTeacherUseCase inactivate,
            ReactivateTeacherUseCase reactivate,
            ReissueAccessCodeUseCase reissue,
            UnlinkTeacherTelegramUseCase unlink) {
        this.list = list;
        this.get = get;
        this.register = register;
        this.update = update;
        this.inactivate = inactivate;
        this.reactivate = reactivate;
        this.reissue = reissue;
        this.unlink = unlink;
    }

    @GetMapping
    public AdminPageResponse<AdminTeacherResponse> list(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return AdminTeacherDtos.page(
                list.run(
                        admin.actor(),
                        new ListTeachersUseCase.Command(
                                search, active, PageRequest.of(page, size))));
    }

    @PostMapping
    public ResponseEntity<AdminTeacherWithCodeResponse> register(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @Valid @RequestBody AdminTeacherRequest request,
            @RequestAttribute(CORRELATION) String correlationId) {
        var result =
                register.run(
                        admin.actor(),
                        new RegisterTeacherUseCase.Command(
                                request.name(), request.email(), correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminTeacherDtos.withCode(result));
    }

    @GetMapping("/{teacherId}")
    public AdminTeacherResponse get(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID teacherId) {
        return AdminTeacherDtos.teacher(
                get.run(admin.actor(), new GetTeacherUseCase.Command(teacherId)));
    }

    @PatchMapping("/{teacherId}")
    public AdminTeacherResponse update(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID teacherId,
            @Valid @RequestBody AdminTeacherRequest request,
            @RequestAttribute(CORRELATION) String correlationId) {
        return AdminTeacherDtos.teacher(
                update.run(
                        admin.actor(),
                        new UpdateTeacherUseCase.Command(
                                teacherId, request.name(), request.email(), correlationId)));
    }

    @PostMapping("/{teacherId}/inactivation")
    public AdminTeacherResponse inactivate(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID teacherId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return AdminTeacherDtos.teacher(
                inactivate.run(
                        admin.actor(),
                        new InactivateTeacherUseCase.Command(teacherId, correlationId)));
    }

    @PostMapping("/{teacherId}/reactivation")
    public AdminTeacherWithCodeResponse reactivate(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID teacherId,
            @RequestAttribute(CORRELATION) String correlationId) {
        return AdminTeacherDtos.withCode(
                reactivate.run(
                        admin.actor(),
                        new ReactivateTeacherUseCase.Command(teacherId, correlationId)));
    }

    @PostMapping("/{teacherId}/access-code")
    public AdminTeacherWithCodeResponse reissueAccessCode(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID teacherId,
            @Valid @RequestBody AdminReissueCodeRequest request,
            @RequestAttribute(CORRELATION) String correlationId) {
        return AdminTeacherDtos.withCode(
                reissue.run(
                        admin.actor(),
                        new ReissueAccessCodeUseCase.Command(
                                teacherId, request.endTelegramLink(), correlationId)));
    }

    @DeleteMapping("/{teacherId}/telegram-link")
    public ResponseEntity<Void> unlinkTelegram(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID teacherId,
            @RequestAttribute(CORRELATION) String correlationId) {
        unlink.run(
                admin.actor(), new UnlinkTeacherTelegramUseCase.Command(teacherId, correlationId));
        return ResponseEntity.noContent().build();
    }
}
