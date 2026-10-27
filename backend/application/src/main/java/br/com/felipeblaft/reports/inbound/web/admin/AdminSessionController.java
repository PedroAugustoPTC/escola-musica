package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.inbound.web.admin.AdminSessionDtos.AdminActivationRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminSessionDtos.AdminChangePasswordRequest;
import br.com.felipeblaft.reports.inbound.web.admin.AdminSessionDtos.AdminSessionResponse;
import br.com.felipeblaft.reports.inbound.web.admin.AdminSessionDtos.AdminSignInRequest;
import br.com.felipeblaft.reports.inbound.web.security.AdminSessionCookie;
import br.com.felipeblaft.reports.inbound.web.security.AdminSessionFilter;
import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedAdministrator;
import br.com.felipeblaft.reports.inbound.web.security.RequestOriginResolver;
import br.com.felipeblaft.reports.usecase.administrator.ActivateAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.AdministratorSessionResult;
import br.com.felipeblaft.reports.usecase.administrator.ChangeProvisionalPasswordUseCase;
import br.com.felipeblaft.reports.usecase.administrator.GetAdministratorSessionUseCase;
import br.com.felipeblaft.reports.usecase.administrator.SignInAdministratorUseCase;
import br.com.felipeblaft.reports.usecase.administrator.SignOutAdministratorUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sessão do administrador: entrar, consultar, sair, ativar a conta e trocar a senha provisória
 * (FR-004 a FR-006, FR-032, FR-034a). Só traduz HTTP para os casos de uso; o cookie é emitido aqui
 * e a origem vem do resolvedor, nunca de um cabeçalho do cliente.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminSessionController {

    private final SignInAdministratorUseCase signIn;
    private final GetAdministratorSessionUseCase getSession;
    private final SignOutAdministratorUseCase signOut;
    private final ActivateAdministratorUseCase activate;
    private final ChangeProvisionalPasswordUseCase changePassword;
    private final AdminSessionCookie cookie;
    private final RequestOriginResolver origin;

    public AdminSessionController(
            SignInAdministratorUseCase signIn,
            GetAdministratorSessionUseCase getSession,
            SignOutAdministratorUseCase signOut,
            ActivateAdministratorUseCase activate,
            ChangeProvisionalPasswordUseCase changePassword,
            AdminSessionCookie cookie,
            RequestOriginResolver origin) {
        this.signIn = signIn;
        this.getSession = getSession;
        this.signOut = signOut;
        this.activate = activate;
        this.changePassword = changePassword;
        this.cookie = cookie;
        this.origin = origin;
    }

    @PostMapping("/session")
    public ResponseEntity<AdminSessionResponse> signIn(
            @Valid @RequestBody AdminSignInRequest request,
            HttpServletRequest http,
            @RequestAttribute(AdminSessionFilter.CORRELATION_ATTRIBUTE) String correlationId) {
        AdministratorSessionResult result =
                signIn.run(
                        new SignInAdministratorUseCase.Command(
                                request.email(),
                                AdminSessionDtos.secret(request.password()),
                                origin.resolver(http),
                                correlationId));
        return opened(result);
    }

    @GetMapping("/session")
    public AdminSessionResponse current(@AuthenticationPrincipal AuthenticatedAdministrator admin) {
        return AdminSessionDtos.session(
                getSession.run(
                        admin.actor(), new GetAdministratorSessionUseCase.Command(admin.token())));
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> end(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @RequestAttribute(AdminSessionFilter.CORRELATION_ATTRIBUTE) String correlationId) {
        signOut.run(
                admin.actor(),
                new SignOutAdministratorUseCase.Command(admin.token(), correlationId));
        return ResponseEntity.noContent()
                .header(AdminSessionCookie.header(), cookie.remove())
                .build();
    }

    @PostMapping("/activation")
    public ResponseEntity<Void> activate(
            @Valid @RequestBody AdminActivationRequest request,
            HttpServletRequest http,
            @RequestAttribute(AdminSessionFilter.CORRELATION_ATTRIBUTE) String correlationId) {
        activate.run(
                new ActivateAdministratorUseCase.Command(
                        request.email(),
                        AdminSessionDtos.secret(request.activationCode()),
                        AdminSessionDtos.secret(request.newPassword()),
                        origin.resolver(http),
                        correlationId));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/password")
    public ResponseEntity<AdminSessionResponse> changePassword(
            @AuthenticationPrincipal AuthenticatedAdministrator admin,
            @Valid @RequestBody AdminChangePasswordRequest request,
            @RequestAttribute(AdminSessionFilter.CORRELATION_ATTRIBUTE) String correlationId) {
        AdministratorSessionResult result =
                changePassword.run(
                        admin.actor(),
                        new ChangeProvisionalPasswordUseCase.Command(
                                AdminSessionDtos.secret(request.newPassword()), correlationId));
        return opened(result);
    }

    private ResponseEntity<AdminSessionResponse> opened(AdministratorSessionResult result) {
        return ResponseEntity.ok()
                .header(AdminSessionCookie.header(), cookie.create(result.sessionToken().value()))
                .body(AdminSessionDtos.session(result));
    }
}
