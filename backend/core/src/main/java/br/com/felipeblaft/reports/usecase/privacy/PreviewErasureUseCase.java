package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureScope;
import br.com.felipeblaft.reports.domain.privacy.PrivacyOfficer;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.ErasureRequestRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.ErasureScopeService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Mostra o alcance da eliminação e emite o token de confirmação, válido por 10 minutos ({@code
 * ERASURE_PREVIEW}, FR-021). Cada prévia invalida a anterior; só o resumo do token é gravado.
 * Também serve para retomar um pedido {@code IN_PROGRESS}.
 */
public class PreviewErasureUseCase {

    /** Professor associado, mostrado ao administrador (nome da escola, não dado do aluno). */
    public record TeacherView(UUID id, String name) {}

    /** O token só existe aqui, em claro; o {@code toString} não o revela. */
    public record Preview(
            ErasureScope scope,
            List<TeacherView> teachers,
            String confirmationToken,
            Instant expiresAt,
            PrivacyOfficer privacyContact) {

        @Override
        public String toString() {
            return "PreviewErasureUseCase.Preview";
        }
    }

    private final ActorAuthorizationService authorization;
    private final ErasureRequestRepository erasures;
    private final ErasureScopeService scopes;
    private final AssignmentRepository assignments;
    private final TeacherRepository teachers;
    private final DataProtectionPort protection;
    private final PrivacyOfficer privacyContact;
    private final AuthorshipService authorship;
    private final TransactionPort transaction;
    private final ClockPort clock;

    @SuppressWarnings("java:S107")
    public PreviewErasureUseCase(
            ActorAuthorizationService authorization,
            ErasureRequestRepository erasures,
            ErasureScopeService scopes,
            AssignmentRepository assignments,
            TeacherRepository teachers,
            DataProtectionPort protection,
            PrivacyOfficer privacyContact,
            AuthorshipService authorship,
            TransactionPort transaction,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.erasures = Objects.requireNonNull(erasures);
        this.scopes = Objects.requireNonNull(scopes);
        this.assignments = Objects.requireNonNull(assignments);
        this.teachers = Objects.requireNonNull(teachers);
        this.protection = Objects.requireNonNull(protection);
        this.privacyContact = Objects.requireNonNull(privacyContact);
        this.authorship = Objects.requireNonNull(authorship);
        this.transaction = Objects.requireNonNull(transaction);
        this.clock = Objects.requireNonNull(clock);
    }

    public Preview run(Actor actor, UUID requestId, String correlationId) {
        authorization.require(actor, Operation.ERASURE_PREVIEW);
        return transaction.run(
                () -> {
                    ErasureRequest request =
                            erasures.findForUpdate(requestId)
                                    .orElseThrow(
                                            () ->
                                                    new DomainException(
                                                            ErrorCode.RESOURCE_NOT_FOUND));
                    String token = protection.generateSessionToken();
                    Instant now = clock.now();
                    ErasureRequest issued =
                            request.withPreview(protection.sessionTokenHash(token), now);
                    erasures.save(issued);
                    authorship.record(
                            actor,
                            Operation.ERASURE_PREVIEW,
                            AuthorshipTargetType.ERASURE_REQUEST,
                            requestId,
                            correlationId);
                    // depois de IN_PROGRESS o aluno já não tem relatórios para contar: vale o
                    // alcance gravado na confirmação
                    ErasureScope scope =
                            issued.scope().orElseGet(() -> scopes.compute(request.studentId()));
                    List<TeacherView> assigned =
                            assignments.listTeachersOfStudent(request.studentId()).stream()
                                    .map(teachers::searchById)
                                    .flatMap(java.util.Optional::stream)
                                    .map(this::view)
                                    .toList();
                    return new Preview(
                            scope,
                            assigned,
                            token,
                            issued.confirmation().orElseThrow().expiresAt(),
                            privacyContact);
                });
    }

    private TeacherView view(Teacher teacher) {
        return new TeacherView(teacher.id(), teacher.name());
    }
}
