package br.com.felipeblaft.reports.usecase.access;

import br.com.felipeblaft.reports.domain.access.WebSession;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import br.com.felipeblaft.reports.service.AccessRenewalService;
import java.time.Instant;
import java.util.Objects;

/**
 * Valida a sessão da Web a cada requisição (FR-008): expiração deslizante e revogação real no
 * servidor. Cada uso renova a sessão e a validade do código (FR-005).
 */
public class ValidateSessionWebUseCase {

    public record Result(Teacher teacher, WebSession session) {}

    private final WebSessionRepository sessions;
    private final TeacherRepository teachers;
    private final AccessRenewalService renewal;
    private final DataProtectionPort protection;
    private final ClockPort clock;
    private final BusinessParameters parameters;

    public ValidateSessionWebUseCase(
            WebSessionRepository sessions,
            TeacherRepository teachers,
            AccessRenewalService renewal,
            DataProtectionPort protection,
            ClockPort clock,
            BusinessParameters parameters) {
        this.sessions = Objects.requireNonNull(sessions);
        this.teachers = Objects.requireNonNull(teachers);
        this.renewal = Objects.requireNonNull(renewal);
        this.protection = Objects.requireNonNull(protection);
        this.clock = Objects.requireNonNull(clock);
        this.parameters = Objects.requireNonNull(parameters);
    }

    public Result run(String token) {
        if (token == null || token.isBlank()) {
            throw new DomainException(ErrorCode.NAO_AUTENTICADO);
        }
        Instant now = clock.now();
        WebSession session =
                sessions.searchByTokenHash(protection.sessionTokenHash(token))
                        .orElseThrow(() -> new DomainException(ErrorCode.SESSAO_EXPIRADA));
        if (!session.validAt(now)) {
            sessions.remove(session.id());
            throw new DomainException(ErrorCode.SESSAO_EXPIRADA);
        }
        Teacher teacher =
                teachers.searchById(session.teacherId()).filter(Teacher::active).orElse(null);
        if (teacher == null) {
            sessions.remove(session.id());
            throw new DomainException(ErrorCode.SESSAO_EXPIRADA);
        }
        if (session.registerUse(now, parameters.webSessionInactivity())) {
            sessions.save(session);
        }
        renewal.renew(teacher);
        return new Result(teacher, session);
    }
}
