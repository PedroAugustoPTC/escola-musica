package br.com.felipeblaft.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.InteractionResult;
import br.com.felipeblaft.reports.domain.report.InteractionType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.report.VersionOrigin;
import br.com.felipeblaft.reports.doubles.ReportScenario;
import br.com.felipeblaft.reports.port.AudioNotProcessableException;
import br.com.felipeblaft.reports.port.InvalidAiOutputException;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.TaskInput;
import br.com.felipeblaft.reports.port.TaskLlm;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ProcessadorTrabalhoServico: respostas às perguntas e alterações, por texto e por áudio. */
class AnswersChangesJobProcessorTest {

    private final ReportScenario c = new ReportScenario();

    private JobResult result(ProcessingJob job) {
        c.processQueue();
        return c.queue.searchById(job.id()).orElseThrow().result();
    }

    // ------------------------------------------------------------ respostas

    @Test
    void answerByTextIncorporatedGoesToReview() {
        LessonReport report = c.reportWithQuestions();
        c.llm.schedule(
                TaskLlm.INCORPORACAO_RESPOSTAS,
                ReportScenario.output("Estudou três vezes.", List.of()));

        ProcessingJob job =
                c.answer.answerText(
                        c.clara.id(), report.id(), "Czerny; estudou três vezes.", Channel.WEB);

        assertThat(result(job)).isEqualTo(JobResult.PRONTO_PARA_CONFERENCIA);
        assertThat(report.status()).isEqualTo(ReportStatus.AGUARDANDO_CONFERENCIA);
        assertThat(report.content().evolucao()).isEqualTo("Estudou três vezes.");
        assertThat(c.versions.versions)
                .singleElement()
                .extracting(v -> v.origin())
                .isEqualTo(VersionOrigin.RESPOSTA_PERGUNTAS);
        ReportInteraction interaction = c.interactions.interactions.getFirst();
        assertThat(interaction.type()).isEqualTo(InteractionType.RESPOSTA_PERGUNTAS);
        assertThat(interaction.medium()).isEqualTo(Medium.TEXTO);
        assertThat(interaction.channel()).isEqualTo(Channel.WEB);
        assertThat(interaction.round()).isEqualTo(1);
        assertThat(c.versions.versions.getFirst().interactionId()).isEqualTo(interaction.id());
        TaskInput.AnswerIncorporation input =
                (TaskInput.AnswerIncorporation) c.llm.inputs.getLast();
        assertThat(input.perguntas())
                .containsExactly("Qual repertório foi trabalhado?", "Estudou em casa?");
        assertThat(input.resposta()).isEqualTo("Czerny; estudou três vezes.");
    }

    @Test
    void answerByAudioShowsUnderstoodTextAndSecondRound() {
        LessonReport report = c.reportWithQuestions();
        c.transcription.schedule("Foi o Czerny.");
        c.llm.schedule(
                TaskLlm.INCORPORACAO_RESPOSTAS, ReportScenario.output("x", List.of("E a escala?")));

        ProcessingJob job =
                c.answer.answerAudio(
                        c.clara.id(),
                        report.id(),
                        ReportScenario.file("resp.ogg", 10),
                        Channel.TELEGRAM);

        assertThat(result(job)).isEqualTo(JobResult.PERGUNTAS_PENDENTES);
        assertThat(c.queue.searchById(job.id()).orElseThrow().parameters())
                .containsEntry("textoEntendido", "Foi o Czerny.");
        assertThat(report.questionRounds()).isEqualTo(2);
        assertThat(report.step()).isNull();
        assertThat(c.interactions.interactions.getFirst().medium()).isEqualTo(Medium.AUDIO);
        assertThat(c.notification.events)
                .contains("etapa:TRANSCREVENDO_PEDIDO", "etapa:INCORPORANDO_RESPOSTAS");
    }

    @Test
    void secondAnswerWithNewQuestionsIncorporatesIgnoresExcessAndGoesToReview() {
        LessonReport report = c.reportWithQuestions();
        c.llm.schedule(
                TaskLlm.INCORPORACAO_RESPOSTAS,
                ReportScenario.output("Primeira rodada.", List.of("E a escala?")),
                ReportScenario.output("Segunda rodada.", List.of("Mais uma pergunta?")));

        ProcessingJob first =
                c.answer.answerText(c.clara.id(), report.id(), "Foi o Czerny.", Channel.WEB);
        assertThat(result(first)).isEqualTo(JobResult.PERGUNTAS_PENDENTES);
        assertThat(report.status()).isEqualTo(ReportStatus.AGUARDANDO_RESPOSTAS);
        assertThat(report.questionRounds()).isEqualTo(2);
        assertThat(c.versions.versions).isEmpty();

        ProcessingJob second =
                c.answer.answerText(c.clara.id(), report.id(), "Escala de Dó maior.", Channel.WEB);

        assertThat(result(second)).isEqualTo(JobResult.PRONTO_PARA_CONFERENCIA);
        assertThat(report.status()).isEqualTo(ReportStatus.AGUARDANDO_CONFERENCIA);
        assertThat(report.questionRounds()).isEqualTo(2);
        assertThat(report.pendingQuestions()).isEmpty();
        assertThat(report.content().evolucao()).isEqualTo("Segunda rodada.");
        assertThat(report.currentVersion()).isEqualTo(1);
        assertThat(c.versions.versions)
                .singleElement()
                .satisfies(
                        v -> {
                            assertThat(v.number()).isEqualTo(1);
                            assertThat(v.origin()).isEqualTo(VersionOrigin.RESPOSTA_PERGUNTAS);
                        });
        assertThat(c.pdfDocuments.documents.values()).hasSize(1);
        assertThat(c.interactions.interactions)
                .extracting(ReportInteraction::round)
                .containsExactly(1, 2);
        assertThat(c.interactions.interactions)
                .allMatch(i -> i.result() == InteractionResult.APLICADA);
        TaskInput.AnswerIncorporation input =
                (TaskInput.AnswerIncorporation) c.llm.inputs.getLast();
        assertThat(input.rodada()).isEqualTo(2);
        assertThat(input.perguntas()).containsExactly("E a escala?");
    }

    @Test
    void answerTranscriptionFailsWithoutInteractionAndWithoutConsumingRound() {
        LessonReport report = c.reportWithQuestions();
        c.transcription.schedule(
                new AudioNotProcessableException(AudioNotProcessableException.Reason.SEM_FALA));

        ProcessingJob job =
                c.answer.answerAudio(
                        c.clara.id(), report.id(), ReportScenario.file("r.ogg", 10), Channel.WEB);

        assertThat(result(job)).isEqualTo(JobResult.FALHA_RESPOSTA);
        assertThat(report.status()).isEqualTo(ReportStatus.AGUARDANDO_RESPOSTAS);
        assertThat(report.questionRounds()).isEqualTo(1);
        assertThat(report.step()).isNull();
        assertThat(c.interactions.interactions).isEmpty();
    }

    @Test
    void aiFailureInAnswerRegistersFailedInteraction() {
        LessonReport report = c.reportWithQuestions();
        c.llm.schedule(TaskLlm.INCORPORACAO_RESPOSTAS, new InvalidAiOutputException("x"));
        ProcessingJob job = c.answer.answerText(c.clara.id(), report.id(), "ok", Channel.WEB);
        assertThat(result(job)).isEqualTo(JobResult.FALHA_RESPOSTA);
        assertThat(c.interactions.interactions.getFirst().result())
                .isEqualTo(InteractionResult.FALHOU);
        assertThat(report.status()).isEqualTo(ReportStatus.AGUARDANDO_RESPOSTAS);
    }

    // ------------------------------------------------------------ alterações

    @Test
    void changeByTextCreatesNewVersionWithPdf() {
        LessonReport report = c.reportInReview();
        c.llm.schedule(
                TaskLlm.REVISAO_AULA,
                ReportScenario.revision("Leitura mais fácil.", false, "NENHUM"));

        ProcessingJob job =
                c.change.changeByText(
                        c.clara.id(),
                        report.id(),
                        "A leitura foi mais fácil.",
                        1,
                        Channel.TELEGRAM);

        assertThat(result(job)).isEqualTo(JobResult.NOVA_VERSAO);
        assertThat(report.currentVersion()).isEqualTo(2);
        assertThat(report.content().evolucao()).isEqualTo("Leitura mais fácil.");
        assertThat(c.versions.versions).hasSize(2);
        assertThat(c.versions.versions.getFirst().content())
                .isNotEqualTo(c.versions.versions.getLast().content());
        assertThat(c.pdf.models).extracting(m -> m.version()).containsExactly(1, 2);
        ReportInteraction interaction = c.interactions.interactions.getLast();
        assertThat(interaction.result()).isEqualTo(InteractionResult.APLICADA);
        assertThat(interaction.baseVersion()).isEqualTo(1);
        assertThat(interaction.resultingVersion()).isEqualTo(2);
    }

    @Test
    void changeByAudioReturnsUnderstoodText() {
        LessonReport report = c.reportInReview();
        c.transcription.schedule("Troque a observação para: estudou pouco.");
        c.llm.schedule(
                TaskLlm.REVISAO_AULA, ReportScenario.revision("Nova evolução.", false, "NENHUM"));

        ProcessingJob job =
                c.change.changeByAudio(
                        c.clara.id(),
                        report.id(),
                        ReportScenario.file("alt.ogg", 5),
                        1,
                        Channel.WEB);

        assertThat(result(job)).isEqualTo(JobResult.NOVA_VERSAO);
        assertThat(c.queue.searchById(job.id()).orElseThrow().parameters())
                .containsEntry("textoEntendido", "Troque a observação para: estudou pouco.");
        assertThat(((TaskInput.LessonRevision) c.llm.inputs.getLast()).pedido())
                .isEqualTo("Troque a observação para: estudou pouco.");
        assertThat(c.interactions.interactions.getLast().medium()).isEqualTo(Medium.AUDIO);
    }

    @Test
    void actionRequestNeverApprovesNorCancels() {
        LessonReport report = c.reportInReview();
        c.llm.schedule(
                TaskLlm.REVISAO_AULA,
                ReportScenario.revision("Evoluiu na articulação.", true, "APROVAR"));
        ProcessingJob job =
                c.change.changeByText(c.clara.id(), report.id(), "pode aprovar", 1, Channel.WEB);

        assertThat(result(job)).isEqualTo(JobResult.PEDIDO_DE_ACAO_IGNORADO);
        assertThat(report.status()).isEqualTo(ReportStatus.AGUARDANDO_CONFERENCIA);
        assertThat(report.currentVersion()).isEqualTo(1);
        assertThat(report.step()).isNull();
        assertThat(c.interactions.interactions.getLast().result())
                .isEqualTo(InteractionResult.PEDIDO_DE_ACAO_IGNORADO);
    }

    @Test
    void withoutContentChangeOrIdenticalContentDoesNotCreateVersion() {
        LessonReport report = c.reportInReview();
        c.llm.schedule(
                TaskLlm.REVISAO_AULA,
                ReportScenario.revision("x", true, "NENHUM"),
                ReportScenario.revision("Evoluiu na articulação.", false, null));

        ProcessingJob without =
                c.change.changeByText(c.clara.id(), report.id(), "hmm", 1, Channel.WEB);
        assertThat(result(without)).isEqualTo(JobResult.SEM_ALTERACAO_DE_CONTEUDO);
        ProcessingJob identical =
                c.change.changeByText(c.clara.id(), report.id(), "mantenha", 1, Channel.WEB);
        assertThat(result(identical)).isEqualTo(JobResult.SEM_ALTERACAO_DE_CONTEUDO);

        assertThat(report.currentVersion()).isEqualTo(1);
        assertThat(c.versions.versions).hasSize(1);
    }

    @Test
    void staleBaseVersionAtProcessingIsRefused() {
        LessonReport report = c.reportInReview();
        c.llm.schedule(TaskLlm.REVISAO_AULA, ReportScenario.revision("A.", false, "NENHUM"));
        ProcessingJob first = c.change.changeByText(c.clara.id(), report.id(), "a", 1, Channel.WEB);
        // um segundo pedido já na fila, feito sobre a versão 1, chega depois da versão 2
        report.completeStep(c.clock.now());
        ProcessingJob second =
                c.change.changeByText(c.clara.id(), report.id(), "b", 1, Channel.TELEGRAM);

        assertThat(result(first)).isEqualTo(JobResult.NOVA_VERSAO);
        assertThat(c.queue.searchById(second.id()).orElseThrow().result())
                .isEqualTo(JobResult.VERSAO_DESATUALIZADA);
        assertThat(c.interactions.interactions.getLast().result())
                .isEqualTo(InteractionResult.RECUSADA_VERSAO_DESATUALIZADA);
        assertThat(report.step()).isNull();
    }

    @Test
    void changeFailuresKeepVersion() {
        LessonReport report = c.reportInReview();
        c.transcription.schedule(
                new AudioNotProcessableException(AudioNotProcessableException.Reason.VAZIO));
        ProcessingJob audio =
                c.change.changeByAudio(
                        c.clara.id(), report.id(), ReportScenario.file("v.ogg", 1), 1, Channel.WEB);
        assertThat(result(audio)).isEqualTo(JobResult.FALHA_ALTERACAO);
        assertThat(c.interactions.interactions).isEmpty();

        c.llm.schedule(TaskLlm.REVISAO_AULA, new ProviderUnavailableException("503"));
        ProcessingJob ai = c.change.changeByText(c.clara.id(), report.id(), "x", 1, Channel.WEB);
        assertThat(result(ai)).isEqualTo(JobResult.FALHA_ALTERACAO);
        assertThat(c.interactions.interactions.getLast().result())
                .isEqualTo(InteractionResult.FALHOU);
        assertThat(report.currentVersion()).isEqualTo(1);
        assertThat(report.step()).isNull();
        assertThat(JobProcessorService.failureCode(new InvalidAiOutputException("x")))
                .isEqualTo("SAIDA_INVALIDA");
    }

    @Test
    void complementProcessorServesOtherTypes() {
        final boolean[] called = new boolean[1];
        c.processor.registerComplement(
                new JobProcessorService.ComplementProcessor() {
                    @Override
                    public boolean serves(ProcessingJob job) {
                        return true;
                    }

                    @Override
                    public void process(
                            ProcessingJob job, JobProcessorService.ProcessingSupport support) {
                        called[0] = true;
                    }
                });
        ProcessingJob semester =
                ProcessingJob.create(
                        br.com.felipeblaft.reports.domain.job.JobType.CONSOLIDACAO_SEMESTRAL,
                        c.clara.id(),
                        c.matteo.id(),
                        null,
                        null,
                        Channel.WEB,
                        Medium.TEXTO,
                        br.com.felipeblaft.reports.domain.job.JobStep.CONSOLIDANDO,
                        c.clock.now());
        c.queue.enqueue(semester);
        c.processQueue();
        assertThat(called[0]).isTrue();
    }
}
