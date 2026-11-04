package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipReadDetail;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.audit.AuditEventType;
import br.com.felipeblaft.reports.domain.privacy.ConsentInput;
import br.com.felipeblaft.reports.domain.privacy.ConsentPolicy;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.privacy.GuardianConsent;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConsentRepository;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import java.util.Objects;
import java.util.UUID;

/**
 * Valida e grava um consentimento e o evento {@code CONSENT_RECORD}, na mesma transação de quem o
 * chamou. É o único caminho de gravação: o cadastro, a edição, o registro avulso e a carga
 * inicial usam o mesmo, nos dois canais (FR-031, FR-081).
 */
public class ConsentRecordingService {

    private final ConsentRepository consents;
    private final ConsentTermRepository terms;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public ConsentRecordingService(
            ConsentRepository consents,
            ConsentTermRepository terms,
            AuthorshipService authorship,
            ClockPort clock) {
        this.consents = Objects.requireNonNull(consents);
        this.terms = Objects.requireNonNull(terms);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Situação atual do consentimento do aluno (histórico contra a versão compatível do termo). */
    public ConsentStatus status(UUID studentId) {
        int floor = ConsentTermVersion.compatibleFrom(terms.listAll());
        return ConsentPolicy.status(consents.findByStudent(studentId), floor);
    }

    /**
     * Valida a entrada sem gravar nada (todas as violações em {@code consent.*}): o cadastro e a
     * edição conferem o consentimento antes de gravar o aluno.
     */
    public void check(ConsentInput input) {
        GuardianConsent.of(
                input,
                UUID.randomUUID(),
                terms.listAll(),
                clock.todayInSchoolTimezone(),
                clock.now(),
                null,
                null,
                ActionOrigin.WEB);
    }

    /** Valida a entrada (todas as violações em {@code consent.*}), grava e registra o evento. */
    public GuardianConsent record(
            Actor actor, UUID studentId, ConsentInput input, String correlationId) {
        GuardianConsent consent =
                GuardianConsent.of(
                        input,
                        studentId,
                        terms.listAll(),
                        clock.todayInSchoolTimezone(),
                        clock.now(),
                        actor.id(),
                        actor.role(),
                        actor.origin());
        consents.save(consent);
        authorship.recordEvent(
                actor,
                AuditEventType.CONSENT_RECORD.name(),
                AuthorshipTargetType.CONSENT,
                consent.id(),
                AuthorshipReadDetail.ofConsentStudent(studentId),
                AuthorshipResult.SUCCESS,
                null,
                correlationId);
        return consent;
    }
}
