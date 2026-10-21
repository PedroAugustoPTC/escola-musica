package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.usecase.teacher.TeacherView;
import br.com.felipeblaft.reports.usecase.teacher.TeacherWithCode;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** DTOs e conversões dos professores da área administrativa. */
public final class AdminTeacherDtos {

    private AdminTeacherDtos() {}

    /** Nome e e-mail; o núcleo valida tamanho, formato e unicidade. */
    public record AdminTeacherRequest(String name, String email) {

        @Override
        public String toString() {
            return "AdminTeacherRequest";
        }
    }

    public record AdminReissueCodeRequest(@NotNull Boolean endTelegramLink) {}

    public record AdminTeacherResponse(
            UUID id, String name, String email, boolean active, boolean telegramLinked) {}

    /** O código de acesso só existe nesta resposta (FR-011). */
    public record AdminTeacherWithCodeResponse(
            AdminTeacherResponse teacher, String accessCode, Instant validUntil) {

        @Override
        public String toString() {
            return "AdminTeacherWithCodeResponse";
        }
    }

    static AdminTeacherResponse teacher(TeacherView view) {
        return new AdminTeacherResponse(
                view.id(), view.name(), view.email(), view.active(), view.telegramLinked());
    }

    static AdminTeacherWithCodeResponse withCode(TeacherWithCode result) {
        return new AdminTeacherWithCodeResponse(
                teacher(result.teacher()), result.accessCode().value(), result.validUntil());
    }

    static AdminPageResponse<AdminTeacherResponse> page(PageResult<TeacherView> result) {
        return new AdminPageResponse<>(
                result.items().stream().map(AdminTeacherDtos::teacher).toList(),
                result.page(),
                result.size(),
                result.total());
    }
}
