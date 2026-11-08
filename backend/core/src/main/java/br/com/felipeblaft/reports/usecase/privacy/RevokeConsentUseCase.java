package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentPolicy;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.privacy.GuardianConsent;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConsentRepository;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Registra a revogação de um consentimento ({@code CONSENT_REVOKE}, FR-033): só o administrador.
 * Daí em diante, o aluno menor fica sem consentimento válido e a IA, bloqueada. O registro continua
 * no histórico; quem revogou entra só por papel, sem nome.
 */
public class RevokeConsentUseCase {

    public record Command(
            UUID consentId, LocalDate revokedOn, String revokedBy, String correlationId) {

        @Override
        public String toString() {
            return "RevokeConsentUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final ConsentRepository consents;
    private final ConsentTermRepository terms;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public RevokeConsentUseCase(
            ActorAuthorizationService authorization,
            ConsentRepository consents,
            ConsentTermRepository terms,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.consents = Objects.requireNonNull(consents);
        this.terms = Objects.requireNonNull(terms);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    public ConsentView run(Actor actor, Command command) {
        authorization.require(actor, Operation.CONSENT_REVOKE);
        GuardianConsent current =
                consents.findById(command.consentId())
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        GuardianConsent revoked =
                current.revoke(
                        command.revokedOn(),
                        command.revokedBy(),
                        actor.id(),
                        clock.todayInSchoolTimezone());
        consents.save(revoked);
        authorship.record(
                actor,
                Operation.CONSENT_REVOKE,
                AuthorshipTargetType.CONSENT,
                revoked.id(),
                command.correlationId());
        int floor = ConsentTermVersion.compatibleFrom(terms.listAll());
        return ConsentView.of(revoked, ConsentPolicy.recordStatus(revoked, floor));
    }
}
