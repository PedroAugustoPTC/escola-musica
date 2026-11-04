package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ConsentInput;
import br.com.felipeblaft.reports.domain.privacy.ConsentPolicy;
import br.com.felipeblaft.reports.domain.privacy.ConsentStatus;
import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.privacy.GuardianConsent;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.ConsentRecordingService;
import br.com.felipeblaft.reports.service.StudentAccessService;
import java.util.Objects;
import java.util.UUID;

/**
 * Registra o consentimento do responsável por um aluno ({@code CONSENT_RECORD}): o administrador
 * para qualquer aluno, o professor (Web e Telegram) para os próprios e a carga inicial (FR-031,
 * FR-036). Cada registro é novo; o anterior fica como histórico.
 */
public class RecordConsentUseCase {

    public record Command(UUID studentId, ConsentInput input, String correlationId) {

        @Override
        public String toString() {
            return "RecordConsentUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final StudentAccessService access;
    private final ConsentRecordingService recording;
    private final ConsentTermRepository terms;
    private final AuthorshipService authorship;

    public RecordConsentUseCase(
            ActorAuthorizationService authorization,
            StudentAccessService access,
            ConsentRecordingService recording,
            ConsentTermRepository terms,
            AuthorshipService authorship) {
        this.authorization = Objects.requireNonNull(authorization);
        this.access = Objects.requireNonNull(access);
        this.recording = Objects.requireNonNull(recording);
        this.terms = Objects.requireNonNull(terms);
        this.authorship = Objects.requireNonNull(authorship);
    }

    public ConsentView run(Actor actor, Command command) {
        authorization.require(actor, Operation.CONSENT_RECORD);
        Student student = findVisible(actor, command);
        GuardianConsent consent =
                recording.record(actor, student.id(), command.input(), command.correlationId());
        int floor = ConsentTermVersion.compatibleFrom(terms.listAll());
        ConsentStatus status = ConsentPolicy.recordStatus(consent, floor);
        return ConsentView.of(consent, status);
    }

    private Student findVisible(Actor actor, Command command) {
        try {
            return access.findVisible(actor, command.studentId());
        } catch (DomainException error) {
            if (error.code() == ErrorCode.RESOURCE_NOT_FOUND && actor.role() == Role.TEACHER) {
                authorship.record(
                        actor,
                        AuthorshipRecord.AUTHORIZATION_DENIED,
                        AuthorshipTargetType.STUDENT,
                        command.studentId(),
                        AuthorshipResult.DENIED,
                        ErrorCode.RESOURCE_NOT_FOUND.name(),
                        command.correlationId());
            }
            throw error;
        }
    }
}
