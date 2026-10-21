package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.usecase.administrator.AdministratorView;
import br.com.felipeblaft.reports.usecase.administrator.AdministratorWithCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** DTOs e conversões dos administradores da área administrativa. */
public final class AdminAdministratorDtos {

    private AdminAdministratorDtos() {}

    public record AdminAdministratorRequest(String name, String email) {

        @Override
        public String toString() {
            return "AdminAdministratorRequest";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdminAdministratorResponse(
            UUID id,
            String name,
            String email,
            boolean active,
            String activationState,
            Instant activatedAt) {}

    /** O código de ativação só existe nesta resposta (FR-011). */
    public record AdminAdministratorWithCodeResponse(
            AdminAdministratorResponse administrator, String activationCode, Instant validUntil) {

        @Override
        public String toString() {
            return "AdminAdministratorWithCodeResponse";
        }
    }

    static AdminAdministratorResponse administrator(AdministratorView view) {
        return new AdminAdministratorResponse(
                view.id(),
                view.name(),
                view.email(),
                view.active(),
                view.activationState().name(),
                view.activatedAt());
    }

    static AdminAdministratorWithCodeResponse withCode(AdministratorWithCode result) {
        return new AdminAdministratorWithCodeResponse(
                administrator(result.administrator()),
                result.activationCode().value(),
                result.validUntil());
    }

    static AdminPageResponse<AdminAdministratorResponse> page(
            PageResult<AdministratorView> result) {
        return new AdminPageResponse<>(
                result.items().stream().map(AdminAdministratorDtos::administrator).toList(),
                result.page(),
                result.size(),
                result.total());
    }
}
