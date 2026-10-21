package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import java.util.Objects;
import java.util.UUID;

/**
 * Revogação "na hora" das sessões do professor e do administrador (R-08): marca as sessões com o
 * motivo, sem removê-las, para que a próxima requisição receba a resposta certa.
 */
public class SessionRevocationService {

    private final WebSessionRepository teacherSessions;
    private final AdministratorSessionRepository administratorSessions;
    private final ClockPort clock;

    public SessionRevocationService(
            WebSessionRepository teacherSessions,
            AdministratorSessionRepository administratorSessions,
            ClockPort clock) {
        this.teacherSessions = Objects.requireNonNull(teacherSessions);
        this.administratorSessions = Objects.requireNonNull(administratorSessions);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Revoga as sessões Web do professor; devolve quantas. */
    public int revokeTeacher(UUID teacherId, SessionRevocationReason reason) {
        return teacherSessions.revokeAllOf(teacherId, reason, clock.now());
    }

    /** Revoga as sessões do administrador; devolve quantas. */
    public int revokeAdministrator(UUID administratorId, SessionRevocationReason reason) {
        return administratorSessions.revokeAllOf(administratorId, reason, clock.now());
    }
}
