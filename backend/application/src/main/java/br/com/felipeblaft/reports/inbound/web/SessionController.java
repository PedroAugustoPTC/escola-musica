package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.inbound.web.security.AuthenticatedTeacher;
import br.com.felipeblaft.reports.inbound.web.security.RequestOriginResolver;
import br.com.felipeblaft.reports.inbound.web.security.SessionCookie;
import br.com.felipeblaft.reports.usecase.access.EndWebSessionUseCase;
import br.com.felipeblaft.reports.usecase.access.QueryTelegramLinkUseCase;
import br.com.felipeblaft.reports.usecase.access.SignInPortalUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Entrar, consultar e encerrar a sessão do portal (FR-004, FR-007, FR-008). */
@RestController
@RequestMapping("/api/v1/session")
public class SessionController {

    private final SignInPortalUseCase signIn;
    private final EndWebSessionUseCase end;
    private final QueryTelegramLinkUseCase queryLink;
    private final SessionCookie cookie;
    private final RequestOriginResolver origin;

    public SessionController(
            SignInPortalUseCase signIn,
            EndWebSessionUseCase end,
            QueryTelegramLinkUseCase queryLink,
            SessionCookie cookie,
            RequestOriginResolver origin) {
        this.signIn = signIn;
        this.end = end;
        this.queryLink = queryLink;
        this.cookie = cookie;
        this.origin = origin;
    }

    @PostMapping
    public ResponseEntity<SessionDto> signIn(
            @Valid @RequestBody SignInRequest request, HttpServletRequest http) {
        SignInPortalUseCase.Result result = signIn.run(request.code(), origin.resolver(http));
        SessionDto body =
                new SessionDto(
                        HomeMapper.teacher(result.teacher()),
                        result.session().expiresAt(),
                        result.telegramLinked());
        return ResponseEntity.ok()
                .header(SessionCookie.header(), cookie.create(result.token()))
                .body(body);
    }

    @GetMapping
    public SessionDto current(@AuthenticationPrincipal AuthenticatedTeacher teacher) {
        return new SessionDto(
                HomeMapper.teacher(teacher.teacher()),
                teacher.sessionExpiresAt(),
                queryLink.run(teacher.id()));
    }

    @DeleteMapping
    public ResponseEntity<Void> end(HttpServletRequest http) {
        end.run(cookie.read(http));
        return ResponseEntity.noContent().header(SessionCookie.header(), cookie.remove()).build();
    }
}
