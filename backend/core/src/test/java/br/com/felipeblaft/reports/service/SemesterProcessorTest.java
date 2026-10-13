package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiCall;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.JobStatus;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.InteractionResult;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.AiSemesterReportOutput;
import br.com.felipeblaft.reports.port.AudioNotProcessableException;
import br.com.felipeblaft.reports.port.InvalidAiOutputException;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.TaskLlm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Caminhos de falha, descarte e concorrência do processamento do semestral (FR-045, FR-022). */
class SemesterProcessorTest {

    private final ReportScenario c = new ReportScenario();

    private SemesterReport inReview() {
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        c.llm.schedule(
                TaskLlm.CONSOLIDACAO_SEMESTRAL, ReportScenario.semesterOutput("Bom semestre."));
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);
        c.processQueue();
        return c.semester.reports.values().iterator().next();
    }

    private ProcessingJob last() {
        return c.queue.jobs.values().stream().reduce((a, b) -> b).orElseThrow();
    }

    private InteractionResult lastInteraction(SemesterReport report) {
        return c.interactions.last(ReportType.SEMESTRAL, report.id()).orElseThrow().result();
    }

    @Test
    void consolidationStaysLinkedToCreatedSemesterReport() {
        SemesterReport report = inReview();

        assertThat(c.costs.calls)
                .singleElement()
                .satisfies(
                        call -> {
                            assertThat(call.task()).isEqualTo(AiTask.CONSOLIDACAO);
                            assertThat(call.success()).isTrue();
                            assertThat(call.reportType()).isEqualTo(ReportType.SEMESTRAL);
                            assertThat(call.reportId()).isEqualTo(report.id());
                        });
    }

    @Test
    void consolidationThatFailsWithoutReportStaysUnlinkedAndKeepsTokensAndCost() {
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        c.llm.schedule(
                TaskLlm.CONSOLIDACAO_SEMESTRAL,
                new InvalidAiOutputException("schema", 900, 120, new BigDecimal("0.0042")));
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);

        c.processQueue();

        assertThat(last().result()).isEqualTo(JobResult.FALHA_CONSOLIDACAO);
        assertThat(c.semester.reports).isEmpty();
        AiCall failure = c.costs.calls.getLast();
        assertThat(failure.success()).isFalse();
        assertThat(failure.errorCode()).isEqualTo("SAIDA_INVALIDA");
        assertThat(failure.reportId()).isNull();
        assertThat(failure.inputTokens()).isEqualTo(900);
        assertThat(failure.outputTokens()).isEqualTo(120);
        assertThat(failure.estimatedCostUsd()).isEqualByComparingTo("0.0042");
    }

    @Test
    void lessonsVanishedBeforeConsolidationFailWithoutCallingAi() {
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);
        c.reports.reports.clear();

        c.processQueue();

        assertThat(last().result()).isEqualTo(JobResult.FALHA_CONSOLIDACAO);
        assertThat(c.llm.inputs).isEmpty();
    }

    @Test
    void semesterOpenInOtherChannelDuringConsolidationDoesNotDuplicate() {
        c.approvedLesson(c.matteo, LocalDate.of(2026, 3, 10), "Escala");
        c.llm.schedule(TaskLlm.CONSOLIDACAO_SEMESTRAL, ReportScenario.semesterOutput("Bom."));
        c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-1", false, Channel.WEB);
        SemesterPeriod period = SemesterPeriod.of("2026-1");
        c.semester.save(
                SemesterReport.create(
                        c.clara.id(),
                        c.matteo.id(),
                        period,
                        1,
                        0,
                        GeneralInformation.calculate(
                                "Matteo Mafra",
                                LocalDate.of(2012, 5, 10),
                                "Piano",
                                "Clara Souza",
                                period,
                                1,
                                LocalDate.of(2026, 9, 21)),
                        SemesterReportContent.empty(),
                        Channel.TELEGRAM,
                        c.clock.now()));

        c.processQueue();

        assertThat(c.semester.reports).hasSize(1);
        assertThat(last().result()).isEqualTo(JobResult.FALHA_CONSOLIDACAO);
    }

    @Test
    void changeByAudioWithTranscriptionAndUnderstoodText() {
        SemesterReport report = inReview();
        c.transcription.schedule("Parecer: muito bom.");
        c.llm.schedule(TaskLlm.REVISAO_SEMESTRAL, ReportScenario.semesterOutput("Muito bom."));

        c.changeSemester.changeByAudio(
                c.clara.id(), report.id(), ReportScenario.file("pedido.ogg", 100), 1, Channel.WEB);
        c.processQueue();

        assertThat(report.currentVersion()).isEqualTo(2);
        assertThat(last().medium()).isEqualTo(Medium.AUDIO);
        assertThat(last().parameter(ProcessingJob.UNDERSTOOD_TEXT)).contains("Parecer: muito bom.");
        assertThat(c.normalization.deleted).isNotEmpty();
    }

    @Test
    void requestTranscriptionFailsWithoutNewVersion() {
        SemesterReport report = inReview();
        c.transcription.schedule(
                new AudioNotProcessableException(AudioNotProcessableException.Reason.SEM_FALA));

        c.changeSemester.changeByAudio(
                c.clara.id(),
                report.id(),
                ReportScenario.file("mudo.ogg", 100),
                1,
                Channel.TELEGRAM);
        c.processQueue();

        assertThat(last().result()).isEqualTo(JobResult.FALHA_ALTERACAO);
        assertThat(report.currentVersion()).isEqualTo(1);
        assertThat(report.step()).isNull();
    }

    @Test
    void aiFailureInRevisionRegistersInteractionAndNoChangeDoesNotCreateVersion() {
        SemesterReport report = inReview();
        c.llm.schedule(
                TaskLlm.REVISAO_SEMESTRAL,
                new ProviderUnavailableException("teste"),
                AiSemesterReportOutput.of(report.content(), false, "NENHUM"));

        c.changeSemester.changeByText(c.clara.id(), report.id(), "Mude algo.", 1, Channel.WEB);
        c.processQueue();
        assertThat(last().result()).isEqualTo(JobResult.FALHA_ALTERACAO);
        assertThat(lastInteraction(report)).isEqualTo(InteractionResult.FALHOU);

        c.changeSemester.changeByText(c.clara.id(), report.id(), "Mude de novo.", 1, Channel.WEB);
        c.processQueue();
        assertThat(last().result()).isEqualTo(JobResult.SEM_ALTERACAO_DE_CONTEUDO);
        assertThat(lastInteraction(report)).isEqualTo(InteractionResult.SEM_ALTERACAO_DE_CONTEUDO);
        assertThat(report.currentVersion()).isEqualTo(1);
    }

    @Test
    void versionChangedInOtherChannelBeforeProcessingIsRefused() {
        SemesterReport report = inReview();
        c.changeSemester.changeByText(c.clara.id(), report.id(), "Mude.", 1, Channel.WEB);
        report.applyChange(SemesterReportContent.empty(), c.clock.now());

        c.processQueue();

        assertThat(last().result()).isEqualTo(JobResult.VERSAO_DESATUALIZADA);
        assertThat(lastInteraction(report))
                .isEqualTo(InteractionResult.RECUSADA_VERSAO_DESATUALIZADA);
    }

    @Test
    void cancellationDuringProcessingDiscardsResult() {
        SemesterReport report = inReview();
        c.changeSemester.changeByText(c.clara.id(), report.id(), "Mude.", 1, Channel.WEB);
        ProcessingJob job = c.queue.reserveNext(c.clock.now()).orElseThrow();
        report.cancel(c.clock.now());

        c.processor.process(job);

        assertThat(job.status()).isEqualTo(JobStatus.DESCARTADO);
        assertThat(report.currentVersion()).isEqualTo(1);
    }

    @Test
    void unexpectedErrorDoesNotLeaveSemesterStuckInStep() {
        SemesterReport report = inReview();
        c.llm.schedule(TaskLlm.REVISAO_SEMESTRAL, new IllegalStateException("falha inesperada"));
        c.changeSemester.changeByText(c.clara.id(), report.id(), "Mude.", 1, Channel.WEB);

        assertThatThrownBy(c::processQueue).isInstanceOf(IllegalStateException.class);

        assertThat(report.step()).isNull();
        assertThat(last().result()).isEqualTo(JobResult.FALHA_ALTERACAO);
    }

    @Test
    void discardOfJobThatIsNeitherRegistrationNorConsolidationIsRefused() {
        SemesterReport report = inReview();
        ProcessingJob change =
                c.changeSemester.changeByText(c.clara.id(), report.id(), "Mude.", 1, Channel.WEB);
        assertThatThrownBy(() -> c.discardRegistration.run(c.clara.id(), change.id()))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ACAO_NAO_PERMITIDA_NA_ETAPA));
        c.llm.schedule(TaskLlm.REVISAO_SEMESTRAL, ReportScenario.semesterOutput("Outro."));
        c.processQueue();

        c.reports.reports.clear();
        c.approvedLesson(c.matteo, LocalDate.of(2026, 9, 1), "Escala");
        ProcessingJob consolidation =
                c.generateSemester.run(c.clara.id(), c.matteo.id(), "2026-2", false, Channel.WEB);
        c.discardRegistration.run(c.clara.id(), consolidation.id());
        assertThat(consolidation.status()).isEqualTo(JobStatus.DESCARTADO);
        assertThatThrownBy(() -> c.discardRegistration.run(c.clara.id(), consolidation.id()))
                .isInstanceOf(DomainException.class);
        assertThat(consolidation.type()).isEqualTo(JobType.CONSOLIDACAO_SEMESTRAL);
        assertThat(consolidation.step()).isEqualTo(JobStep.CONSOLIDANDO);
    }

    @Test
    void audioAboveLimitInChangeIsRefusedAndDeleted() {
        SemesterReport report = inReview();
        var large = ReportScenario.file("grande.ogg", c.parameters.audioLimitBytes() + 1);

        assertThatThrownBy(
                        () ->
                                c.changeSemester.changeByAudio(
                                        c.clara.id(), report.id(), large, 1, Channel.WEB))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AUDIO_MUITO_GRANDE));
        assertThat(c.normalization.deleted).contains(large.path());
        assertThat(c.semesterProcessor.serves(last())).isTrue();
        assertThat(List.of(report.step() == null)).containsExactly(true);
    }
}
