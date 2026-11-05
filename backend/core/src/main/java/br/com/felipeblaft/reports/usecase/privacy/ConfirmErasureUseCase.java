package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipReadDetail;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.privacy.ErasedStudent;
import br.com.felipeblaft.reports.domain.privacy.ErasureConfirmation;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureScope;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConsentRepository;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.DocumentStoragePort;
import br.com.felipeblaft.reports.port.ErasureRequestRepository;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.PendingFileRemoval;
import br.com.felipeblaft.reports.port.ReportInteractionRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.ErasureScopeService;
import br.com.felipeblaft.reports.service.OperationalAlertService;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Confirma e executa a eliminação ({@code ERASURE_CONFIRM}, FR-022 a FR-026). Exige a palavra
 * {@code ELIMINAR} e o token da última prévia. A parte do banco corre numa só transação (o aluno
 * nunca fica meio anonimizado): relatórios abertos são cancelados, todos os relatórios perdem
 * fonte, versões, textos e PDFs, saem as associações e o nome de quem consentiu, e o aluno é
 * eliminado. Só depois do commit os arquivos saem do armazenamento; se algum falhar, o pedido fica
 * {@code IN_PROGRESS} com alerta e uma nova prévia seguida de confirmação conclui. O nome de quem
 * pediu é apagado na conclusão.
 */
public class ConfirmErasureUseCase {

    public static final int BATCH_SIZE = 200;
    private static final String COMPONENT = "pdf-storage";

    public record Command(
            UUID requestId,
            String confirmationToken,
            String confirmationWord,
            String correlationId) {

        @Override
        public String toString() {
            return "ConfirmErasureUseCase.Command";
        }
    }

    private final ActorAuthorizationService authorization;
    private final ErasureRequestRepository erasures;
    private final ErasureScopeService scopes;
    private final LessonReportRepository lessons;
    private final SemesterReportRepository semesters;
    private final ReportVersionRepository versions;
    private final ReportInteractionRepository interactions;
    private final PdfDocumentRepository pdfs;
    private final AssignmentRepository assignments;
    private final ConsentRepository consents;
    private final StudentRepository students;
    private final JobQueuePort queue;
    private final DocumentStoragePort storage;
    private final DataProtectionPort protection;
    private final OperationalAlertService alerts;
    private final AuthorshipService authorship;
    private final TransactionPort transaction;
    private final ClockPort clock;

    @SuppressWarnings("java:S107")
    public ConfirmErasureUseCase(
            ActorAuthorizationService authorization,
            ErasureRequestRepository erasures,
            ErasureScopeService scopes,
            LessonReportRepository lessons,
            SemesterReportRepository semesters,
            ReportVersionRepository versions,
            ReportInteractionRepository interactions,
            PdfDocumentRepository pdfs,
            AssignmentRepository assignments,
            ConsentRepository consents,
            StudentRepository students,
            JobQueuePort queue,
            DocumentStoragePort storage,
            DataProtectionPort protection,
            OperationalAlertService alerts,
            AuthorshipService authorship,
            TransactionPort transaction,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.erasures = Objects.requireNonNull(erasures);
        this.scopes = Objects.requireNonNull(scopes);
        this.lessons = Objects.requireNonNull(lessons);
        this.semesters = Objects.requireNonNull(semesters);
        this.versions = Objects.requireNonNull(versions);
        this.interactions = Objects.requireNonNull(interactions);
        this.pdfs = Objects.requireNonNull(pdfs);
        this.assignments = Objects.requireNonNull(assignments);
        this.consents = Objects.requireNonNull(consents);
        this.students = Objects.requireNonNull(students);
        this.queue = Objects.requireNonNull(queue);
        this.storage = Objects.requireNonNull(storage);
        this.protection = Objects.requireNonNull(protection);
        this.alerts = Objects.requireNonNull(alerts);
        this.authorship = Objects.requireNonNull(authorship);
        this.transaction = Objects.requireNonNull(transaction);
        this.clock = Objects.requireNonNull(clock);
    }

    public ErasureRequest run(Actor actor, Command command) {
        authorization.require(actor, Operation.ERASURE_CONFIRM);
        if (!ErasureConfirmation.isConfirmationWord(command.confirmationWord())) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("fields", Map.of("confirmationWord", ErrorCode.INVALID_DATA.name())));
        }
        ErasureRequest confirmed = transaction.run(() -> eraseData(actor, command));
        return finish(actor, confirmed, command.correlationId());
    }

    /**
     * Retomada pela retenção: conclui os pedidos {@code IN_PROGRESS} cujos arquivos já puderam
     * sair. Devolve quantos foram concluídos.
     */
    public int resumePending(Actor actor, String correlationId) {
        authorization.require(actor, Operation.ERASURE_CONFIRM);
        int completed = 0;
        for (ErasureRequest request : erasures.listInProgress()) {
            if (finish(actor, request, correlationId).status() == ErasureStatus.COMPLETED) {
                completed++;
            }
        }
        return completed;
    }

    // ------------------------------------------------------------ banco (uma transação)

    private ErasureRequest eraseData(Actor actor, Command command) {
        ErasureRequest request =
                erasures.findForUpdate(command.requestId())
                        .orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND));
        Instant now = clock.now();
        // numa retomada o alcance gravado na primeira confirmação é o que vale
        ErasureScope scope = request.scope().orElseGet(() -> scopes.compute(request.studentId()));
        ErasureRequest confirmed =
                request.confirm(
                        protection.sessionTokenHash(command.confirmationToken()),
                        actor.id(),
                        scope,
                        now);
        erasures.save(confirmed);
        eraseStudent(request.studentId(), now);
        authorship.record(
                actor,
                Operation.ERASURE_CONFIRM,
                AuthorshipTargetType.ERASURE_REQUEST,
                request.id(),
                command.correlationId());
        return confirmed;
    }

    private void eraseStudent(UUID studentId, Instant now) {
        for (UUID id : lessons.findIdsByStudent(studentId)) {
            lessons.searchForUpdate(id)
                    .ifPresent(
                            report -> {
                                if (report.status().isOpen()) {
                                    report.cancel(now, true);
                                }
                                if (report.anonymizedAt() == null) {
                                    report.anonymize(now);
                                }
                                lessons.save(report);
                            });
            discardJob(ReportType.LESSON, id, now);
            interactions.removeText(ReportType.LESSON, id, now);
            versions.markContentRemoved(ReportType.LESSON, id, now);
            pdfs.markContentRemovedForReport(ReportType.LESSON, id, now);
        }
        for (UUID id : semesters.findIdsByStudent(studentId)) {
            semesters
                    .searchForUpdate(id)
                    .ifPresent(
                            report -> {
                                if (report.status().isOpen()) {
                                    report.cancel(now);
                                }
                                if (report.anonymizedAt() == null) {
                                    report.anonymize(now);
                                }
                                semesters.save(report);
                            });
            discardJob(ReportType.SEMESTER, id, now);
            interactions.removeText(ReportType.SEMESTER, id, now);
            versions.markContentRemoved(ReportType.SEMESTER, id, now);
            pdfs.markContentRemovedForReport(ReportType.SEMESTER, id, now);
        }
        assignments.deleteAllByStudent(studentId);
        consents.removeNamesByStudent(studentId);
        students.erase(studentId, now);
        erasures.recordErased(new ErasedStudent(studentId, now));
    }

    /** O resultado de IA de um trabalho em curso do relatório deixa de ser aproveitado. */
    private void discardJob(ReportType type, UUID reportId, Instant now) {
        queue.searchInProgressByReport(type, reportId)
                .ifPresent(
                        job -> {
                            job.discard(now);
                            queue.update(job);
                        });
    }

    // ------------------------------------------------------------ arquivos e conclusão

    private ErasureRequest finish(Actor actor, ErasureRequest request, String correlationId) {
        if (request.status() != ErasureStatus.IN_PROGRESS || !removeFiles()) {
            return request;
        }
        return transaction.run(
                () -> {
                    ErasureRequest done =
                            erasures.findForUpdate(request.id())
                                    .orElseThrow(
                                            () -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND))
                                    .complete(clock.now());
                    erasures.save(done);
                    authorship.recordEvent(
                            actor,
                            "ERASURE_COMPLETED",
                            AuthorshipTargetType.ERASURE_REQUEST,
                            done.id(),
                            AuthorshipReadDetail.ofCount(total(done.scope().orElseThrow())),
                            AuthorshipResult.SUCCESS,
                            null,
                            correlationId);
                    return done;
                });
    }

    /** Remove do armazenamento os arquivos marcados; {@code false} se algum não saiu. */
    private boolean removeFiles() {
        Set<UUID> failed = new HashSet<>();
        while (true) {
            List<PendingFileRemoval> batch =
                    pdfs.findPendingFileRemovals(BATCH_SIZE + failed.size()).stream()
                            .filter(file -> !failed.contains(file.pdfId()))
                            .toList();
            if (batch.isEmpty()) {
                return failed.isEmpty();
            }
            for (PendingFileRemoval file : batch) {
                try {
                    storage.delete(file.storageKey());
                    transaction.run(() -> pdfs.markFileRemoved(file.pdfId(), clock.now()));
                } catch (RuntimeException failure) {
                    failed.add(file.pdfId());
                    alerts.report(AlertType.STORAGE_FAILED, COMPONENT, "DELETE_FAILED");
                }
            }
        }
    }

    private static int total(ErasureScope scope) {
        return scope.totalLessonReports()
                + scope.totalSemesterReports()
                + scope.pdfs()
                + scope.assignments()
                + scope.consents();
    }
}
