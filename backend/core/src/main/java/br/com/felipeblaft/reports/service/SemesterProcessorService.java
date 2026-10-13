package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.InteractionResult;
import br.com.felipeblaft.reports.domain.report.InteractionType;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.domain.report.ReportStep;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.domain.report.VersionOrigin;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.port.AiSemesterReportOutput;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.LessonReportRepository.ApprovedLesson;
import br.com.felipeblaft.reports.port.ReportInteractionRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.ResponseLlm;
import br.com.felipeblaft.reports.port.SemesterConsolidationInput;
import br.com.felipeblaft.reports.port.SemesterConsolidationInput.ConsolidatedLesson;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.port.SemesterRevisionInput;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TaskLlm;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.JobProcessorService.ComplementProcessor;
import br.com.felipeblaft.reports.service.JobProcessorService.ProcessingSupport;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Consolidação e alteração do relatório semestral (FR-042 a FR-045). A consolidação usa só os
 * relatórios de aula aprovados do próprio professor no período, com as datas, e nunca envia dados
 * pessoais à IA; as Informações Gerais são preenchidas pelo sistema. Se a consolidação falhar, nada
 * é criado.
 */
public class SemesterProcessorService implements ComplementProcessor {

    private final SemesterReportRepository semester;
    private final LessonReportRepository lessons;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final ReportVersionRepository versions;
    private final ReportInteractionRepository interactions;
    private final DocumentGenerationService documents;
    private final JobQueuePort queue;
    private final TransactionPort transaction;
    private final ClockPort clock;

    @SuppressWarnings("java:S107")
    public SemesterProcessorService(
            SemesterReportRepository semester,
            LessonReportRepository lessons,
            StudentRepository students,
            TeacherRepository teachers,
            ReportVersionRepository versions,
            ReportInteractionRepository interactions,
            DocumentGenerationService documents,
            JobQueuePort queue,
            TransactionPort transaction,
            ClockPort clock) {
        this.semester = Objects.requireNonNull(semester);
        this.lessons = Objects.requireNonNull(lessons);
        this.students = Objects.requireNonNull(students);
        this.teachers = Objects.requireNonNull(teachers);
        this.versions = Objects.requireNonNull(versions);
        this.interactions = Objects.requireNonNull(interactions);
        this.documents = Objects.requireNonNull(documents);
        this.queue = Objects.requireNonNull(queue);
        this.transaction = Objects.requireNonNull(transaction);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public boolean serves(ProcessingJob job) {
        return job.type() == JobType.CONSOLIDACAO_SEMESTRAL
                || (job.type() == JobType.ALTERACAO && job.reportType() == ReportType.SEMESTRAL);
    }

    @Override
    public void process(ProcessingJob job, ProcessingSupport support) {
        if (job.type() == JobType.CONSOLIDACAO_SEMESTRAL) {
            consolidate(job, support);
        } else {
            change(job, support);
        }
    }

    // ------------------------------------------------------------ consolidação

    private void consolidate(ProcessingJob job, ProcessingSupport support) {
        SemesterPeriod period =
                SemesterPeriod.of(job.parameter(ProcessingJob.PERIOD_KEY).orElseThrow());
        List<ApprovedLesson> approvedLessons =
                lessons.listApprovedInPeriod(
                        job.teacherId(), job.studentId(), period.start(), period.end());
        if (approvedLessons.isEmpty()) {
            support.complete(job, JobResult.FALHA_CONSOLIDACAO);
            return;
        }
        int open =
                lessons.countOpenInPeriod(
                        job.teacherId(), job.studentId(), period.start(), period.end());
        support.advance(job, JobStep.CONSOLIDANDO);
        SemesterConsolidationInput input =
                new SemesterConsolidationInput(
                        approvedLessons.stream()
                                .map(a -> new ConsolidatedLesson(a.lessonDate(), a.content()))
                                .toList(),
                        approvedLessons.size(),
                        period.start(),
                        period.end());
        Optional<ResponseLlm<AiSemesterReportOutput>> response =
                support.callLlm(
                        job,
                        TaskLlm.CONSOLIDACAO_SEMESTRAL,
                        AiTask.CONSOLIDACAO,
                        input,
                        AiSemesterReportOutput.class);
        if (response.isEmpty()) {
            support.complete(job, JobResult.FALHA_CONSOLIDACAO);
            return;
        }
        if (support.discarded(job)) {
            return;
        }
        support.advance(job, JobStep.GERANDO_PDF);
        Instant now = clock.now();
        boolean created;
        try {
            created =
                    transaction.run(
                            () -> {
                                Student student =
                                        students.searchById(job.studentId()).orElseThrow();
                                Teacher teacher =
                                        teachers.searchById(job.teacherId()).orElseThrow();
                                GeneralInformation information =
                                        GeneralInformation.calculate(
                                                student.name(),
                                                student.birthDate(),
                                                student.instrument(),
                                                teacher.name(),
                                                period,
                                                approvedLessons.size(),
                                                clock.todayInSchoolTimezone());
                                SemesterReport report =
                                        SemesterReport.create(
                                                job.teacherId(),
                                                job.studentId(),
                                                period,
                                                approvedLessons.size(),
                                                open,
                                                information,
                                                response.get().output().content(),
                                                job.requestChannel(),
                                                now);
                                semester.save(report);
                                job.linkReport(ReportType.SEMESTRAL, report.id());
                                support.linkCosts(job, ReportType.SEMESTRAL, report.id());
                                createVersion(
                                        report,
                                        VersionOrigin.CONSOLIDACAO,
                                        null,
                                        job,
                                        response.get().versionPrompt());
                                queue.update(job);
                                return true;
                            });
        } catch (ConcurrencyConflictException conflict) {
            // aprovado ou aberto em outro canal enquanto consolidava
            created = false;
        }
        support.complete(
                job, created ? JobResult.PRONTO_PARA_CONFERENCIA : JobResult.FALHA_CONSOLIDACAO);
    }

    // ------------------------------------------------------------ alteração

    private void change(ProcessingJob job, ProcessingSupport support) {
        Optional<String> text = support.requestText(job);
        if (text.isEmpty()) {
            end(job, support, JobResult.FALHA_ALTERACAO, null, null);
            return;
        }
        SemesterReport report = semester.searchById(job.reportId()).orElseThrow();
        int baseVersion =
                job.parameter(ProcessingJob.BASE_VERSION)
                        .map(Integer::parseInt)
                        .orElse(report.currentVersion());
        if (report.currentVersion() != baseVersion) {
            end(
                    job,
                    support,
                    JobResult.VERSAO_DESATUALIZADA,
                    text.get(),
                    InteractionResult.RECUSADA_VERSAO_DESATUALIZADA);
            return;
        }
        if (!withReport(
                job,
                support,
                current -> {
                    current.startStep(ReportStep.APLICANDO_ALTERACAO, clock.now());
                    return true;
                })) {
            return;
        }
        support.advance(job, JobStep.APLICANDO_ALTERACAO);
        Optional<ResponseLlm<AiSemesterReportOutput>> response =
                support.callLlm(
                        job,
                        TaskLlm.REVISAO_SEMESTRAL,
                        AiTask.REVISAO,
                        new SemesterRevisionInput(report.content(), text.get()),
                        AiSemesterReportOutput.class);
        if (response.isEmpty()) {
            end(job, support, JobResult.FALHA_ALTERACAO, text.get(), InteractionResult.FALHOU);
            return;
        }
        AiSemesterReportOutput output = response.get().output();
        final JobResult[] result = new JobResult[1];
        boolean saved =
                withReport(
                        job,
                        support,
                        current -> {
                            Instant now = clock.now();
                            if (output.withoutChange()
                                    || output.content().equals(current.content())) {
                                InteractionResult withoutChange =
                                        output.withoutChange() && output.indicatedActionRequest()
                                                ? InteractionResult.PEDIDO_DE_ACAO_IGNORADO
                                                : InteractionResult.SEM_ALTERACAO_DE_CONTEUDO;
                                registerInteraction(current, job, text.get(), withoutChange, null);
                                current.completeStep(now);
                                result[0] =
                                        withoutChange == InteractionResult.PEDIDO_DE_ACAO_IGNORADO
                                                ? JobResult.PEDIDO_DE_ACAO_IGNORADO
                                                : JobResult.SEM_ALTERACAO_DE_CONTEUDO;
                                return true;
                            }
                            int created = current.applyChange(output.content(), now);
                            ReportInteraction interaction =
                                    registerInteraction(
                                            current,
                                            job,
                                            text.get(),
                                            InteractionResult.APLICADA,
                                            created);
                            createVersion(
                                    current,
                                    VersionOrigin.ALTERACAO,
                                    interaction,
                                    job,
                                    response.get().versionPrompt());
                            result[0] = JobResult.NOVA_VERSAO;
                            return true;
                        });
        if (saved) {
            support.complete(job, result[0]);
        }
    }

    /** Encerra a alteração sem nova versão, registrando a interação quando houver texto. */
    private void end(
            ProcessingJob job,
            ProcessingSupport support,
            JobResult result,
            String text,
            InteractionResult interaction) {
        boolean saved =
                withReport(
                        job,
                        support,
                        current -> {
                            if (text != null) {
                                registerInteraction(current, job, text, interaction, null);
                            }
                            current.completeStep(clock.now());
                            return true;
                        });
        if (saved) {
            support.complete(job, result);
        }
    }

    /**
     * Grava no semestral com a linha bloqueada; um semestral cancelado ou um trabalho descartado em
     * outro canal encerra sem resultado.
     */
    private boolean withReport(
            ProcessingJob job, ProcessingSupport support, Predicate<SemesterReport> operation) {
        boolean[] discarded = {false};
        boolean applied =
                transaction.run(
                        () -> {
                            SemesterReport current =
                                    semester.searchForUpdate(job.reportId()).orElseThrow();
                            if (!current.status().isOpen()) {
                                discarded[0] = true;
                                return false;
                            }
                            boolean result = operation.test(current);
                            semester.save(current);
                            return result;
                        });
        if (discarded[0]) {
            job.discard(clock.now());
            queue.update(job);
            return false;
        }
        return applied && !support.discarded(job);
    }

    private ReportInteraction registerInteraction(
            SemesterReport report,
            ProcessingJob job,
            String text,
            InteractionResult result,
            Integer resultingVersion) {
        int baseVersion =
                job.parameter(ProcessingJob.BASE_VERSION)
                        .map(Integer::parseInt)
                        .orElse(report.currentVersion());
        ReportInteraction interaction =
                ReportInteraction.create(
                        ReportType.SEMESTRAL,
                        report.id(),
                        InteractionType.ALTERACAO,
                        job.requestChannel(),
                        job.medium(),
                        text,
                        baseVersion,
                        null,
                        result,
                        resultingVersion,
                        clock.now());
        interactions.save(interaction);
        return interaction;
    }

    private void createVersion(
            SemesterReport report,
            VersionOrigin origin,
            ReportInteraction interaction,
            ProcessingJob job,
            String prompt) {
        ReportVersion version =
                ReportVersion.create(
                        ReportType.SEMESTRAL,
                        report.id(),
                        report.currentVersion(),
                        report.content(),
                        origin,
                        interaction == null ? null : interaction.id(),
                        job.requestChannel(),
                        prompt,
                        clock.now());
        versions.save(version);
        documents.generateReview(report, version);
    }

    @Override
    public void failed(ProcessingJob job) {
        if (job.reportId() == null || job.reportType() != ReportType.SEMESTRAL) {
            return;
        }
        transaction.run(
                () -> {
                    semester.searchForUpdate(job.reportId())
                            .filter(r -> r.status().isOpen())
                            .ifPresent(
                                    r -> {
                                        r.completeStep(clock.now());
                                        semester.save(r);
                                    });
                    return null;
                });
    }

    @Override
    public void registerTime(ProcessingJob job) {
        if (job.reportId() == null) {
            return;
        }
        semester.searchById(job.reportId())
                .ifPresent(
                        r -> {
                            r.addProcessingTime(job.durationMs());
                            semester.save(r);
                        });
    }
}
