package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.privacy.ConsentPolicy;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.port.ConsentRepository;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Histórico de consentimentos de um aluno, do mais recente ao mais antigo, com a situação de cada
 * registro ({@code CONSENT_READ}, só o administrador).
 */
public class ListStudentConsentsUseCase {

    private final ActorAuthorizationService authorization;
    private final StudentAccessService access;
    private final ConsentRepository consents;
    private final ConsentTermRepository terms;

    public ListStudentConsentsUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            ConsentRepository consents,
            ConsentTermRepository terms) {
        this.authorization = Objects.requireNonNull(authorization);
        this.access = Objects.requireNonNull(access);
        this.consents = Objects.requireNonNull(consents);
        this.terms = Objects.requireNonNull(terms);
    }

    public List<ConsentView> run(Actor actor, UUID studentId) {
        authorization.require(actor, Operation.CONSENT_READ);
        access.findVisible(actor, studentId);
        int floor = ConsentTermVersion.compatibleFrom(terms.listAll());
        return consents.findByStudent(studentId).stream()
                .map(c -> ConsentView.of(c, ConsentPolicy.recordStatus(c, floor)))
                .toList();
    }
}
