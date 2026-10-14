package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiCall;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.JobStatus;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.DocumentType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.report.VersionOrigin;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.AudioNotProcessableException;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.port.InvalidAiOutputException;
import br.com.felipeblaft.reports.port.PdfGenerationPort.DocumentStatus;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.TaskInput;
import br.com.felipeblaft.reports.port.TaskLlm;
import br.com.felipeblaft.reports.port.UnsupportedFormatException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ProcessadorTrabalhoServico: registro de aula e nova tentativa. */
class RegistrationJobProcessorTest {

    private final ReportScenario c = new ReportScenario();

    private ProcessingJob registerAndProcess(Channel channel) {
        ProcessingJob job =
                c.register.run(
                        c.clara.id(),
                        c.matteo.id(),
                        ReportScenario.file("lesson.ogg", 10),
                        channel);
        c.processQueue();
        return c.queue.searchById(job.id()).orElseThrow();
    }

    @Test
    void successWithoutQuestionsCreatesLessonReportVersionOneAndPdf() {
        c.transcription.schedule("Hoje trabalhamos o Czerny com o aluno.");
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, ReportScenario.output("Evoluiu.", List.of()));

        ProcessingJob job = registerAndProcess(Channel.WEB);

        assertThat(job.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.result()).isEqualTo(JobResult.READY_FOR_REVIEW);
        LessonReport report = c.reports.searchById(job.reportId()).orElseThrow();
        assertThat(report.status()).isEqualTo(ReportStatus.AWAITING_REVIEW);
        assertThat(report.transcription()).isEqualTo("Hoje trabalhamos o Czerny com o aluno.");
        assertThat(report.originChannel()).isEqualTo(Channel.WEB);
        assertThat(c.versions.versions)
                .singleElement()
                .satisfies(
                        v -> {
                            assertThat(v.origin()).isEqualTo(VersionOrigin.INITIAL_STRUCTURING);
                            assertThat(v.promptVersion()).isEqualTo("lesson-structuring.v1");
                        });
        assertThat(c.pdfDocuments.documents.values())
                .singleElement()
                .satisfies(d -> assertThat(d.type()).isEqualTo(DocumentType.REVIEW));
        assertThat(c.pdf.models.getFirst().status()).isEqualTo(DocumentStatus.REVIEW);
        assertThat(c.pdf.models.getFirst().studentName()).isEqualTo("Matteo Mafra");
        assertThat(c.storage.files).containsKey("reports/lesson/" + report.id() + "/v1.pdf");
        assertThat(c.notification.events)
                .containsExactly(
                        "step:UPLOADING",
                        "step:TRANSCRIBING",
                        "step:STRUCTURING",
                        "result:READY_FOR_REVIEW");
        assertThat(c.costs.calls)
                .extracting(AiCall::task)
                .containsExactly(AiTask.TRANSCRIPTION, AiTask.STRUCTURING);
        assertThat(c.costs.calls).allMatch(AiCall::success);
        assertThat(report.processingTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void initialTranscriptionStaysLinkedToCreatedReport() {
        c.transcription.schedule("Hoje trabalhamos o Czerny com o aluno.");
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, ReportScenario.output("x", List.of()));

        ProcessingJob job = registerAndProcess(Channel.WEB);

        assertThat(job.reportId()).isNotNull();
        assertThat(c.costs.calls)
                .extracting(AiCall::task)
                .containsExactly(AiTask.TRANSCRIPTION, AiTask.STRUCTURING);
        assertThat(c.costs.calls)
                .allSatisfy(
                        call -> {
                            assertThat(call.reportType()).isEqualTo(ReportType.LESSON);
                            assertThat(call.reportId()).isEqualTo(job.reportId());
                        });
    }

    @Test
    void transcriptionThatFailsWithoutReportStaysUnlinked() {
        c.normalization.failWith(new UnsupportedFormatException());

        ProcessingJob job = registerAndProcess(Channel.WEB);

        assertThat(job.reportId()).isNull();
        assertThat(c.costs.calls)
                .singleElement()
                .satisfies(
                        call -> {
                            assertThat(call.reportType()).isNull();
                            assertThat(call.reportId()).isNull();
                        });
    }

    @Test
    void invalidOutputRegistersTokensAndCostOfModelCalls() {
        c.transcription.schedule("Transcrição preservada.");
        c.llm.schedule(
                TaskLlm.LESSON_STRUCTURING,
                new InvalidAiOutputException("schema", 1200, 340, new BigDecimal("0.0123")));

        ProcessingJob job = registerAndProcess(Channel.WEB);

        assertThat(job.result()).isEqualTo(JobResult.STRUCTURING_FAILED);
        AiCall failure = c.costs.calls.getLast();
        assertThat(failure.task()).isEqualTo(AiTask.STRUCTURING);
        assertThat(failure.success()).isFalse();
        assertThat(failure.errorCode()).isEqualTo("INVALID_OUTPUT");
        assertThat(failure.inputTokens()).isEqualTo(1200);
        assertThat(failure.outputTokens()).isEqualTo(340);
        assertThat(failure.estimatedCostUsd()).isEqualByComparingTo("0.0123");
        assertThat(failure.reportId()).isEqualTo(job.reportId());
    }

    @Test
    void providerDownAfterAlreadyConsumingRegistersWhatWasSpent() {
        c.transcription.schedule("x");
        c.llm.schedule(
                TaskLlm.LESSON_STRUCTURING,
                new ProviderUnavailableException("503", 1000, 500, new BigDecimal("0.006")));

        registerAndProcess(Channel.WEB);

        AiCall failure = c.costs.calls.getLast();
        assertThat(failure.success()).isFalse();
        assertThat(failure.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(failure.inputTokens()).isEqualTo(1000);
        assertThat(failure.outputTokens()).isEqualTo(500);
        assertThat(failure.estimatedCostUsd()).isEqualByComparingTo("0.006");
    }

    @Test
    void unavailableProviderHasNoTokensNorCost() {
        c.transcription.schedule("x");
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, new ProviderUnavailableException("503"));

        registerAndProcess(Channel.WEB);

        AiCall failure = c.costs.calls.getLast();
        assertThat(failure.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(failure.inputTokens()).isNull();
        assertThat(failure.outputTokens()).isNull();
        assertThat(failure.estimatedCostUsd()).isEqualByComparingTo("0");
    }

    @Test
    void audioDeletedOnAllPathsAndAiInputWithoutStudentData() {
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, ReportScenario.output("x", List.of()));
        registerAndProcess(Channel.WEB);

        assertThat(c.normalization.deleted)
                .contains(
                        Path.of("/tmp/audios/lesson.ogg"),
                        Path.of("/tmp/audios/lesson.ogg.normalized.ogg"));
        assertThat(c.llm.inputs)
                .singleElement()
                .isInstanceOf(TaskInput.LessonStructuring.class)
                .asString()
                .doesNotContain("Matteo", "Mafra");
        assertThat(c.queue.jobs.values()).allMatch(t -> t.temporaryFile() == null);
    }

    @Test
    void withQuestionsAwaitsAnswersWithoutPdf() {
        c.llm.schedule(
                TaskLlm.LESSON_STRUCTURING, ReportScenario.output("x", List.of("Qual peça?")));

        ProcessingJob job = registerAndProcess(Channel.TELEGRAM);

        assertThat(job.result()).isEqualTo(JobResult.PENDING_QUESTIONS);
        LessonReport report = c.reports.searchById(job.reportId()).orElseThrow();
        assertThat(report.status()).isEqualTo(ReportStatus.AWAITING_ANSWERS);
        assertThat(report.pendingQuestions()).containsExactly("Qual peça?");
        assertThat(c.versions.versions).isEmpty();
        assertThat(c.pdf.models).isEmpty();
    }

    @Test
    void emptyTranscriptionOrInvalidFormatCreatesNothing() {
        c.transcription.schedule(
                new AudioNotProcessableException(AudioNotProcessableException.Reason.NO_SPEECH));
        ProcessingJob withoutSpeech = registerAndProcess(Channel.WEB);
        assertThat(withoutSpeech.result()).isEqualTo(JobResult.AUDIO_NOT_PROCESSABLE);
        assertThat(withoutSpeech.status()).isEqualTo(JobStatus.FAILED);

        c.normalization.failWith(new UnsupportedFormatException());
        ProcessingJob format = registerAndProcess(Channel.WEB);
        assertThat(format.result()).isEqualTo(JobResult.AUDIO_NOT_PROCESSABLE);

        c.transcription.schedule("   ");
        ProcessingJob empty = registerAndProcess(Channel.WEB);
        assertThat(empty.result()).isEqualTo(JobResult.AUDIO_NOT_PROCESSABLE);

        assertThat(c.reports.reports).isEmpty();
        assertThat(c.reports.lessons).isEmpty();
        assertThat(c.normalization.deleted).contains(Path.of("/tmp/audios/lesson.ogg"));
        assertThat(c.costs.calls).noneMatch(AiCall::success);
        assertThat(c.costs.calls)
                .extracting(AiCall::errorCode)
                .containsExactly("AUDIO_NO_SPEECH", "UNSUPPORTED_FORMAT", "AUDIO_NO_SPEECH");
    }

    @Test
    void unavailableProviderRetriesOnce() {
        c.transcription.schedule(
                new ProviderUnavailableException("503"), "Transcrição na segunda tentativa.");
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, ReportScenario.output("x", List.of()));
        assertThat(registerAndProcess(Channel.WEB).result()).isEqualTo(JobResult.READY_FOR_REVIEW);

        c.transcription.schedule(
                new ProviderUnavailableException("503"), new ProviderUnavailableException("503"));
        ProcessingJob failed =
                c.register.run(
                        c.clara.id(),
                        c.student(c.clara, "Olavo", "Violão").id(),
                        ReportScenario.file("b.ogg", 1),
                        Channel.WEB);
        c.processQueue();
        assertThat(c.queue.searchById(failed.id()).orElseThrow().result())
                .isEqualTo(JobResult.AUDIO_NOT_PROCESSABLE);
        assertThat(c.costs.calls.getLast().errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
    }

    @Test
    void telegramFileIsDownloadedOnlyDuringProcessing() {
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, ReportScenario.output("x", List.of()));
        c.register.run(
                c.clara.id(),
                c.matteo.id(),
                new ChannelFileReference("file-9", "u-9", "video/mp4", 31_000_000),
                Channel.TELEGRAM);
        assertThat(c.channelFile.obtained).isEmpty();
        c.processQueue();
        assertThat(c.channelFile.obtained)
                .extracting(ChannelFileReference::id)
                .containsExactly("file-9");
        assertThat(c.normalization.deleted).contains(Path.of("/tmp/audios/channel-file-9"));
    }

    @Test
    void invalidOutputFailsWithPreservedTranscriptionAndNewAttempt() {
        c.transcription.schedule("Transcrição preservada.");
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, new InvalidAiOutputException("schema"));
        ProcessingJob job = registerAndProcess(Channel.WEB);

        assertThat(job.result()).isEqualTo(JobResult.STRUCTURING_FAILED);
        LessonReport report = c.reports.searchById(job.reportId()).orElseThrow();
        assertThat(report.status()).isEqualTo(ReportStatus.PROCESSING_FAILED);
        assertThat(report.transcription()).isEqualTo("Transcrição preservada.");

        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, ReportScenario.output("x", List.of()));
        ProcessingJob newAttempt = c.retry.run(c.clara.id(), report.id(), Channel.TELEGRAM);
        int transcriptions = c.transcription.received.size();
        c.processQueue();

        assertThat(c.queue.searchById(newAttempt.id()).orElseThrow().result())
                .isEqualTo(JobResult.READY_FOR_REVIEW);
        assertThat(c.transcription.received).hasSize(transcriptions);
        assertThat(report.status()).isEqualTo(ReportStatus.AWAITING_REVIEW);
    }

    @Test
    void cancellationDuringJobDiscardsResult() {
        LessonReport failure = failureScenario();
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, ReportScenario.output("x", List.of()));
        ProcessingJob job = c.retry.run(c.clara.id(), failure.id(), Channel.WEB);
        ProcessingJob reserved = c.queue.reserveNext(c.clock.now()).orElseThrow();
        c.cancel.discard(c.clara.id(), failure.id());

        c.processor.process(reserved);

        assertThat(c.queue.searchById(job.id()).orElseThrow().status())
                .isEqualTo(JobStatus.DISCARDED);
        assertThat(failure.status()).isEqualTo(ReportStatus.CANCELLED);
        assertThat(c.versions.versions).isEmpty();
        assertThat(c.notification.events).doesNotContain("result:READY_FOR_REVIEW");
    }

    @Test
    void registrationDiscardedDuringTranscriptionDoesNotCreateReport() {
        ProcessingJob job =
                c.register.run(
                        c.clara.id(), c.matteo.id(), ReportScenario.file("a", 1), Channel.WEB);
        ProcessingJob reserved = c.queue.reserveNext(c.clock.now()).orElseThrow();
        reserved.discard(c.clock.now());

        c.processor.process(reserved);

        assertThat(c.reports.reports).isEmpty();
        assertThat(c.queue.searchById(job.id()).orElseThrow().status())
                .isEqualTo(JobStatus.DISCARDED);
    }

    @Test
    void unexpectedErrorFailsJobWithoutTrappingReport() {
        // sem resposta programada, o dublê lança IllegalStateException
        ProcessingJob job =
                c.register.run(
                        c.clara.id(), c.matteo.id(), ReportScenario.file("a", 1), Channel.WEB);
        ProcessingJob reserved = c.queue.reserveNext(c.clock.now()).orElseThrow();
        assertThatThrownBy(() -> c.processor.process(reserved))
                .isInstanceOf(IllegalStateException.class);

        ProcessingJob saved = c.queue.searchById(job.id()).orElseThrow();
        assertThat(saved.status()).isEqualTo(JobStatus.FAILED);
        assertThat(saved.result()).isEqualTo(JobResult.STRUCTURING_FAILED);
        assertThat(c.reports.searchById(saved.reportId()).orElseThrow().status())
                .isEqualTo(ReportStatus.PROCESSING_FAILED);
    }

    private LessonReport failureScenario() {
        c.llm.schedule(TaskLlm.LESSON_STRUCTURING, new ProviderUnavailableException("outside"));
        ProcessingJob job = registerAndProcess(Channel.WEB);
        return c.reports.searchById(job.reportId()).orElseThrow();
    }
}
