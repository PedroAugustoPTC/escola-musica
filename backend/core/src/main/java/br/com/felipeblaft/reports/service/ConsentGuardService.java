package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.audit.AuditEventType;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentPolicy;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConsentRepository;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.port.RequestContextPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import java.util.Objects;
import java.util.UUID;

/**
 * Barreira de consentimento (FR-034, SC-005): aluno menor sem consentimento válido não chega a
 * nenhum provedor de IA. Vale para quem já foi autorizado, depois da autorização e antes de
 * qualquer chamada, áudio ou trabalho; aprovar e cancelar relatório existente não passam por aqui.
 * O pedido recusado deixa o evento {@code CONSENT_BLOCKED_AI} (em transação independente, porque a
 * operação é desfeita) e nunca revela idade, data de nascimento nem o motivo.
 */
public class ConsentGuardService {

    private final StudentRepository students;
    private final ConsentRepository consents;
    private final ConsentTermRepository terms;
    private final ClockPort clock;
    private final AuthorshipService authorship;
    private final RequestContextPort context;

    public ConsentGuardService(
            StudentRepository students,
            ConsentRepository consents,
            ConsentTermRepository terms,
            ClockPort clock,
            AuthorshipService authorship,
            RequestContextPort context) {
        this.students = Objects.requireNonNull(students);
        this.consents = Objects.requireNonNull(consents);
        this.terms = Objects.requireNonNull(terms);
        this.clock = Objects.requireNonNull(clock);
        this.authorship = Objects.requireNonNull(authorship);
        this.context = Objects.requireNonNull(context);
    }

    /** Situação do consentimento do aluno hoje. */
    public ConsentStatus statusOf(UUID studentId) {
        int floor = ConsentTermVersion.compatibleFrom(terms.listAll());
        return ConsentPolicy.status(consents.findByStudent(studentId), floor);
    }

    /**
     * Situação do consentimento exigido hoje do aluno; vazio quando ele não exige (maior de idade).
     */
    public java.util.Optional<ConsentStatus> statusIfRequired(Student student) {
        return ConsentPolicy.required(student.birthDate(), clock.todayInSchoolTimezone())
                ? java.util.Optional.of(statusOf(student.id()))
                : java.util.Optional.empty();
    }

    /**
     * A IA está bloqueada para este aluno? Aluno desconhecido não bloqueia (a autorização decide).
     */
    public boolean blocks(UUID studentId) {
        return students.searchById(studentId)
                .map(
                        student ->
                                ConsentPolicy.blocksAi(
                                        student.birthDate(),
                                        clock.todayInSchoolTimezone(),
                                        statusOf(student.id())))
                .orElse(false);
    }

    /**
     * Primeira barreira, na entrada do caso de uso: recusa com {@code CONSENT_REQUIRED} e registra
     * o evento do professor quando há requisição em curso.
     */
    public void requireForAi(UUID teacherId, UUID studentId) {
        if (!blocks(studentId)) {
            return;
        }
        if (context.active()) {
            authorship.recordEvent(
                    Actor.teacher(teacherId, context.origin()),
                    AuditEventType.CONSENT_BLOCKED_AI.name(),
                    AuthorshipTargetType.STUDENT,
                    studentId,
                    null,
                    AuthorshipResult.BLOCKED,
                    ErrorCode.CONSENT_REQUIRED.name(),
                    context.correlationId());
        }
        throw new DomainException(
                ErrorCode.CONSENT_REQUIRED, java.util.Map.of("studentId", studentId.toString()));
    }

    /**
     * Segunda barreira, no processamento: a revogação entre enfileirar e processar encerra o
     * trabalho com {@code CONSENT_REQUIRED}, sem chamar provedor.
     */
    public void requireForProcessing(Student student) {
        if (ConsentPolicy.blocksAi(
                student.birthDate(), clock.todayInSchoolTimezone(), statusOf(student.id()))) {
            throw new DomainException(
                    ErrorCode.CONSENT_REQUIRED,
                    java.util.Map.of("studentId", student.id().toString()));
        }
    }
}
