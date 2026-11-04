package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import java.util.Objects;

/**
 * Versão vigente do termo ({@code CONSENT_TERM_READ}): texto institucional, sem dado de aluno,
 * legível pelos dois papéis (o professor precisa dela no cadastro do menor). Sem termo publicado,
 * {@code RESOURCE_NOT_FOUND}.
 */
public class GetCurrentConsentTermUseCase {

    private final ActorAuthorizationService authorization;
    private final ConsentTermRepository terms;

    public GetCurrentConsentTermUseCase(
            ActorAuthorizationService authorization, ConsentTermRepository terms) {
        this.authorization = Objects.requireNonNull(authorization);
        this.terms = Objects.requireNonNull(terms);
    }

    public ConsentTermVersion run(Actor actor) {
        authorization.require(actor, Operation.CONSENT_TERM_READ);
        return ConsentTermVersion.current(terms.listAll())
                .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
