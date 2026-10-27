package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.inbound.web.security.AdminSessionFilter;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedAdministrator;
import br.com.felipeblaft.reports.usecase.registry.AssignTeacherToStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.PreviewAssignmentRemovalUseCase;
import br.com.felipeblaft.reports.usecase.registry.RemoveTeacherFromStudentUseCase;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Associações aluno-professor na área administrativa (FR-028, FR-029). Sem regra de negócio: o
 * acesso do professor cai na hora e nenhum relatório é alterado, tudo decidido no núcleo.
 */
@RestController
@RequestMapping("/api/v1/admin/students/{studentId}/teachers/{teacherId}")
public class AdminAssignmentController {

    private static final String CORRELATION = AdminSessionFilter.CORRELATION_ATTRIBUTE;

    private final AssignTeacherToStudentUseCase assign;
    private final RemoveTeacherFromStudentUseCase remove;
    private final PreviewAssignmentRemovalUseCase preview;

    public AdminAssignmentController(
            AssignTeacherToStudentUseCase assign,
            RemoveTeacherFromStudentUseCase remove,
            PreviewAssignmentRemovalUseCase preview) {
        this.assign = assign;
        this.remove = remove;
        this.preview = preview;
    }

    public record AdminRemovalPreviewResponse(int frozenReports) {}

    @PutMapping
    public ResponseEntity<Void> assign(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID studentId,
            @PathVariable UUID teacherId,
            @RequestAttribute(CORRELATION) String correlationId) {
        assign.run(
                admin.actor(),
                new AssignTeacherToStudentUseCase.Command(studentId, teacherId, correlationId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID studentId,
            @PathVariable UUID teacherId,
            @RequestAttribute(CORRELATION) String correlationId) {
        remove.run(
                admin.actor(),
                new RemoveTeacherFromStudentUseCase.Command(studentId, teacherId, correlationId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/removal-preview")
    public AdminRemovalPreviewResponse removalPreview(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @PathVariable UUID studentId,
            @PathVariable UUID teacherId) {
        var result =
                preview.run(
                        admin.actor(),
                        new PreviewAssignmentRemovalUseCase.Command(studentId, teacherId));
        return new AdminRemovalPreviewResponse(result.frozenReports());
    }
}
