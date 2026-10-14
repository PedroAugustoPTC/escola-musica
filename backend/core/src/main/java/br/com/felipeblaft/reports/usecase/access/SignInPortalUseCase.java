package br.com.felipeblaft.reports.usecase.access;

import br.com.felipeblaft.reports.domain.access.WebSession;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import br.com.felipeblaft.reports.service.AccessRenewalService;
import br.com.felipeblaft.reports.service.CodeAuthenticationService;
import java.util.Objects;

/** Entrar no portal com o código de acesso (FR-004, FR-007, FR-008). */
public class SignInPortalUseCase {

    /** O token em claro só existe aqui, para ir ao cookie; no banco fica o hash. */
    public record Result(
            String token, WebSession session, Teacher teacher, boolean telegramLinked) {

        @Override
        public String toString() {
            return "Result[session=" + session.id() + "]";
        }
    }

    private final CodeAuthenticationService authentication;
    private final AccessRenewalService renewal;
    private final WebSessionRepository sessions;
    private final TelegramLinkRepository links;
    private final DataProtectionPort protection;
    private final ClockPort clock;
    private final BusinessParameters parameters;

    public SignInPortalUseCase(
            CodeAuthenticationService authentication,
            AccessRenewalService renewal,
            WebSessionRepository sessions,
            TelegramLinkRepository links,
            DataProtectionPort protection,
            ClockPort clock,
            BusinessParameters parameters) {
        this.authentication = Objects.requireNonNull(authentication);
        this.renewal = Objects.requireNonNull(renewal);
        this.sessions = Objects.requireNonNull(sessions);
        this.links = Objects.requireNonNull(links);
        this.protection = Objects.requireNonNull(protection);
        this.clock = Objects.requireNonNull(clock);
        this.parameters = Objects.requireNonNull(parameters);
    }

    /** {@code origem} é o endereço de quem tenta entrar (só o hash é guardado). */
    public Result run(String code, String origin) {
        Teacher teacher = authentication.authenticate(Channel.WEB, origin, code);
        renewal.renew(teacher);
        String token = protection.generateSessionToken();
        WebSession session =
                WebSession.create(
                        teacher.id(),
                        protection.sessionTokenHash(token),
                        clock.now(),
                        parameters.webSessionInactivity());
        sessions.save(session);
        boolean telegram = links.searchByTeacherId(teacher.id()).isPresent();
        return new Result(token, session, teacher, telegram);
    }
}
