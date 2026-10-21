package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.SessionRevocationService;
import java.time.Duration;

/**
 * Troca obrigatória da senha provisória do primeiro administrador ({@code ADMIN_CHANGE_PASSWORD}):
 * a nova senha segue a política, difere da provisória, revoga as outras sessões e abre uma sessão
 * nova sem a restrição. Esqueleto da Fundação; a regra é do fluxo W-A.
 */
public class ChangeProvisionalPasswordUseCase {

    public record Command(Secret newPassword, String correlationId) {

        @Override
        public String toString() {
            return "ChangeProvisionalPasswordUseCase.Command";
        }
    }

    public ChangeProvisionalPasswordUseCase(
            ActorAuthorizationService authorization,
            AdministratorRepository administrators,
            AdministratorSessionRepository sessions,
            SessionRevocationService revocation,
            PasswordHashPort passwords,
            DataProtectionPort protection,
            AuthorshipService authorship,
            ClockPort clock,
            Duration sessionDuration) {}

    public AdministratorSessionResult run(Actor actor, Command command) {
        throw new UnsupportedOperationException("ChangeProvisionalPasswordUseCase: fluxo W-A");
    }
}
