package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.privacy.ConsentPolicy;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.privacy.GuardianConsent;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConsentRepository;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Alunos menores ativos sem consentimento válido e o motivo ({@code CONSENT_PENDING_READ}, FR-035):
 * ausente, revogado ou versão do termo superada. Em operação normal a lista fica vazia. O nome do
 * aluno só aparece nesta tela do administrador.
 */
public class ListPendingConsentsUseCase {

    public record Pending(UUID studentId, String studentName, ConsentStatus reason) {

        @Override
        public String toString() {
            return "ListPendingConsentsUseCase.Pending";
        }
    }

    private final ActorAuthorizationService authorization;
    private final StudentAccessService access;
    private final ConsentRepository consents;
    private final ConsentTermRepository terms;
    private final ClockPort clock;

    public ListPendingConsentsUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            ConsentRepository consents,
            ConsentTermRepository terms,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.access = Objects.requireNonNull(access);
        this.consents = Objects.requireNonNull(consents);
        this.terms = Objects.requireNonNull(terms);
        this.clock = Objects.requireNonNull(clock);
    }

    public PageResult<Pending> run(Actor actor, PageRequest page) {
        authorization.require(actor, Operation.CONSENT_PENDING_READ);
        return PageResult.slice(statusOfMinors(actor, false), page);
    }

    /**
     * Quantos alunos menores ativos têm consentimento válido hoje: são os que ficam pendentes se
     * for publicado um termo que exige novo consentimento (mostrado na confirmação da publicação).
     */
    public int countValid(Actor actor) {
        authorization.require(actor, Operation.CONSENT_PENDING_READ);
        return statusOfMinors(actor, true).size();
    }

    private List<Pending> statusOfMinors(Actor actor, boolean valid) {
        LocalDate today = clock.todayInSchoolTimezone();
        List<Student> minors =
                access.list(actor, StudentStatusFilter.ACTIVE).stream()
                        .filter(student -> ConsentPolicy.required(student.birthDate(), today))
                        .toList();
        Map<UUID, List<GuardianConsent>> history =
                consents.findByStudents(minors.stream().map(Student::id).toList());
        int floor = ConsentTermVersion.compatibleFrom(terms.listAll());
        List<Pending> pending = new ArrayList<>();
        for (Student student : minors) {
            ConsentStatus status =
                    ConsentPolicy.status(history.getOrDefault(student.id(), List.of()), floor);
            if ((status == ConsentStatus.VALID) == valid) {
                pending.add(new Pending(student.id(), student.name(), status));
            }
        }
        return pending;
    }
}
