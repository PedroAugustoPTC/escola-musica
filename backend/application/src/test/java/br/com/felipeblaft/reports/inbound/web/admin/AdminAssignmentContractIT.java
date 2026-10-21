package br.com.felipeblaft.reports.inbound.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.usecase.registry.AssignTeacherToStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.PreviewAssignmentRemovalUseCase;
import br.com.felipeblaft.reports.usecase.registry.RemoveTeacherFromStudentUseCase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Contrato das associações aluno-professor (FR-028, FR-029): {@code PUT} idempotente, {@code
 * DELETE} nunca bloqueado e a prévia de relatórios congelados.
 */
class AdminAssignmentContractIT extends AdminWebBase {

    private static final UUID STUDENT = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID TEACHER = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final String URI = BASE + "/students/" + STUDENT + "/teachers/" + TEACHER;

    @Test
    void assignReturns204AndPassesBothIds() throws Exception {
        mvc.perform(asAdmin(put(URI)))
                .andExpect(status().isNoContent())
                .andExpect(followsContract())
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        ArgumentCaptor<AssignTeacherToStudentUseCase.Command> command =
                ArgumentCaptor.forClass(AssignTeacherToStudentUseCase.Command.class);
        verify(assignTeacher).run(any(), command.capture());
        assertThat(command.getValue().studentId()).isEqualTo(STUDENT);
        assertThat(command.getValue().teacherId()).isEqualTo(TEACHER);
        assertThat(command.getValue().correlationId()).isNotBlank();
    }

    @Test
    void assignAgainIsStillNoContent() throws Exception {
        mvc.perform(asAdmin(put(URI))).andExpect(status().isNoContent());
        mvc.perform(asAdmin(put(URI))).andExpect(status().isNoContent());
    }

    @Test
    void assigningAnInactiveTeacherIs409() throws Exception {
        doThrow(new DomainException(ErrorCode.TEACHER_INACTIVE))
                .when(assignTeacher)
                .run(any(), any());

        mvc.perform(asAdmin(put(URI)))
                .andExpect(status().isConflict())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("TEACHER_INACTIVE"))
                .andExpect(
                        jsonPath("$.message")
                                .value("Este professor está inativo. Reative-o antes."));
    }

    @Test
    void unknownStudentOrTeacherIs404() throws Exception {
        doThrow(new DomainException(ErrorCode.RESOURCE_NOT_FOUND))
                .when(assignTeacher)
                .run(any(), any());

        mvc.perform(asAdmin(put(URI)))
                .andExpect(status().isNotFound())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void removeReturns204() throws Exception {
        mvc.perform(asAdmin(delete(URI)))
                .andExpect(status().isNoContent())
                .andExpect(followsContract());

        ArgumentCaptor<RemoveTeacherFromStudentUseCase.Command> command =
                ArgumentCaptor.forClass(RemoveTeacherFromStudentUseCase.Command.class);
        verify(removeTeacher).run(any(), command.capture());
        assertThat(command.getValue().studentId()).isEqualTo(STUDENT);
        assertThat(command.getValue().teacherId()).isEqualTo(TEACHER);
    }

    @Test
    void removalPreviewReturnsTheFrozenReportCount() throws Exception {
        given(previewRemoval.run(any(), any()))
                .willReturn(new PreviewAssignmentRemovalUseCase.Result(3));

        mvc.perform(get(URI + "/removal-preview").cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(followsContract())
                .andExpect(jsonPath("$.frozenReports").value(3))
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        ArgumentCaptor<PreviewAssignmentRemovalUseCase.Command> command =
                ArgumentCaptor.forClass(PreviewAssignmentRemovalUseCase.Command.class);
        verify(previewRemoval).run(any(), command.capture());
        assertThat(command.getValue().studentId()).isEqualTo(STUDENT);
        assertThat(command.getValue().teacherId()).isEqualTo(TEACHER);
    }

    @Test
    void previewWithoutAssociationIs404() throws Exception {
        given(previewRemoval.run(any(), any()))
                .willThrow(new DomainException(ErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(get(URI + "/removal-preview").cookie(adminCookie(ADMIN_TOKEN)))
                .andExpect(status().isNotFound())
                .andExpect(followsContract());
    }
}
