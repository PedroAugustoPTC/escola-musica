package br.com.felipeblaft.reports.usecase.access;

import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import br.com.felipeblaft.reports.service.TeacherReportAuditService;
import java.util.Objects;

/** "Encerrar sessão": apaga a sessão no servidor (FR-008); a próxima requisição recebe 401. */
public class EndWebSessionUseCase {

    private final WebSessionRepository sessions;
    private final DataProtectionPort protection;
    private final TeacherReportAuditService audit;

    public EndWebSessionUseCase(
            WebSessionRepository sessions,
            DataProtectionPort protection,
            TeacherReportAuditService audit) {
        this.sessions = Objects.requireNonNull(sessions);
        this.protection = Objects.requireNonNull(protection);
        this.audit = Objects.requireNonNull(audit);
    }

    public void run(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessions.searchByTokenHash(protection.sessionTokenHash(token))
                .ifPresent(
                        session -> {
                            sessions.remove(session.id());
                            audit.signedOut(session.teacherId());
                        });
    }
}
