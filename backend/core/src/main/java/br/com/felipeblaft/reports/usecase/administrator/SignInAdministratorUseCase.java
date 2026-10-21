package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.Duration;

/**
 * Entrada do administrador com e-mail e senha (operação {@code ADMIN_SIGN_IN}, pública). Mesma
 * mensagem e mesmo tempo para e-mail inexistente, senha errada e conta inativa; bloqueio por
 * origem; id de sessão novo a cada entrada. Esqueleto da Fundação; a regra é do fluxo W-A.
 */
public class SignInAdministratorUseCase {

    /** {@code origin} é o endereço visto pelo proxy; só o seu resumo é guardado. */
    public record Command(String email, Secret password, String origin, String correlationId) {

        @Override
        public String toString() {
            return "SignInAdministratorUseCase.Command";
        }
    }

    public SignInAdministratorUseCase(
            AdministratorRepository administrators,
            AdministratorSessionRepository sessions,
            PasswordHashPort passwords,
            DataProtectionPort protection,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock,
            Duration sessionDuration) {}

    public AdministratorSessionResult run(Command command) {
        throw new UnsupportedOperationException("SignInAdministratorUseCase: fluxo W-A");
    }
}
