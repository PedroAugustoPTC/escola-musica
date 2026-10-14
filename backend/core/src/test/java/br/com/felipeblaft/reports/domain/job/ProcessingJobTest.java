package br.com.felipeblaft.reports.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessingJobTest {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");

    private static ProcessingJob create() {
        return ProcessingJob.create(
                JobType.LESSON_REGISTRATION,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                Channel.WEB,
                Medium.AUDIO,
                JobStep.UPLOADING,
                T0);
    }

    @Test
    void cycle() {
        ProcessingJob job = create();
        assertThat(job.status()).isEqualTo(JobStatus.PENDING);
        job.start(T0.plusSeconds(1));
        assertThat(job.status()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.attempts()).isEqualTo(1);
        assertThatThrownBy(() -> job.start(T0)).isInstanceOf(IllegalStateException.class);
        job.advance(JobStep.TRANSCRIBING);
        UUID report = UUID.randomUUID();
        job.linkReport(ReportType.LESSON, report);
        assertThat(job.resultingReportId()).isEqualTo(report);
        job.complete(JobResult.READY_FOR_REVIEW, T0.plusSeconds(31));
        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.step()).isEqualTo(JobStep.READY);
        assertThat(job.durationMs()).isEqualTo(30_000);
        assertThat(job.discard(T0)).isFalse();
        assertThat(job.toString()).contains("LESSON_REGISTRATION");
    }

    @Test
    void failureAndDiscard() {
        ProcessingJob failed = create();
        failed.start(T0);
        failed.complete(JobResult.AUDIO_NOT_PROCESSABLE, T0);
        assertThat(failed.status()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.step()).isEqualTo(JobStep.UPLOADING);

        ProcessingJob discarded = create();
        assertThat(discarded.discard(T0)).isTrue();
        assertThat(discarded.status()).isEqualTo(JobStatus.DISCARDED);
        assertThat(discarded.result()).isEqualTo(JobResult.DISCARDED);
        assertThat(discarded.durationMs()).isZero();

        ProcessingJob completedDiscarded = create();
        completedDiscarded.complete(JobResult.DISCARDED, T0);
        assertThat(completedDiscarded.status()).isEqualTo(JobStatus.DISCARDED);
    }

    @Test
    void delayAtThreeMinutesAndNotice() {
        ProcessingJob job = create();
        assertThat(job.delayed(T0.plus(Duration.ofSeconds(179)))).isFalse();
        assertThat(job.delayed(T0.plus(Duration.ofMinutes(3)))).isTrue();
        job.markDelayNotice();
        assertThat(job.delayNoticeSent()).isTrue();
        job.complete(JobResult.NEW_VERSION, T0);
        assertThat(job.delayed(T0.plus(Duration.ofMinutes(5)))).isFalse();
    }

    @Test
    void parametersAndFileAndReturnToQueue() {
        ProcessingJob job = create();
        job.defineParameter(ProcessingJob.TEXT, "a");
        job.defineParameter(ProcessingJob.TEXT, null);
        assertThat(job.parameter(ProcessingJob.TEXT)).isEmpty();
        job.defineParameter(ProcessingJob.BASE_VERSION, "2");
        assertThat(job.parameters()).containsEntry("baseVersion", "2");
        job.defineTemporaryFile("/tmp/audios/a.ogg");
        assertThat(job.temporaryFile()).isEqualTo("/tmp/audios/a.ogg");
        job.returnToQueue();
        assertThat(job.status()).isEqualTo(JobStatus.PENDING);
        job.start(T0);
        job.returnToQueue();
        assertThat(job.status()).isEqualTo(JobStatus.PENDING);
        job.start(T0.plusSeconds(10));
        assertThat(job.attempts()).isEqualTo(2);
        assertThat(job.startedAt()).isEqualTo(T0);
        assertThat(JobResult.NEW_VERSION.success()).isTrue();
        assertThat(JobStatus.FAILED.inProgress()).isFalse();
    }
}
