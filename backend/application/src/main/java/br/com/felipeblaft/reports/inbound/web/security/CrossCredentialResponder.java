package br.com.felipeblaft.reports.inbound.web.security;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Secret;
import br.com.felipeblaft.reports.usecase.access.ValidateSessionWebUseCase;
import br.com.felipeblaft.reports.usecase.administrator.ValidateAdministratorSessionUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Credencial cruzada (FR-003, R-19): a requisição que chega com a credencial válida do
 * <em>outro</em> papel, e sem credencial própria, recebe a resposta de rota inexistente, com o
 * mesmo status, o mesmo corpo e os mesmos cabeçalhos do 404 padrão (nada revela que a rota existe
 * para outro papel). Sem credencial alguma, a resposta continua sendo 401. Cada cadeia só aceita o
 * seu cookie; esta classe apenas decide se o cookie do outro papel estava presente e valia.
 */
@Component
public class CrossCredentialResponder {

    private final ValidateSessionWebUseCase validateTeacher;
    private final ValidateAdministratorSessionUseCase validateAdministrator;
    private final SessionCookie teacherCookie;
    private final AdminSessionCookie administratorCookie;
    private final MessageCatalog catalog;

    public CrossCredentialResponder(
            ValidateSessionWebUseCase validateTeacher,
            ValidateAdministratorSessionUseCase validateAdministrator,
            SessionCookie teacherCookie,
            AdminSessionCookie administratorCookie,
            MessageCatalog catalog) {
        this.validateTeacher = validateTeacher;
        this.validateAdministrator = validateAdministrator;
        this.teacherCookie = teacherCookie;
        this.administratorCookie = administratorCookie;
        this.catalog = catalog;
    }

    /** O professor tem sessão válida (usado nas rotas administrativas sem credencial própria). */
    public boolean validTeacherCredential(HttpServletRequest request) {
        String token = teacherCookie.read(request);
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            validateTeacher.run(token);
            return true;
        } catch (DomainException error) {
            return false;
        }
    }

    /** O administrador tem sessão válida (usado nas rotas do professor sem credencial própria). */
    public boolean validAdministratorCredential(HttpServletRequest request) {
        String token = administratorCookie.read(request);
        if (token == null) {
            return false;
        }
        try {
            validateAdministrator.run(new Secret(token));
            return true;
        } catch (DomainException error) {
            return false;
        }
    }

    /** Escreve o 404 {@code RESOURCE_NOT_FOUND} idêntico ao de rota inexistente. */
    public void writeNotFound(HttpServletResponse response) throws IOException {
        SecurityResponses.write(
                response, ErrorCode.RESOURCE_NOT_FOUND, HttpServletResponse.SC_NOT_FOUND, catalog);
    }
}
