package br.com.felipeblaft.reports.usecase.privacy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipReadDetail;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.privacy.RetentionDecision;
import br.com.felipeblaft.reports.domain.privacy.RetentionItemType;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import br.com.felipeblaft.reports.domain.privacy.RetentionRunStatus;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.port.AlertHistoryRetentionPort;
import br.com.felipeblaft.reports.port.AuditTrailRetentionPort;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.DocumentStoragePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.PdfDocumentRepository;
import br.com.felipeblaft.reports.port.PendingFileRemoval;
import br.com.felipeblaft.reports.port.ReportInteractionRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.RetentionPdfCandidate;
import br.com.felipeblaft.reports.port.RetentionRunRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.service.OperationalAlertService;
import br.com.felipeblaft.reports.usecase.audit.VerifyAuditChainUseCase;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Rotina diária de retenção (FR-010 a FR-015): remove as fontes e os PDFs vencidos, anonimiza os
 * cancelados vencidos, expurga a trilha, os alertas normalizados e o histórico de execuções, e
 * verifica a cadeia da trilha. Cada item é tratado numa transação própria, depois de reavaliado sob
 * bloqueio (um relatório aprovado entre a seleção e o tratamento não perde nada, FR-011); o arquivo
 * só sai do armazenamento depois do commit que o marcou. Uma falha de item vira evento de falha e
 * alerta, e os demais itens seguem. Repetir a rotina não trata nada duas vezes (FR-015). O registro
 * da execução guarda só identificadores e quantidades, nunca conteúdo.
 */
public class RunRetentionUseCase {

    public static final int BATCH_SIZE = 200;

    /** Execução {@code RUNNING} mais velha que isto é dada como interrompida. */
    public static final Duration STALE_AFTER = Duration.ofHours(6);

    public static final String ITEM_FAILED = "RETENTION_ITEM_FAILED";
    public static final String RUN_FAILED = "RETENTION_RUN_FAILED";
    private static final String COMPONENT = "retention";

    /** Resultado da execução; {@code runId} é nulo quando outra execução já estava em curso. */
    public record Result(
            RetentionRunStatus status,
            UUID runId,
            boolean alreadyRunning,
            Map<RetentionItemType, Integer> counts,
            int failures) {}

    private final AuthorshipService authorship;
    private final ClockPort clock;
    private final TransactionPort transaction;
    private final RetentionPolicy policy;
    private final LessonReportRepository lessons;
    private final SemesterReportRepository semesters;
    private final ReportVersionRepository versions;
    private final ReportInteractionRepository interactions;
    private final PdfDocumentRepository pdfs;
    private final DocumentStoragePort storage;
    private final RetentionRunRepository runs;
    private final AuditTrailRetentionPort trail;
    private final AlertHistoryRetentionPort alertHistory;
    private final OperationalAlertService alerts;
    private final VerifyAuditChainUseCase verifyChain;

    @SuppressWarnings("java:S107")
    public RunRetentionUseCase(
            AuthorshipService authorship,
            ClockPort clock,
            TransactionPort transaction,
            RetentionPolicy policy,
            LessonReportRepository lessons,
            SemesterReportRepository semesters,
            ReportVersionRepository versions,
            ReportInteractionRepository interactions,
            PdfDocumentRepository pdfs,
            DocumentStoragePort storage,
            RetentionRunRepository runs,
            AuditTrailRetentionPort trail,
            AlertHistoryRetentionPort alertHistory,
            OperationalAlertService alerts,
            VerifyAuditChainUseCase verifyChain) {
        this.authorship = Objects.requireNonNull(authorship);
        this.clock = Objects.requireNonNull(clock);
        this.transaction = Objects.requireNonNull(transaction);
        this.policy = Objects.requireNonNull(policy);
        this.lessons = Objects.requireNonNull(lessons);
        this.semesters = Objects.requireNonNull(semesters);
        this.versions = Objects.requireNonNull(versions);
        this.interactions = Objects.requireNonNull(interactions);
        this.pdfs = Objects.requireNonNull(pdfs);
        this.storage = Objects.requireNonNull(storage);
        this.runs = Objects.requireNonNull(runs);
        this.trail = Objects.requireNonNull(trail);
        this.alertHistory = Objects.requireNonNull(alertHistory);
        this.alerts = Objects.requireNonNull(alerts);
        this.verifyChain = Objects.requireNonNull(verifyChain);
    }

    /** Estado de uma execução em curso. */
    private static final class Execution {
        final UUID runId;
        final Actor actor;
        final String correlationId;
        final Map<RetentionItemType, Integer> counts = new EnumMap<>(RetentionItemType.class);
        int failures;

        Execution(UUID runId, Actor actor, String correlationId) {
            this.runId = runId;
            this.actor = actor;
            this.correlationId = correlationId;
        }

        void count(RetentionItemType type, int amount) {
            counts.merge(type, amount, Integer::sum);
        }

        int total() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** Falha ao gravar o evento de falha: a execução para (não há como registrar o que fez). */
    private static final class EventRecordingFailed extends RuntimeException {
        EventRecordingFailed(Throwable cause) {
            super(RUN_FAILED, cause);
        }
    }

    public Result run(Actor actor, String correlationId) {
        if (actor == null || actor.origin() != ActionOrigin.RETENTION) {
            throw new IllegalArgumentException("a retenção só roda com o ator da retenção");
        }
        Instant now = clock.now();
        Optional<UUID> started = runs.start(now, snapshot(), STALE_AFTER);
        if (started.isEmpty()) {
            return new Result(RetentionRunStatus.RUNNING, null, true, Map.of(), 0);
        }
        Execution execution = new Execution(started.get(), actor, correlationId);
        try {
            treatLessons(execution, now);
            treatSemesters(execution, now);
            treatUnapprovedPdfs(execution, now);
            removeFiles(execution);
            purgeTrail(execution, now);
            purgeAlertHistory(execution, now);
            purgeRunHistory(execution, now);
            verifyChain.run(actor, correlationId);
        } catch (RuntimeException failure) {
            return fail(execution, RUN_FAILED);
        }
        boolean clean = execution.failures == 0;
        String errorCode = clean ? null : ITEM_FAILED;
        if (clean) {
            alerts.resolve(AlertType.RETENTION_FAILED, COMPONENT);
        }
        runs.finish(
                execution.runId,
                clock.now(),
                clean ? RetentionRunStatus.SUCCEEDED : RetentionRunStatus.FAILED,
                errorCode);
        transaction.run(
                () ->
                        authorship.recordEvent(
                                actor,
                                "RETENTION_RUN",
                                AuthorshipTargetType.RETENTION_RUN,
                                execution.runId,
                                AuthorshipReadDetail.ofCount(execution.total()),
                                clean ? AuthorshipResult.SUCCESS : AuthorshipResult.FAILURE,
                                errorCode,
                                correlationId));
        return new Result(
                clean ? RetentionRunStatus.SUCCEEDED : RetentionRunStatus.FAILED,
                execution.runId,
                false,
                execution.counts,
                execution.failures);
    }

    // ------------------------------------------------------------ relatórios

    private void treatLessons(Execution execution, Instant now) {
        forEachCandidate(
                id -> lessons.findRetentionCandidates(policy, now, BATCH_SIZE, id),
                id ->
                        treat(
                                execution,
                                id,
                                AuthorshipTargetType.LESSON_REPORT,
                                () -> treatLesson(execution, id, now)));
    }

    private void treatLesson(Execution execution, UUID id, Instant now) {
        LessonReport report = lessons.searchForUpdate(id).orElse(null);
        if (report == null) {
            return;
        }
        RetentionDecision decision = report.retentionAction(policy, now);
        if (decision == RetentionDecision.REMOVE_SOURCE) {
            report.removeSource(now);
            lessons.save(report);
            int texts = interactions.removeText(ReportType.LESSON, id, now);
            add(execution, RetentionItemType.LESSON_SOURCE, id, 1 + texts);
        } else if (decision == RetentionDecision.ANONYMIZE) {
            report.anonymize(now);
            lessons.save(report);
            interactions.removeText(ReportType.LESSON, id, now);
            versions.markContentRemoved(ReportType.LESSON, id, now);
            pdfs.markContentRemovedForReport(ReportType.LESSON, id, now);
            add(execution, RetentionItemType.CANCELLED_LESSON_REPORT, id, 1);
        }
    }

    private void treatSemesters(Execution execution, Instant now) {
        forEachCandidate(
                id -> semesters.findRetentionCandidates(policy, now, BATCH_SIZE, id),
                id ->
                        treat(
                                execution,
                                id,
                                AuthorshipTargetType.SEMESTER_REPORT,
                                () -> treatSemester(execution, id, now)));
    }

    private void treatSemester(Execution execution, UUID id, Instant now) {
        SemesterReport report = semesters.searchForUpdate(id).orElse(null);
        if (report == null) {
            return;
        }
        RetentionDecision decision = report.retentionAction(policy, now);
        if (decision == RetentionDecision.REMOVE_SOURCE) {
            report.removeSource(now);
            semesters.save(report);
            int texts = interactions.removeText(ReportType.SEMESTER, id, now);
            add(execution, RetentionItemType.SEMESTER_SOURCE, id, texts);
        } else if (decision == RetentionDecision.ANONYMIZE) {
            report.anonymize(now);
            semesters.save(report);
            interactions.removeText(ReportType.SEMESTER, id, now);
            versions.markContentRemoved(ReportType.SEMESTER, id, now);
            pdfs.markContentRemovedForReport(ReportType.SEMESTER, id, now);
            add(execution, RetentionItemType.CANCELLED_SEMESTER_REPORT, id, 1);
        }
    }

    // ------------------------------------------------------------ PDFs

    private void treatUnapprovedPdfs(Execution execution, Instant now) {
        forEachCandidate(
                id -> pdfs.findRetentionCandidates(policy, now, BATCH_SIZE, id),
                id ->
                        treat(
                                execution,
                                id,
                                AuthorshipTargetType.LESSON_REPORT,
                                () -> treatPdf(execution, id, now)));
    }

    private void treatPdf(Execution execution, UUID id, Instant now) {
        RetentionPdfCandidate candidate = pdfs.findForRetention(id).orElse(null);
        if (candidate == null) {
            return;
        }
        RetentionDecision decision =
                candidate
                        .pdf()
                        .retentionAction(
                                policy,
                                now,
                                candidate.approvedVersion(),
                                candidate.reportApprovedAt());
        if (decision == RetentionDecision.REMOVE_UNAPPROVED_PDFS) {
            pdfs.markContentRemoved(id, now);
            add(execution, RetentionItemType.UNAPPROVED_PDF, id, 1);
        }
    }

    /** Remove do armazenamento os arquivos já marcados (inclusive os de execuções anteriores). */
    private void removeFiles(Execution execution) {
        Set<UUID> failed = new HashSet<>();
        while (true) {
            List<PendingFileRemoval> batch =
                    pdfs.findPendingFileRemovals(BATCH_SIZE + failed.size()).stream()
                            .filter(file -> !failed.contains(file.pdfId()))
                            .toList();
            if (batch.isEmpty()) {
                return;
            }
            for (PendingFileRemoval file : batch) {
                try {
                    storage.delete(file.storageKey());
                    transaction.run(
                            () -> {
                                pdfs.markFileRemoved(file.pdfId(), clock.now());
                                add(execution, RetentionItemType.PDF_FILE, file.pdfId(), 1);
                            });
                    alerts.resolve(AlertType.STORAGE_FAILED, "pdf-storage");
                } catch (RuntimeException failure) {
                    failed.add(file.pdfId());
                    registerFailure(execution, file.pdfId(), AuthorshipTargetType.LESSON_REPORT);
                    alerts.report(AlertType.STORAGE_FAILED, "pdf-storage", "DELETE_FAILED");
                }
            }
        }
    }

    // ------------------------------------------------------------ expurgos

    private void purgeTrail(Execution execution, Instant now) {
        int removed =
                trail.purgeOlderThan(now.minus(Duration.ofDays(policy.auditTrailDays().value())));
        if (removed > 0) {
            execution.count(RetentionItemType.AUDIT_EVENTS, removed);
            runs.addItem(execution.runId, RetentionItemType.AUDIT_EVENTS, null, removed);
            transaction.run(
                    () ->
                            authorship.recordEvent(
                                    execution.actor,
                                    "AUDIT_TRAIL_PURGED",
                                    AuthorshipTargetType.AUDIT_TRAIL,
                                    null,
                                    AuthorshipReadDetail.ofCount(removed),
                                    AuthorshipResult.SUCCESS,
                                    null,
                                    execution.correlationId));
        }
    }

    private void purgeAlertHistory(Execution execution, Instant now) {
        int removed =
                alertHistory.purgeNormalizedBefore(
                        now.minus(Duration.ofDays(policy.auditTrailDays().value())));
        if (removed > 0) {
            execution.count(RetentionItemType.ALERT_HISTORY, removed);
            runs.addItem(execution.runId, RetentionItemType.ALERT_HISTORY, null, removed);
        }
    }

    private void purgeRunHistory(Execution execution, Instant now) {
        int removed =
                runs.purgeStartedBefore(
                        now.minus(Duration.ofDays(policy.runHistoryDays().value())));
        if (removed > 0) {
            execution.count(RetentionItemType.RUN_HISTORY, removed);
            runs.addItem(execution.runId, RetentionItemType.RUN_HISTORY, null, removed);
        }
    }

    // ------------------------------------------------------------ apoio

    private void forEachCandidate(
            Function<UUID, List<UUID>> source, java.util.function.Consumer<UUID> action) {
        UUID after = null;
        while (true) {
            List<UUID> batch = source.apply(after);
            if (batch.isEmpty()) {
                return;
            }
            batch.forEach(action);
            after = batch.getLast();
        }
    }

    /** Trata um item numa transação; falha vira evento e alerta, e a execução segue. */
    private void treat(
            Execution execution, UUID id, AuthorshipTargetType targetType, Runnable work) {
        try {
            transaction.run(work);
        } catch (RuntimeException failure) {
            registerFailure(execution, id, targetType);
        }
    }

    private void registerFailure(Execution execution, UUID id, AuthorshipTargetType targetType) {
        execution.failures++;
        alerts.report(AlertType.RETENTION_FAILED, COMPONENT, "ITEM_FAILED");
        try {
            authorship.recordEvent(
                    execution.actor,
                    "RETENTION_RUN",
                    targetType,
                    id,
                    null,
                    AuthorshipResult.FAILURE,
                    ITEM_FAILED,
                    execution.correlationId);
        } catch (RuntimeException recordingFailure) {
            throw new EventRecordingFailed(recordingFailure);
        }
    }

    private void add(Execution execution, RetentionItemType type, UUID targetId, int count) {
        runs.addItem(execution.runId, type, targetId, count);
        execution.count(type, count);
    }

    private Result fail(Execution execution, String errorCode) {
        alerts.report(AlertType.RETENTION_FAILED, COMPONENT, "RUN_FAILED");
        runs.finish(execution.runId, clock.now(), RetentionRunStatus.FAILED, errorCode);
        try {
            authorship.recordEvent(
                    execution.actor,
                    "RETENTION_RUN",
                    AuthorshipTargetType.RETENTION_RUN,
                    execution.runId,
                    AuthorshipReadDetail.ofCount(execution.total()),
                    AuthorshipResult.FAILURE,
                    errorCode,
                    execution.correlationId);
        } catch (RuntimeException ignored) {
            // sem como registrar: o alerta e o estado da execução já dizem que falhou
        }
        return new Result(
                RetentionRunStatus.FAILED,
                execution.runId,
                false,
                execution.counts,
                execution.failures);
    }

    private Map<String, Integer> snapshot() {
        Map<String, Integer> days = new LinkedHashMap<>();
        days.put("transcriptionDays", policy.transcriptionDays().value());
        days.put("unapprovedPdfDays", policy.unapprovedPdfDays().value());
        days.put("cancelledReportDays", policy.cancelledReportDays().value());
        days.put("auditTrailDays", policy.auditTrailDays().value());
        days.put("runHistoryDays", policy.runHistoryDays().value());
        return days;
    }
}
