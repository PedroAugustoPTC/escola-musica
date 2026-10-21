package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.common.Secret;
import java.time.Instant;
import java.util.UUID;

/**
 * Sessão aberta para o administrador (entrada e troca da senha provisória): o token vai só ao
 * cookie. A validade é fixa e não é renovada. O {@code toString} não revela dado pessoal.
 */
public record AdministratorSessionResult(
        UUID administratorId,
        String name,
        Secret sessionToken,
        Instant expiresAt,
        boolean mustChangePassword) {

    @Override
    public String toString() {
        return "AdministratorSessionResult[administratorId=" + administratorId + "]";
    }
}
