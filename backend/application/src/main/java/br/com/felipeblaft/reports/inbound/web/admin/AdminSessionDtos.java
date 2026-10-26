package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.usecase.administrator.AdministratorSessionResult;
import br.com.felipeblaft.reports.usecase.administrator.GetAdministratorSessionUseCase;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * DTOs e conversões da sessão do administrador (contracts/api-administration.openapi.yaml). Só
 * forma e tamanho; a validação de regra é do núcleo. Os pedidos com segredo não os revelam no
 * {@code toString}.
 */
public final class AdminSessionDtos {

    private AdminSessionDtos() {}

    public record AdminSignInRequest(
            @NotNull @Size(max = 254) String email, @NotNull @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "AdminSignInRequest";
        }
    }

    public record AdminActivationRequest(
            @NotNull @Size(max = 254) String email,
            @NotNull @Size(max = 20) String activationCode,
            @NotNull @Size(max = 128) String newPassword) {

        @Override
        public String toString() {
            return "AdminActivationRequest";
        }
    }

    public record AdminChangePasswordRequest(@NotNull @Size(max = 128) String newPassword) {

        @Override
        public String toString() {
            return "AdminChangePasswordRequest";
        }
    }

    public record AdminSummaryResponse(UUID id, String name) {

        @Override
        public String toString() {
            return "AdminSummaryResponse[id=" + id + "]";
        }
    }

    public record AdminSessionResponse(
            AdminSummaryResponse administrator, Instant expiresAt, boolean mustChangePassword) {

        @Override
        public String toString() {
            return "AdminSessionResponse[expiresAt=" + expiresAt + "]";
        }
    }

    static Secret secret(String value) {
        return new Secret(value == null ? "" : value);
    }

    static AdminSessionResponse session(AdministratorSessionResult result) {
        return new AdminSessionResponse(
                new AdminSummaryResponse(result.administratorId(), result.name()),
                result.expiresAt(),
                result.mustChangePassword());
    }

    static AdminSessionResponse session(GetAdministratorSessionUseCase.Result result) {
        return new AdminSessionResponse(
                new AdminSummaryResponse(result.administratorId(), result.name()),
                result.expiresAt(),
                result.mustChangePassword());
    }
}
