package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.util.List;
import java.util.Objects;

/** Todas as versões do termo, da mais recente à mais antiga ({@code CONSENT_TERM_READ}). */
public class ListConsentTermsUseCase {

    private final ActorAuthorizationService authorization;
    private final ConsentTermRepository terms;

    public ListConsentTermsUseCase(
            ActorAuthorizationService authorization, ConsentTermRepository terms) {
        this.authorization = Objects.requireNonNull(authorization);
        this.terms = Objects.requireNonNull(terms);
    }

    public List<ConsentTermVersion> run(Actor actor) {
        authorization.require(actor, Operation.CONSENT_TERM_READ);
        return terms.listAll();
    }
}
