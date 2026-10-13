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
                        c.clara.id(), c.matteo.id(), ReportScenario.file("aula.ogg", 10), channel);
        c.processQueue();
        return c.queue.searchById(job.id()).orElseThrow();
    }

    @Test
    void successWithoutQuestionsCreatesLessonReportVersionOneAndPdf() {
        c.transcription.schedule("Hoje trabalhamos o Czerny com o aluno.");
        c.llm.schedule(TaskLlm.ESTRUTURACAO_AULA, ReportScenario.output("Evoluiu.", List.of()));

        ProcessingJob job = registerAndProcess(Channel.WEB);

        assertThat(job.status()).isEqualTo(JobStatus.CONCLUIDO);
        assertThat(job.result()).isEqualTo(JobResult.PRONTO_PARA_CONFERENCIA);
        LessonReport report = c.reports.searchById(job.reportId()).orElseThrow();
        assertThat(report.status()).isEqualTo(ReportStatus.AGUARDANDO_CONFERENCIA);
        assertThat(report.transcription()).isEqualTo("Hoje trabalhamos o Czerny com o aluno.");
        assertThat(report.originChannel()).isEqualTo(Channel.WEB);
        assertThat(c.versions.versions)
                .singleElement()
                .satisfies(
                        v -> {
                            assertThat(v.origin()).isEqualTo(VersionOrigin.ESTRUTURACAO_INICIAL);
                            assertThat(v.promptVersion()).isEqualTo("estruturacao-aula.v1");
                        });
        assertThat(c.pdfDocuments.documents.values())
                .singleElement()
                .satisfies(d -> assertThat(d.type()).isEqualTo(DocumentType.CONFERENCIA));
        assertThat(c.pdf.models.getFirst().status()).isEqualTo(DocumentStatus.CONFERENCIA);
        assertThat(c.pdf.models.getFirst().studentName()).isEqualTo("Matteo Mafra");
        assertThat(c.storage.files).containsKey("relatorios/aula/" + report.id() + "/v1.pdf");
        assertThat(c.notification.events)
                .containsExactly(
                        "etapa:ENVIANDO",
                        "etapa:TRANSCREVENDO",
                        "etapa:ESTRUTURANDO",
                        "resultado:PRONTO_PARA_CONFERENCIA");
        assertThat(c.costs.calls)
                .extracting(AiCall::task)
                .containsExactly(AiTask.TRANSCRICAO, AiTask.ESTRUTURACAO);
        assertThat(c.costs.calls).allMatch(AiCall::success);
        assertThat(report.processingTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void initialTranscriptionStaysLinkedToCreatedReport() {
        c.transcription.schedule("Hoje trabalhamos o Czerny com o aluno.");
        c.llm.schedule(TaskLlm.ESTRUTURACAO_AULA, ReportScenario.output("x", List.of()));

        ProcessingJob job = registerAndProcess(Channel.WEB);

        assertThat(job.reportId()).isNotNull();
        assertThat(c.costs.calls)
                .extracting(AiCall::task)
                .containsExactly(AiTask.TRANSCRICAO, AiTask.ESTRUTURACAO);
        assertThat(c.costs.calls)
                .allSatisfy(
                        call -> {
                            assertThat(call.reportType()).isEqualTo(ReportType.AULA);
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
                TaskLlm.ESTRUTURACAO_AULA,
                new InvalidAiOutputException("schema", 1200, 340, new BigDecimal("0.0123")));

        ProcessingJob job = registerAndProcess(Channel.WEB);

        assertThat(job.result()).isEqualTo(JobResult.FALHA_ESTRUTURACAO);
        AiCall failure = c.costs.calls.getLast();
        assertThat(failure.task()).isEqualTo(AiTask.ESTRUTURACAO);
        assertThat(failure.success()).isFalse();
        assertThat(failure.errorCode()).isEqualTo("SAIDA_INVALIDA");
        assertThat(failure.inputTokens()).isEqualTo(1200);
        assertThat(failure.outputTokens()).isEqualTo(340);
        assertThat(failure.estimatedCostUsd()).isEqualByComparingTo("0.0123");
        assertThat(failure.reportId()).isEqualTo(job.reportId());
    }

    @Test
    void providerDownAfterAlreadyConsumingRegistersWhatWasSpent() {
        c.transcription.schedule("x");
        c.llm.schedule(
                TaskLlm.ESTRUTURACAO_AULA,
                new ProviderUnavailableException("503", 1000, 500, new BigDecimal("0.006")));

        registerAndProcess(Channel.WEB);

        AiCall failure = c.costs.calls.getLast();
        assertThat(failure.success()).isFalse();
        assertThat(failure.errorCode()).isEqualTo("PROVEDOR_INDISPONIVEL");
        assertThat(failure.inputTokens()).isEqualTo(1000);
        assertThat(failure.outputTokens()).isEqualTo(500);
        assertThat(failure.estimatedCostUsd()).isEqualByComparingTo("0.006");
    }

    @Test
    void unavailableProviderHasNoTokensNorCost() {
        c.transcription.schedule("x");
        c.llm.schedule(TaskLlm.ESTRUTURACAO_AULA, new ProviderUnavailableException("503"));

        registerAndProcess(Channel.WEB);

        AiCall failure = c.costs.calls.getLast();
        assertThat(failure.errorCode()).isEqualTo("PROVEDOR_INDISPONIVEL");
        assertThat(failure.inputTokens()).isNull();
        assertThat(failure.outputTokens()).isNull();
        assertThat(failure.estimatedCostUsd()).isEqualByComparingTo("0");
    }

    @Test
    void audioDeletedOnAllPathsAndAiInputWithoutStudentData() {
        c.llm.schedule(TaskLlm.ESTRUTURACAO_AULA, ReportScenario.output("x", List.of()));
        registerAndProcess(Channel.WEB);

        assertThat(c.normalization.deleted)
                .contains(
                        Path.of("/tmp/audios/aula.ogg"),
                        Path.of("/tmp/audios/aula.ogg.normalizado.ogg"));
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
                TaskLlm.ESTRUTURACAO_AULA, ReportScenario.output("x", List.of("Qual peça?")));

        ProcessingJob job = registerAndProcess(Channel.TELEGRAM);

        assertThat(job.result()).isEqualTo(JobResult.PERGUNTAS_PENDENTES);
        LessonReport report = c.reports.searchById(job.reportId()).orElseThrow();
        assertThat(report.status()).isEqualTo(ReportStatus.AGUARDANDO_RESPOSTAS);
        assertThat(report.pendingQuestions()).containsExactly("Qual peça?");
        assertThat(c.versions.versions).isEmpty();
        assertThat(c.pdf.models).isEmpty();
    }

    @Test
    void emptyTranscriptionOrInvalidFormatCreatesNothing() {
        c.transcription.schedule(
                new AudioNotProcessableException(AudioNotProcessableException.Reason.SEM_FALA));
        ProcessingJob withoutSpeech = registerAndProcess(Channel.WEB);
        assertThat(withoutSpeech.result()).isEqualTo(JobResult.AUDIO_NAO_PROCESSAVEL);
        assertThat(withoutSpeech.status()).isEqualTo(JobStatus.FALHOU);

        c.normalization.failWith(new UnsupportedFormatException());
        ProcessingJob format = registerAndProcess(Channel.WEB);
        assertThat(format.result()).isEqualTo(JobResult.AUDIO_NAO_PROCESSAVEL);

        c.transcription.schedule("   ");
        ProcessingJob empty = registerAndProcess(Channel.WEB);
        assertThat(empty.result()).isEqualTo(JobResult.AUDIO_NAO_PROCESSAVEL);

        assertThat(c.reports.reports).isEmpty();
        assertThat(c.reports.lessons).isEmpty();
        assertThat(c.normalization.deleted).contains(Path.of("/tmp/audios/aula.ogg"));
        assertThat(c.costs.calls).noneMatch(AiCall::success);
        assertThat(c.costs.calls)
                .extracting(AiCall::errorCode)
                .containsExactly("AUDIO_SEM_FALA", "FORMATO_NAO_SUPORTADO", "AUDIO_SEM_FALA");
    }

    @Test
    void unavailableProviderRetriesOnce() {
        c.transcription.schedule(
                new ProviderUnavailableException("503"), "Transcrição na segunda tentativa.");
        c.llm.schedule(TaskLlm.ESTRUTURACAO_AULA, ReportScenario.output("x", List.of()));
        assertThat(registerAndProcess(Channel.WEB).result())
                .isEqualTo(JobResult.PRONTO_PARA_CONFERENCIA);

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
                .isEqualTo(JobResult.AUDIO_NAO_PROCESSAVEL);
        assertThat(c.costs.calls.getLast().errorCode()).isEqualTo("PROVEDOR_INDISPONIVEL");
    }

    @Test
    void telegramFileIsDownloadedOnlyDuringProcessing() {
        c.llm.schedule(TaskLlm.ESTRUTURACAO_AULA, ReportScenario.output("x", List.of()));
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
        assertThat(c.normalization.deleted).contains(Path.of("/tmp/audios/canal-file-9"));
    }

    @Test
    void invalidOutputFailsWithPreservedTranscriptionAndNewAttempt() {
        c.transcription.schedule("Transcrição preservada.");
        c.llm.schedule(TaskLlm.ESTRUTURACAO_AULA, new InvalidAiOutputException("schema"));
        ProcessingJob job = registerAndProcess(Channel.WEB);

        assertThat(job.result()).isEqualTo(JobResult.FALHA_ESTRUTURACAO);
        LessonReport report = c.reports.searchById(job.reportId()).orElseThrow();
        assertThat(report.status()).isEqualTo(ReportStatus.FALHA_PROCESSAMENTO);
        assertThat(report.transcription()).isEqualTo("Transcrição preservada.");

        c.llm.schedule(TaskLlm.ESTRUTURACAO_AULA, ReportScenario.output("x", List.of()));
        ProcessingJob newAttempt = c.retry.run(c.clara.id(), report.id(), Channel.TELEGRAM);
        int transcriptions = c.transcription.received.size();
        c.processQueue();

        assertThat(c.queue.searchById(newAttempt.id()).orElseThrow().result())
                .isEqualTo(JobResult.PRONTO_PARA_CONFERENCIA);
        assertThat(c.transcription.received).hasSize(transcriptions);
        assertThat(report.status()).isEqualTo(ReportStatus.AGUARDANDO_CONFERENCIA);
    }

    @Test
    void cancellationDuringJobDiscardsResult() {
        LessonReport failure = failureScenario();
        c.llm.schedule(TaskLlm.ESTRUTURACAO_AULA, ReportScenario.output("x", List.of()));
        ProcessingJob job = c.retry.run(c.clara.id(), failure.id(), Channel.WEB);
        ProcessingJob reserved = c.queue.reserveNext(c.clock.now()).orElseThrow();
        c.cancel.discard(c.clara.id(), failure.id());

        c.processor.process(reserved);

        assertThat(c.queue.searchById(job.id()).orElseThrow().status())
                .isEqualTo(JobStatus.DESCARTADO);
        assertThat(failure.status()).isEqualTo(ReportStatus.CANCELADO);
        assertThat(c.versions.versions).isEmpty();
        assertThat(c.notification.events).doesNotContain("resultado:PRONTO_PARA_CONFERENCIA");
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
                .isEqualTo(JobStatus.DESCARTADO);
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
        assertThat(saved.status()).isEqualTo(JobStatus.FALHOU);
        assertThat(saved.result()).isEqualTo(JobResult.FALHA_ESTRUTURACAO);
        assertThat(c.reports.searchById(saved.reportId()).orElseThrow().status())
                .isEqualTo(ReportStatus.FALHA_PROCESSAMENTO);
    }

    private LessonReport failureScenario() {
        c.llm.schedule(TaskLlm.ESTRUTURACAO_AULA, new ProviderUnavailableException("fora"));
        ProcessingJob job = registerAndProcess(Channel.WEB);
        return c.reports.searchById(job.reportId()).orElseThrow();
    }
}
