package br.com.felipeblaft.reports.usecase.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.privacy.RetentionItemType;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import br.com.felipeblaft.reports.domain.privacy.RetentionRunStatus;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.InteractionResult;
import br.com.felipeblaft.reports.domain.report.InteractionType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.FakeAlertHistoryRetention;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAuditTrail;
import br.com.felipeblaft.reports.doubles.InMemoryRetentionRunRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.ImmediateTransaction;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryDocumentStorage;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryLessonReportRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryPdfDocumentRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryReportInteractionRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemoryReportVersionRepository;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.InMemorySemesterReportRepository;
import br.com.felipeblaft.reports.doubles.RecordingAlertService;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import br.com.felipeblaft.reports.usecase.audit.VerifyAuditChainUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RunRetentionUseCase.Result;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Rotina de retenção (FR-010 a FR-015) com dublês: seleção, reavaliação, idempotência e falhas. */
class RunRetentionUseCaseTest {

    private static final Instant BASE = Instant.parse("2026-03-01T12:00:00Z");

    private final FixedClock clock = new FixedClock(BASE);
    private final InMemoryAuditTrail trail = new InMemoryAuditTrail();
    private final FailingIndependentRecorder recorder = new FailingIndependentRecorder(trail);
    private final AuthorshipService authorship = new AuthorshipService(recorder, clock);
    private final RecordingAlertService alerts = new RecordingAlertService();
    private final ImmediateTransaction transaction = new ImmediateTransaction();
    private final InMemoryLessonReportRepository lessons = new InMemoryLessonReportRepository();
    private final InMemorySemesterReportRepository semesters =
            new InMemorySemesterReportRepository();
    private final InMemoryReportVersionRepository versions = new InMemoryReportVersionRepository();
    private final InMemoryReportInteractionRepository interactions =
            new InMemoryReportInteractionRepository();
    private final InMemoryPdfDocumentRepository pdfs = new InMemoryPdfDocumentRepository();
    private final InMemoryDocumentStorage storage = new InMemoryDocumentStorage();
    private final InMemoryRetentionRunRepository runs = new InMemoryRetentionRunRepository();
    private final FakeAlertHistoryRetention alertHistory = new FakeAlertHistoryRetention();
    private final RetentionPolicy policy = RetentionPolicy.from(Map.of());
    private final Actor actor = Actor.retention();

    private final RunRetentionUseCase useCase =
            new RunRetentionUseCase(
                    authorship,
                    clock,
                    transaction,
                    policy,
                    lessons,
                    semesters,
                    versions,
                    interactions,
                    pdfs,
                    storage,
                    runs,
                    trail,
                    alertHistory,
                    alerts,
                    new VerifyAuditChainUseCase(
                            new ActorAuthorizationService(PermissionMatrix.standard()),
                            trail,
                            authorship,
                            alerts,
                            100));

    /** Recorder que pode falhar na gravação independente (simula a trilha indisponível). */
    private static final class FailingIndependentRecorder implements AuthorshipRecorderPort {
        private final AuthorshipRecorderPort delegate;
        boolean failIndependent;

        FailingIndependentRecorder(AuthorshipRecorderPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public void record(AuthorshipRecord record) {
            delegate.record(record);
        }

        @Override
        public void recordIndependent(AuthorshipRecord record) {
            if (failIndependent) {
                throw new IllegalStateException("trilha indisponível");
            }
            delegate.recordIndependent(record);
        }
    }

    private LessonReport lesson(ReportStatus status, Instant approvedAt, Instant cancelledAt) {
        LessonReport report =
                new LessonReport(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "source-text",
                        LessonReportContent.empty(),
                        List.of(),
                        0,
                        status,
                        null,
                        2,
                        Channel.WEB,
                        null,
                        BASE.minus(Duration.ofDays(200)),
                        BASE.minus(Duration.ofDays(200)),
                        approvedAt,
                        approvedAt == null ? null : 2,
                        approvedAt == null ? null : Channel.WEB,
                        cancelledAt,
                        0,
                        0);
        lessons.save(report);
        interactions.save(interaction(ReportType.LESSON, report.id()));
        interactions.save(interaction(ReportType.LESSON, report.id()));
        return report;
    }

    private ReportInteraction interaction(ReportType type, UUID reportId) {
        return ReportInteraction.create(
                type,
                reportId,
                InteractionType.QUESTION_ANSWER,
                Channel.WEB,
                Medium.TEXT,
                "typed-text",
                1,
                null,
                InteractionResult.APPLIED,
                2,
                BASE);
    }

    private SemesterReport semester(ReportStatus status, Instant approvedAt, Instant cancelledAt) {
        GeneralInformation info =
                new GeneralInformation(
                        "Name",
                        10,
                        "Piano",
                        "Teacher",
                        "2025-2",
                        LocalDate.of(2025, 7, 1),
                        LocalDate.of(2025, 12, 31),
                        3);
        SemesterReport report =
                new SemesterReport(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new SemesterPeriod(2025, 2),
                        3,
                        0,
                        info,
                        SemesterReportContent.empty(),
                        status,
                        null,
                        1,
                        Channel.WEB,
                        BASE.minus(Duration.ofDays(200)),
                        BASE.minus(Duration.ofDays(200)),
                        approvedAt,
                        approvedAt == null ? null : 1,
                        approvedAt == null ? null : Channel.WEB,
                        cancelledAt,
                        0,
                        0);
        semesters.save(report);
        interactions.save(interaction(ReportType.SEMESTER, report.id()));
        return report;
    }

    private PdfDocument pdf(
            ReportType type, UUID reportId, boolean approvedVersion, Instant approvedAt) {
        PdfDocument document =
                PdfDocument.create(
                        UUID.randomUUID(),
                        DocumentType.OFFICIAL,
                        "key-" + UUID.randomUUID(),
                        3,
                        "h",
                        BASE.minus(Duration.ofDays(100)));
        storage.save(document.storageKey(), new byte[] {1, 2, 3});
        pdfs.link(document, type, reportId, approvedVersion, approvedAt);
        return document;
    }

    private Instant daysAgo(long days) {
        return BASE.minus(Duration.ofDays(days));
    }

    private List<AuthorshipRecord> retentionEvents() {
        return trail.records().stream()
                .filter(r -> r.operation().equals("RETENTION_RUN"))
                .toList();
    }

    @Test
    void approvedLessonPastDeadlineLosesSourceAndInteractionTexts() {
        LessonReport old = lesson(ReportStatus.APPROVED, daysAgo(31), null);
        LessonReport recent = lesson(ReportStatus.APPROVED, daysAgo(29), null);
        PdfDocument approvedPdf = pdf(ReportType.LESSON, old.id(), true, old.approvedAt());

        Result result = useCase.run(actor, "req-a");

        assertThat(result.status()).isEqualTo(RetentionRunStatus.SUCCEEDED);
        assertThat(lessons.reports.get(old.id()).transcription()).isNull();
        assertThat(lessons.reports.get(old.id()).sourceRemovedAt()).isEqualTo(BASE);
        assertThat(lessons.reports.get(recent.id()).transcription()).isEqualTo("source-text");
        assertThat(interactions.interactions)
                .filteredOn(i -> i.reportId().equals(old.id()))
                .allMatch(i -> i.text() == null && i.textRemovedAt() != null);
        assertThat(interactions.interactions)
                .filteredOn(i -> i.reportId().equals(recent.id()))
                .allMatch(i -> i.text() != null);
        assertThat(versions.versions).isEmpty();
        assertThat(pdfs.documents.values()).allMatch(d -> d.contentRemovedAt() == null);
        assertThat(storage.files).containsKey(approvedPdf.storageKey());
        assertThat(runs.totalOf(RetentionItemType.LESSON_SOURCE)).isEqualTo(3);
    }

    @Test
    void reevaluatesUnderLockSoApprovalAfterSelectionRemovesNothing() {
        LessonReport open = lesson(ReportStatus.AWAITING_REVIEW, null, null);
        InMemoryLessonReportRepository stale =
                new InMemoryLessonReportRepository() {
                    @Override
                    public List<UUID> findRetentionCandidates(
                            RetentionPolicy p, Instant now, int limit, UUID afterId) {
                        return afterId == null ? List.of(open.id()) : List.of();
                    }
                };
        stale.reports.put(open.id(), open);
        RunRetentionUseCase racing = withLessons(stale);

        racing.run(actor, "req-b");

        assertThat(stale.reports.get(open.id()).transcription()).isEqualTo("source-text");
        assertThat(runs.items).isEmpty();
    }

    private RunRetentionUseCase withLessons(InMemoryLessonReportRepository repository) {
        return new RunRetentionUseCase(
                authorship,
                clock,
                transaction,
                policy,
                repository,
                semesters,
                versions,
                interactions,
                pdfs,
                storage,
                runs,
                trail,
                alertHistory,
                alerts,
                new VerifyAuditChainUseCase(
                        new ActorAuthorizationService(PermissionMatrix.standard()),
                        trail,
                        authorship,
                        alerts,
                        100));
    }

    @Test
    void approvedSemesterKeepsGeneralInformationAndLosesOnlyInteractionTexts() {
        SemesterReport report = semester(ReportStatus.APPROVED, daysAgo(40), null);

        useCase.run(actor, "req-c");

        SemesterReport after = semesters.reports.get(report.id());
        assertThat(after.generalInformation()).isNotNull();
        assertThat(after.sourceRemovedAt()).isEqualTo(BASE);
        assertThat(after.anonymizedAt()).isNull();
        assertThat(interactions.interactions).allMatch(i -> i.text() == null);
        assertThat(runs.totalOf(RetentionItemType.SEMESTER_SOURCE)).isEqualTo(1);
    }

    @Test
    void unapprovedPdfsOfApprovedReportAreRemovedButTheApprovedOneStays() {
        LessonReport report = lesson(ReportStatus.APPROVED, daysAgo(31), null);
        PdfDocument approved = pdf(ReportType.LESSON, report.id(), true, report.approvedAt());
        PdfDocument draft = pdf(ReportType.LESSON, report.id(), false, report.approvedAt());

        useCase.run(actor, "req-d");

        assertThat(pdfs.documents.values())
                .filteredOn(d -> d.id().equals(draft.id()))
                .allMatch(d -> d.contentRemovedAt() != null);
        assertThat(storage.files).doesNotContainKey(draft.storageKey());
        assertThat(storage.files).containsKey(approved.storageKey());
        assertThat(runs.totalOf(RetentionItemType.UNAPPROVED_PDF)).isEqualTo(1);
        assertThat(runs.totalOf(RetentionItemType.PDF_FILE)).isEqualTo(1);
    }

    @Test
    void cancelledLessonPastDeadlineIsAnonymizedWithFilesAfterCommit() {
        LessonReport report = lesson(ReportStatus.CANCELLED, null, daysAgo(91));
        PdfDocument document = pdf(ReportType.LESSON, report.id(), false, null);

        useCase.run(actor, "req-e");

        LessonReport after = lessons.reports.get(report.id());
        assertThat(after.anonymizedAt()).isEqualTo(BASE);
        assertThat(after.transcription()).isNull();
        assertThat(pdfs.documents.values()).allMatch(d -> d.contentRemovedAt() != null);
        assertThat(storage.files).doesNotContainKey(document.storageKey());
        assertThat(runs.totalOf(RetentionItemType.CANCELLED_LESSON_REPORT)).isEqualTo(1);
    }

    @Test
    void cancelledSemesterIsAnonymizedAndClearsGeneralInformation() {
        SemesterReport report = semester(ReportStatus.CANCELLED, null, daysAgo(95));

        useCase.run(actor, "req-f");

        assertThat(semesters.reports.get(report.id()).generalInformation()).isNull();
        assertThat(semesters.reports.get(report.id()).anonymizedAt()).isEqualTo(BASE);
        assertThat(runs.totalOf(RetentionItemType.CANCELLED_SEMESTER_REPORT)).isEqualTo(1);
    }

    @Test
    void secondRunTreatsNothingAgain() {
        lesson(ReportStatus.APPROVED, daysAgo(31), null);
        lesson(ReportStatus.CANCELLED, null, daysAgo(91));
        useCase.run(actor, "req-g");
        int itemsAfterFirst = runs.items.size();

        Result second = useCase.run(actor, "req-h");

        assertThat(second.status()).isEqualTo(RetentionRunStatus.SUCCEEDED);
        assertThat(second.counts()).isEmpty();
        assertThat(runs.items).hasSize(itemsAfterFirst);
    }

    @Test
    void concurrentRunIsRefusedAndStaleOneIsInterrupted() {
        UUID running = runs.start(BASE.minus(Duration.ofMinutes(5)), Map.of(), Duration.ofHours(6)).get();

        Result refused = useCase.run(actor, "req-i");

        assertThat(refused.alreadyRunning()).isTrue();
        assertThat(runs.runs).hasSize(1);

        clock.define(BASE.plus(Duration.ofHours(7)));
        Result resumed = useCase.run(actor, "req-j");

        assertThat(resumed.alreadyRunning()).isFalse();
        assertThat(
                        runs.runs.stream()
                                .filter(r -> r.id.equals(running))
                                .findFirst()
                                .orElseThrow()
                                .status)
                .isEqualTo(RetentionRunStatus.INTERRUPTED);
    }

    @Test
    void fileRemovalFailureKeepsItemMarkedAndNextRunRemovesIt() {
        LessonReport report = lesson(ReportStatus.APPROVED, daysAgo(31), null);
        PdfDocument draft = pdf(ReportType.LESSON, report.id(), false, report.approvedAt());
        storage.failingDeletes.add(draft.storageKey());

        Result first = useCase.run(actor, "req-k");

        assertThat(first.status()).isEqualTo(RetentionRunStatus.FAILED);
        assertThat(first.failures()).isEqualTo(1);
        assertThat(storage.files).containsKey(draft.storageKey());
        assertThat(alerts.reported())
                .extracting(RecordingAlertService.Reported::type)
                .contains(AlertType.STORAGE_FAILED, AlertType.RETENTION_FAILED);
        assertThat(retentionEvents())
                .anyMatch(e -> e.result() == AuthorshipResult.FAILURE);

        storage.failingDeletes.clear();
        Result second = useCase.run(actor, "req-l");

        assertThat(second.status()).isEqualTo(RetentionRunStatus.SUCCEEDED);
        assertThat(storage.files).doesNotContainKey(draft.storageKey());
    }

    @Test
    void itemFailureDoesNotStopOtherItems() {
        LessonReport broken = lesson(ReportStatus.APPROVED, daysAgo(31), null);
        LessonReport fine = lesson(ReportStatus.APPROVED, daysAgo(32), null);
        InMemoryLessonReportRepository failing =
                new InMemoryLessonReportRepository() {
                    @Override
                    public void save(LessonReport report) {
                        if (report.id().equals(broken.id()) && report.transcription() == null) {
                            throw new IllegalStateException("falha simulada");
                        }
                        super.save(report);
                    }
                };
        failing.reports.putAll(lessons.reports);

        Result result = withLessons(failing).run(actor, "req-m");

        assertThat(result.failures()).isEqualTo(1);
        assertThat(failing.reports.get(fine.id()).transcription()).isNull();
        assertThat(alerts.reported())
                .extracting(RecordingAlertService.Reported::type)
                .contains(AlertType.RETENTION_FAILED);
    }

    @Test
    void stopsWhenTheFailureEventCannotBeRecorded() {
        LessonReport report = lesson(ReportStatus.APPROVED, daysAgo(31), null);
        PdfDocument draft = pdf(ReportType.LESSON, report.id(), false, report.approvedAt());
        storage.failingDeletes.add(draft.storageKey());
        recorder.failIndependent = true;

        Result result = useCase.run(actor, "req-n");

        assertThat(result.status()).isEqualTo(RetentionRunStatus.FAILED);
        assertThat(runs.runs.getLast().status).isEqualTo(RetentionRunStatus.FAILED);
        assertThat(runs.runs.getLast().errorCode).isEqualTo(RunRetentionUseCase.RUN_FAILED);
        assertThat(alerts.reported())
                .extracting(RecordingAlertService.Reported::type)
                .contains(AlertType.RETENTION_FAILED);
    }

    @Test
    void purgesTrailAlertsAndRunHistoryThenVerifiesTheChain() {
        clock.define(daysAgo(2000));
        authorship.record(
                ActorFixtures.administratorWeb(),
                Operation.STUDENT_REGISTER,
                AuthorshipTargetType.STUDENT,
                UUID.randomUUID(),
                "req-old");
        clock.define(BASE);
        runs.start(daysAgo(2000), Map.of(), Duration.ofHours(6)).ifPresent(
                id ->
                        runs.finish(
                                id, daysAgo(2000), RetentionRunStatus.SUCCEEDED, null));
        alertHistory.toPurge = 4;

        Result result = useCase.run(actor, "req-o");

        assertThat(result.counts().get(RetentionItemType.AUDIT_EVENTS)).isEqualTo(1);
        assertThat(result.counts().get(RetentionItemType.ALERT_HISTORY)).isEqualTo(4);
        assertThat(result.counts().get(RetentionItemType.RUN_HISTORY)).isEqualTo(1);
        assertThat(alertHistory.lastCutoff)
                .isEqualTo(BASE.minus(Duration.ofDays(policy.auditTrailDays().value())));
        assertThat(trail.records())
                .extracting(AuthorshipRecord::operation)
                .contains("AUDIT_TRAIL_PURGED", "AUDIT_TRAIL_VERIFY", "RETENTION_RUN");
        assertThat(retentionEvents().getLast().readDetail().itemCount()).isEqualTo(6);
    }

    @Test
    void onlyTheRetentionActorMayRun() {
        assertThatThrownBy(() -> useCase.run(ActorFixtures.administratorWeb(), "req-p"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
