package br.com.felipeblaft.reports.usecase.access;

import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import java.util.Objects;

/** "Encerrar sessão": apaga a sessão no servidor (FR-008); a próxima requisição recebe 401. */
public class EndWebSessionUseCase {

    private final WebSessionRepository sessions;
    private final DataProtectionPort protection;

    public EndWebSessionUseCase(WebSessionRepository sessions, DataProtectionPort protection) {
        this.sessions = Objects.requireNonNull(sessions);
        this.protection = Objects.requireNonNull(protection);
    }

    public void run(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessions.searchByTokenHash(protection.sessionTokenHash(token))
                .ifPresent(session -> sessions.remove(session.id()));
    }
}
