package br.com.felipeblaft.reports.usecase.administrator;

import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.PasswordHashPort;
import br.com.felipeblaft.reports.service.AccessAttemptsControlService;
import br.com.felipeblaft.reports.service.AuthorshipService;

/**
 * Ativação da conta com e-mail, código de ativação e a nova senha ({@code ADMIN_ACTIVATE}, pública,
 * bloqueio {@code ADMIN_ACTIVATION}). Mensagem única {@code INVALID_ACTIVATION} para e-mail
 * desconhecido, código errado, vencido ou já usado. Esqueleto da Fundação; a regra é do fluxo W-A.
 */
public class ActivateAdministratorUseCase {

    public record Command(
            String email,
            Secret activationCode,
            Secret newPassword,
            String origin,
            String correlationId) {

        @Override
        public String toString() {
            return "ActivateAdministratorUseCase.Command";
        }
    }

    public ActivateAdministratorUseCase(
            AdministratorRepository administrators,
            DataProtectionPort protection,
            PasswordHashPort passwords,
            AccessAttemptsControlService attempts,
            AuthorshipService authorship,
            ClockPort clock) {}

    public void run(Command command) {
        throw new UnsupportedOperationException("ActivateAdministratorUseCase: fluxo W-A");
    }
}
