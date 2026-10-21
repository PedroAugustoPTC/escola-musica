package br.com.felipeblaft.reports.usecase.access;

import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.WebSession;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.GeneratorId;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import br.com.felipeblaft.reports.service.AccessRenewalService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.CodeAuthenticationService;
import br.com.felipeblaft.reports.service.TeacherAccessAuditService;
import java.util.Objects;

/**
 * Entrar no portal com o código de acesso (FR-004, FR-007, FR-008). Deixa o registro de autoria da
 * entrada, com sucesso ou falha, e do bloqueio (FR-040); a regra de acesso não muda.
 */
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
    private final TeacherAccessAuditService audit;
    private final ClockPort clock;
    private final BusinessParameters parameters;

    public SignInPortalUseCase(
            CodeAuthenticationService authentication,
            AccessRenewalService renewal,
            WebSessionRepository sessions,
            TelegramLinkRepository links,
            DataProtectionPort protection,
            AuthorshipService authorship,
            ClockPort clock,
            BusinessParameters parameters) {
        this.authentication = Objects.requireNonNull(authentication);
        this.renewal = Objects.requireNonNull(renewal);
        this.sessions = Objects.requireNonNull(sessions);
        this.links = Objects.requireNonNull(links);
        this.protection = Objects.requireNonNull(protection);
        this.audit = new TeacherAccessAuditService(authorship, protection);
        this.clock = Objects.requireNonNull(clock);
        this.parameters = Objects.requireNonNull(parameters);
    }

    /**
     * {@code origin} é o endereço de quem tenta entrar (só o hash é guardado); {@code
     * correlationId} é o identificador da requisição, para o registro de autoria.
     */
    public Result run(String code, String origin, String correlationId) {
        String event = Operation.TEACHER_SIGN_IN.name();
        Teacher teacher =
                audit.authenticate(authentication, Channel.WEB, origin, code, event, correlationId);
        renewal.renew(teacher);
        // sem registro de autoria, a entrada não vale: a sessão só nasce depois dele
        audit.succeeded(Channel.WEB, origin, teacher, event, correlationId);
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

    /** Sem identificador de correlação do chamador: gera um para o registro de autoria. */
    public Result run(String code, String origin) {
        return run(code, origin, GeneratorId.create().toString());
    }
}
