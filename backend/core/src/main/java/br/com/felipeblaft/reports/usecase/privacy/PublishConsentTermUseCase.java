package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Publica uma nova versão do termo ({@code CONSENT_TERM_PUBLISH}, FR-032): só o administrador. O
 * número é sequencial; a versão é imutável; se exigir novo consentimento, os consentimentos de
 * versões anteriores deixam de valer.
 */
public class PublishConsentTermUseCase {

    public record Command(
            LocalDate publishedOn, String text, boolean requiresNewConsent, String correlationId) {

        @Override
        public String toString() {
            return "PublishConsentTermUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final ConsentTermRepository terms;
    private final AuthorshipService authorship;
    private final ClockPort clock;

    public PublishConsentTermUseCase(
            ActorAuthorizationService authorization,
            ConsentTermRepository terms,
            AuthorshipService authorship,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.terms = Objects.requireNonNull(terms);
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
    }

    public ConsentTermVersion run(Actor actor, Command command) {
        authorization.require(actor, Operation.CONSENT_TERM_PUBLISH);
        ConsentTermVersion term =
                ConsentTermVersion.publish(
                        ConsentTermVersion.current(terms.listAll()),
                        command.publishedOn(),
                        command.text(),
                        command.requiresNewConsent(),
                        actor.id(),
                        clock.todayInSchoolTimezone(),
                        clock.now());
        terms.save(term);
        authorship.record(
                actor,
                Operation.CONSENT_TERM_PUBLISH,
                AuthorshipTargetType.CONSENT_TERM,
                term.id(),
                command.correlationId());
        return term;
    }
}
