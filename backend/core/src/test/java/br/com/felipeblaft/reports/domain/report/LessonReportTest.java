package br.com.felipeblaft.reports.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Transições e proibições da máquina de estados do relatório de aula (data-model.md §8.1). */
class LessonReportTest {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");
    private static final LessonReportContent CONTENT =
            new LessonReportContent(
                    List.of("Czerny"), "Evoluiu.", List.of(), List.of("Escala"), "");
    private static final LessonReportContent OTHER =
            new LessonReportContent(List.of("Czerny"), "Evoluiu muito.", List.of(), List.of(), "");

    private static LessonReport create() {
        Lesson lesson =
                Lesson.create(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 21), T0);
        return LessonReport.create(lesson, "  transcrição da aula  ", Channel.TELEGRAM, T0);
    }

    private static LessonReport inReview() {
        LessonReport report = create();
        report.registerStructuring(CONTENT, List.of(), T0);
        return report;
    }

    private static void failWith(ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(code);
    }

    @Test
    void creationRequiresTranscriptionAndStartsStructuring() {
        LessonReport report = create();
        assertThat(report.status()).isEqualTo(ReportStatus.PROCESSING);
        assertThat(report.step()).isEqualTo(ReportStep.STRUCTURING);
        assertThat(report.transcription()).isEqualTo("transcrição da aula");
        assertThat(report.currentVersion()).isZero();
        assertThat(report.originChannel()).isEqualTo(Channel.TELEGRAM);
        assertThat(report.content().isEmpty()).isTrue();
        assertThat(report.toString()).doesNotContain("transcrição");
        Lesson lesson =
                Lesson.create(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 21), T0);
        assertThatThrownBy(() -> LessonReport.create(lesson, "  ", Channel.WEB, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LessonReport.create(lesson, null, Channel.WEB, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void structuringWithoutQuestionsGoesToReviewAtVersionOne() {
        LessonReport report = create();
        assertThat(report.registerStructuring(CONTENT, List.of(" "), T0.plusSeconds(5))).isTrue();
        assertThat(report.status()).isEqualTo(ReportStatus.AWAITING_REVIEW);
        assertThat(report.currentVersion()).isEqualTo(1);
        assertThat(report.step()).isNull();
        assertThat(report.updatedAt()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void atMostTwoRoundsOfQuestions() {
        LessonReport report = create();
        assertThat(report.registerStructuring(CONTENT, List.of("P1", "P2"), T0)).isFalse();
        assertThat(report.status()).isEqualTo(ReportStatus.AWAITING_ANSWERS);
        assertThat(report.pendingQuestions()).containsExactly("P1", "P2");
        assertThat(report.questionRounds()).isEqualTo(1);

        assertThat(report.incorporateAnswer(CONTENT, List.of("P3"), T0)).isFalse();
        assertThat(report.questionRounds()).isEqualTo(2);
        assertThat(report.pendingQuestions()).containsExactly("P3");

        // na 2ª rodada, as perguntas que sobrarem são ignoradas
        assertThat(report.incorporateAnswer(OTHER, List.of("P4"), T0)).isTrue();
        assertThat(report.status()).isEqualTo(ReportStatus.AWAITING_REVIEW);
        assertThat(report.pendingQuestions()).isEmpty();
        assertThat(report.content()).isEqualTo(OTHER);
        assertThat(report.currentVersion()).isEqualTo(1);
    }

    @Test
    void structuringFailureAndRetry() {
        LessonReport report = create();
        report.failStructuring(T0);
        assertThat(report.status()).isEqualTo(ReportStatus.PROCESSING_FAILED);
        assertThat(report.failureReason()).isEqualTo(LessonReport.STRUCTURING_FAILED);
        assertThat(report.transcription()).isNotBlank();
        report.prepareNewAttempt(T0);
        assertThat(report.status()).isEqualTo(ReportStatus.PROCESSING);
        assertThat(report.step()).isEqualTo(ReportStep.STRUCTURING);
        assertThat(report.failureReason()).isNull();
        failWith(report::requireCanRetry, ErrorCode.PROCESSING_IN_PROGRESS);
        failWith(() -> inReview().requireCanRetry(), ErrorCode.ACTION_NOT_ALLOWED_AT_STEP);
    }

    @Test
    void changeCreatesNewVersionAndRequiresCurrentVersion() {
        LessonReport report = inReview();
        report.requireCanChange(1);
        failWith(() -> report.requireCanChange(0), ErrorCode.STALE_VERSION);
        report.startStep(ReportStep.APPLYING_CHANGE, T0);
        failWith(() -> report.requireCanChange(1), ErrorCode.PROCESSING_IN_PROGRESS);
        assertThat(report.applyChange(OTHER, T0)).isEqualTo(2);
        assertThat(report.step()).isNull();
        assertThatThrownBy(() -> report.requireCurrentVersion(1))
                .isInstanceOf(DomainException.class)
                .satisfies(
                        e ->
                                assertThat(((DomainException) e).details())
                                        .containsEntry("currentVersion", 2));
    }

    @Test
    void answerOnlyWhenAwaitingAnswersWithoutStep() {
        LessonReport report = create();
        report.registerStructuring(CONTENT, List.of("P1"), T0);
        report.requireCanAnswer();
        report.startStep(ReportStep.INCORPORATING_ANSWERS, T0);
        failWith(report::requireCanAnswer, ErrorCode.PROCESSING_IN_PROGRESS);
        report.completeStep(T0);
        failWith(() -> inReview().requireCanAnswer(), ErrorCode.ACTION_NOT_ALLOWED_AT_STEP);
    }

    @Test
    void approveOnlyCurrentVersionWithoutStepInProgress() {
        LessonReport report = inReview();
        failWith(() -> report.approve(2, Channel.WEB, T0), ErrorCode.STALE_VERSION);
        report.startStep(ReportStep.GENERATING_PDF, T0);
        failWith(() -> report.approve(1, Channel.WEB, T0), ErrorCode.PROCESSING_IN_PROGRESS);
        report.completeStep(T0);

        report.approve(1, Channel.TELEGRAM, T0.plusSeconds(60));

        assertThat(report.status()).isEqualTo(ReportStatus.APPROVED);
        assertThat(report.approvedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(report.approvedVersion()).isEqualTo(1);
        assertThat(report.approvalChannel()).isEqualTo(Channel.TELEGRAM);
        failWith(() -> report.approve(1, Channel.WEB, T0), ErrorCode.REPORT_ALREADY_APPROVED);
        failWith(() -> report.cancel(T0, false), ErrorCode.REPORT_ALREADY_APPROVED);
        failWith(() -> report.requireCanChange(1), ErrorCode.REPORT_ALREADY_APPROVED);
    }

    @Test
    void approveOutsideReview() {
        LessonReport report = create();
        report.registerStructuring(CONTENT, List.of("P1"), T0);
        failWith(() -> report.approve(0, Channel.WEB, T0), ErrorCode.ACTION_NOT_ALLOWED_AT_STEP);
    }

    @Test
    void cancelInProcessingOnlyByDiscard() {
        LessonReport report = create();
        failWith(() -> report.cancel(T0, false), ErrorCode.ACTION_NOT_ALLOWED_AT_STEP);
        report.cancel(T0, true);
        assertThat(report.status()).isEqualTo(ReportStatus.CANCELLED);
        assertThat(report.cancelledAt()).isEqualTo(T0);
        failWith(() -> report.cancel(T0, true), ErrorCode.REPORT_CANCELLED);
        failWith(report::requireOpen, ErrorCode.REPORT_CANCELLED);
    }

    @Test
    void cancelInReviewOrFailure() {
        LessonReport review = inReview();
        review.cancel(T0, false);
        assertThat(review.status()).isEqualTo(ReportStatus.CANCELLED);
        LessonReport failure = create();
        failure.failStructuring(T0);
        failure.cancel(T0, false);
        assertThat(failure.status()).isEqualTo(ReportStatus.CANCELLED);
    }

    @Test
    void invalidInternalTransitionsAreProgrammingError() {
        LessonReport report = inReview();
        assertThatThrownBy(() -> report.registerStructuring(CONTENT, List.of(), T0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> report.incorporateAnswer(CONTENT, List.of(), T0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> report.failStructuring(T0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> create().applyChange(CONTENT, T0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void processingTimeAndPersistedVersion() {
        LessonReport report = create();
        report.addProcessingTime(1500);
        report.addProcessingTime(-10);
        report.registerPersistedVersion(3);
        assertThat(report.processingTimeMs()).isEqualTo(1500);
        assertThat(report.version()).isEqualTo(3);
        assertThat(report.lessonId()).isNotNull();
        assertThat(report.createdAt()).isEqualTo(T0);
    }
}
