package br.com.felipeblaft.reports.domain.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.report.PdfDocument;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.report.ReportStep;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetentionDecisionTest {

    private static final Instant BASE = Instant.parse("2026-03-01T12:00:00Z");
    private static final RetentionPolicy POLICY = RetentionPolicy.from(Map.of());

    private static Instant after(Instant from, long days) {
        return from.plus(Duration.ofDays(days));
    }

    private static LessonReport lesson(
            ReportStatus status, Instant approvedAt, Instant cancelledAt) {
        return new LessonReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "source-text",
                LessonReportContent.empty(),
                List.of(),
                0,
                status,
                status == ReportStatus.PROCESSING ? ReportStep.STRUCTURING : null,
                1,
                Channel.WEB,
                null,
                BASE,
                BASE,
                approvedAt,
                approvedAt == null ? null : 1,
                approvedAt == null ? null : Channel.WEB,
                cancelledAt,
                0,
                0);
    }

    private static SemesterReport semester(
            ReportStatus status, Instant approvedAt, Instant cancelledAt) {
        GeneralInformation info =
                new GeneralInformation(
                        "Name",
                        10,
                        "Piano",
                        "Teacher",
                        "2026-1",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 6, 30),
                        3);
        return new SemesterReport(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new SemesterPeriod(2026, 1),
                3,
                0,
                info,
                SemesterReportContent.empty(),
                status,
                null,
                1,
                Channel.WEB,
                BASE,
                BASE,
                approvedAt,
                approvedAt == null ? null : 1,
                approvedAt == null ? null : Channel.WEB,
                cancelledAt,
                0,
                0);
    }

    @Test
    void approvedLessonBoundaryAtThirtyDays() {
        LessonReport report = lesson(ReportStatus.APPROVED, BASE, null);
        assertThat(report.retentionAction(POLICY, after(BASE, 29))).isEqualTo(RetentionDecision.KEEP);
        assertThat(report.retentionAction(POLICY, after(BASE, 30)))
                .isEqualTo(RetentionDecision.REMOVE_SOURCE);
        assertThat(report.retentionAction(POLICY, after(BASE, 31)))
                .isEqualTo(RetentionDecision.REMOVE_SOURCE);
    }

    @Test
    void cancelledLessonBoundaryAtNinetyDays() {
        LessonReport report = lesson(ReportStatus.CANCELLED, null, BASE);
        assertThat(report.retentionAction(POLICY, after(BASE, 89))).isEqualTo(RetentionDecision.KEEP);
        assertThat(report.retentionAction(POLICY, after(BASE, 90)))
                .isEqualTo(RetentionDecision.ANONYMIZE);
        assertThat(report.retentionAction(POLICY, after(BASE, 91)))
                .isEqualTo(RetentionDecision.ANONYMIZE);
    }

    @Test
    void openLessonReportsAreAlwaysKept() {
        Instant far = after(BASE, 4000);
        for (ReportStatus status :
                List.of(
                        ReportStatus.PROCESSING,
                        ReportStatus.AWAITING_ANSWERS,
                        ReportStatus.AWAITING_REVIEW,
                        ReportStatus.PROCESSING_FAILED)) {
            assertThat(lesson(status, null, null).retentionAction(POLICY, far))
                    .as(status.name())
                    .isEqualTo(RetentionDecision.KEEP);
        }
    }

    @Test
    void handledLessonIsKeptAndNotTreatedTwice() {
        LessonReport approved = lesson(ReportStatus.APPROVED, BASE, null);
        approved.removeSource(after(BASE, 30));
        assertThat(approved.transcription()).isNull();
        assertThat(approved.retentionAction(POLICY, after(BASE, 400)))
                .isEqualTo(RetentionDecision.KEEP);

        LessonReport cancelled = lesson(ReportStatus.CANCELLED, null, BASE);
        cancelled.anonymize(after(BASE, 90));
        assertThat(cancelled.anonymizedAt()).isNotNull();
        assertThat(cancelled.retentionAction(POLICY, after(BASE, 400)))
                .isEqualTo(RetentionDecision.KEEP);
    }

    @Test
    void lessonWithoutSourceNeedsRecordedRemoval() {
        assertThatThrownBy(
                        () ->
                                new LessonReport(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        null,
                                        LessonReportContent.empty(),
                                        List.of(),
                                        0,
                                        ReportStatus.APPROVED,
                                        null,
                                        1,
                                        Channel.WEB,
                                        null,
                                        BASE,
                                        BASE,
                                        BASE,
                                        1,
                                        Channel.WEB,
                                        null,
                                        0,
                                        0,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approvedSemesterRemovesOnlySourceAndKeepsGeneralInformation() {
        SemesterReport report = semester(ReportStatus.APPROVED, BASE, null);
        assertThat(report.retentionAction(POLICY, after(BASE, 29))).isEqualTo(RetentionDecision.KEEP);
        assertThat(report.retentionAction(POLICY, after(BASE, 30)))
                .isEqualTo(RetentionDecision.REMOVE_SOURCE);
        report.removeSource(after(BASE, 30));
        assertThat(report.generalInformation()).isNotNull();
        assertThat(report.sourceRemovedAt()).isNotNull();
        assertThat(report.retentionAction(POLICY, after(BASE, 500))).isEqualTo(RetentionDecision.KEEP);
    }

    @Test
    void cancelledSemesterIsAnonymizedAndClearsGeneralInformation() {
        SemesterReport report = semester(ReportStatus.CANCELLED, null, BASE);
        assertThat(report.retentionAction(POLICY, after(BASE, 89))).isEqualTo(RetentionDecision.KEEP);
        assertThat(report.retentionAction(POLICY, after(BASE, 90)))
                .isEqualTo(RetentionDecision.ANONYMIZE);
        report.anonymize(after(BASE, 90));
        assertThat(report.generalInformation()).isNull();
        assertThat(report.anonymizedAt()).isNotNull();
        assertThat(report.retentionAction(POLICY, after(BASE, 500))).isEqualTo(RetentionDecision.KEEP);
    }

    @Test
    void openSemesterIsKept() {
        assertThat(
                        semester(ReportStatus.AWAITING_REVIEW, null, null)
                                .retentionAction(POLICY, after(BASE, 4000)))
                .isEqualTo(RetentionDecision.KEEP);
    }

    @Test
    void semesterWithoutGeneralInformationNeedsAnonymization() {
        assertThatThrownBy(
                        () ->
                                new SemesterReport(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        new SemesterPeriod(2026, 1),
                                        1,
                                        0,
                                        null,
                                        SemesterReportContent.empty(),
                                        ReportStatus.APPROVED,
                                        null,
                                        1,
                                        Channel.WEB,
                                        BASE,
                                        BASE,
                                        BASE,
                                        1,
                                        Channel.WEB,
                                        null,
                                        0,
                                        0,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pdfOfApprovedVersionIsNeverRemovable() {
        PdfDocument pdf = PdfDocument.create(UUID.randomUUID(), DocumentType.OFFICIAL, "k", 1, "h", BASE);
        assertThat(pdf.retentionAction(POLICY, after(BASE, 4000), true, BASE))
                .isEqualTo(RetentionDecision.KEEP);
    }

    @Test
    void pdfOfOtherVersionBoundaryAtThirtyDays() {
        PdfDocument pdf = PdfDocument.create(UUID.randomUUID(), DocumentType.OFFICIAL, "k", 1, "h", BASE);
        assertThat(pdf.retentionAction(POLICY, after(BASE, 29), false, BASE))
                .isEqualTo(RetentionDecision.KEEP);
        assertThat(pdf.retentionAction(POLICY, after(BASE, 30), false, BASE))
                .isEqualTo(RetentionDecision.REMOVE_UNAPPROVED_PDFS);
    }

    @Test
    void pdfOfOpenReportOrAlreadyRemovedIsKept() {
        PdfDocument pdf = PdfDocument.create(UUID.randomUUID(), DocumentType.OFFICIAL, "k", 1, "h", BASE);
        assertThat(pdf.retentionAction(POLICY, after(BASE, 4000), false, null))
                .isEqualTo(RetentionDecision.KEEP);
        PdfDocument removed = pdf.withContentRemoved(after(BASE, 31));
        assertThat(removed.retentionAction(POLICY, after(BASE, 4000), false, BASE))
                .isEqualTo(RetentionDecision.KEEP);
        assertThat(removed.contentRemovedAt()).isNotNull();
    }
}
