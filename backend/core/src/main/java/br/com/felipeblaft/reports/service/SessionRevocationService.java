package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.SessionRevocationReason;
import br.com.felipeblaft.reports.port.AdministratorSessionRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.WebSessionRepository;
import java.util.UUID;

/**
 * Revogação "na hora" das sessões do professor e do administrador (R-08): marca as sessões com o
 * motivo, sem removê-las, para que a próxima requisição receba a resposta certa. Esqueleto da
 * Fundação; a regra é do fluxo W-B.
 */
public class SessionRevocationService {

    public SessionRevocationService(
            WebSessionRepository teacherSessions,
            AdministratorSessionRepository administratorSessions,
            ClockPort clock) {}

    /** Revoga as sessões Web do professor; devolve quantas. */
    public int revokeTeacher(UUID teacherId, SessionRevocationReason reason) {
        throw new UnsupportedOperationException("SessionRevocationService: fluxo W-B");
    }

    /** Revoga as sessões do administrador; devolve quantas. */
    public int revokeAdministrator(UUID administratorId, SessionRevocationReason reason) {
        throw new UnsupportedOperationException("SessionRevocationService: fluxo W-B");
    }
}
