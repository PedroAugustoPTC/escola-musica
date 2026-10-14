package br.com.felipeblaft.reports.service;

import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiCall;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.JobStatus;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.InteractionResult;
import br.com.felipeblaft.reports.domain.report.InteractionType;
import br.com.felipeblaft.reports.domain.report.Lesson;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportInteraction;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.report.ReportStep;
import br.com.felipeblaft.reports.domain.report.ReportVersion;
import br.com.felipeblaft.reports.domain.report.VersionOrigin;
import br.com.felipeblaft.reports.port.AiLessonReportOutput;
import br.com.felipeblaft.reports.port.AudioInput;
import br.com.felipeblaft.reports.port.AudioNotProcessableException;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.CostRecordPort;
import br.com.felipeblaft.reports.port.InvalidAiOutputException;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.LlmPort;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.ReportInteractionRepository;
import br.com.felipeblaft.reports.port.ReportVersionRepository;
import br.com.felipeblaft.reports.port.ResponseLlm;
import br.com.felipeblaft.reports.port.TaskInput;
import br.com.felipeblaft.reports.port.TaskLlm;
import br.com.felipeblaft.reports.port.TeacherNotificationPort;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.port.TranscriptionResult;
import br.com.felipeblaft.reports.port.UnsupportedFormatException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Executa os trabalhos da fila (R-06): registro de aula, nova tentativa, resposta às perguntas e
 * alteração. As etapas longas (transcrição, IA) ficam fora de transação; cada gravação de estado é
 * atômica e notificada. Aplica as regras da saída da IA (contracts/portas-provedores.md §6.1),
 * registra interações, versões, PDFs, custo e tempo, e descarta o resultado se o relatório foi
 * cancelado ou o trabalho descartado enquanto processava.
 */
public class JobProcessorService {

    /** Processamento de outros relatórios (ex.: consolidação e alteração do semestral). */
    public interface ComplementProcessor {
        boolean serves(ProcessingJob job);

        void process(ProcessingJob job, ProcessingSupport support);

        /** Erro inesperado: o relatório não pode ficar preso numa etapa (FR-022). */
        default void failed(ProcessingJob job) {}

        /** Soma o tempo do trabalho concluído ao relatório (dentro da transação de conclusão). */
        default void registerTime(ProcessingJob job) {}
    }

    /** Operações comuns oferecidas aos processadores complementares. */
    public interface ProcessingSupport {
        void advance(ProcessingJob job, JobStep step);

        void complete(ProcessingJob job, JobResult result);

        /** O trabalho foi descartado em outro canal; se sim, encerra sem resultado. */
        boolean discarded(ProcessingJob job);

        /** Texto digitado ou transcrito do áudio; vazio se a transcrição falhou. */
        Optional<String> requestText(ProcessingJob job);

        <T> Optional<ResponseLlm<T>> callLlm(
                ProcessingJob job,
                TaskLlm task,
                AiTask aiTask,
                TaskInput input,
                Class<T> outputType);

        /**
         * Vincula ao relatório recém-criado as chamadas de IA do trabalho feitas antes dele
         * (FR-056); deve ser chamado na transação que cria o relatório.
         */
        void linkCosts(ProcessingJob job, ReportType type, UUID reportId);
    }

    private final ProcessingSupport support =
            new ProcessingSupport() {
                @Override
                public void advance(ProcessingJob job, JobStep step) {
                    JobProcessorService.this.advance(job, step);
                }

                @Override
                public void complete(ProcessingJob job, JobResult result) {
                    JobProcessorService.this.complete(job, result);
                }

                @Override
                public boolean discarded(ProcessingJob job) {
                    return JobProcessorService.this.discarded(job);
                }

                @Override
                public Optional<String> requestText(ProcessingJob job) {
                    return JobProcessorService.this.requestText(job);
                }

                @Override
                public <T> Optional<ResponseLlm<T>> callLlm(
                        ProcessingJob job,
                        TaskLlm task,
                        AiTask aiTask,
                        TaskInput input,
                        Class<T> outputType) {
                    return JobProcessorService.this.callTypedLlm(
                            job, task, aiTask, input, outputType);
                }

                @Override
                public void linkCosts(ProcessingJob job, ReportType type, UUID reportId) {
                    costs.linkReport(job.id(), type, reportId);
                }
            };

    private final JobQueuePort queue;
    private final LessonReportRepository reports;
    private final ReportVersionRepository versions;
    private final ReportInteractionRepository interactions;
    private final TemporaryAudioService audio;
    private final LlmPort llm;
    private final DocumentGenerationService documents;
    private final TeacherNotificationPort notification;
    private final CostRecordPort costs;
    private final TransactionPort transaction;
    private final ClockPort clock;
    private ComplementProcessor complement;

    public JobProcessorService(
            JobQueuePort queue,
            LessonReportRepository reports,
            ReportVersionRepository versions,
            ReportInteractionRepository interactions,
            TemporaryAudioService audio,
            LlmPort llm,
            DocumentGenerationService documents,
            TeacherNotificationPort notification,
            CostRecordPort costs,
            TransactionPort transaction,
            ClockPort clock) {
        this.queue = Objects.requireNonNull(queue);
        this.reports = Objects.requireNonNull(reports);
        this.versions = Objects.requireNonNull(versions);
        this.interactions = Objects.requireNonNull(interactions);
        this.audio = Objects.requireNonNull(audio);
        this.llm = Objects.requireNonNull(llm);
        this.documents = Objects.requireNonNull(documents);
        this.notification = Objects.requireNonNull(notification);
        this.costs = Objects.requireNonNull(costs);
        this.transaction = Objects.requireNonNull(transaction);
        this.clock = Objects.requireNonNull(clock);
    }

    public void registerComplement(ComplementProcessor processor) {
        this.complement = processor;
    }

    /**
     * Processa um trabalho já reservado (em execução). Erros inesperados (inclusive {@link Error})
     * viram falha do trabalho, para que ele nunca fique preso em execução.
     */
    public void process(ProcessingJob job) {
        try {
            if (complement != null && complement.serves(job)) {
                complement.process(job, support);
                return;
            }
            switch (job.type()) {
                case LESSON_REGISTRATION -> registerLesson(job);
                case RETRY -> newAttempt(job);
                case QUESTION_ANSWER -> answer(job);
                case CHANGE -> changeLesson(job);
                default -> throw new IllegalStateException("tipo de trabalho sem processador");
            }
        } catch (RuntimeException | Error unexpected) {
            unexpectedFailure(job);
            throw unexpected;
        } finally {
            discardFile(job);
        }
    }

    // ------------------------------------------------------------ registro de aula

    private void registerLesson(ProcessingJob job) {
        notification.notifyStep(job);
        advance(job, JobStep.TRANSCRIBING);
        Optional<TranscriptionResult> transcription = transcribe(job);
        if (transcription.isEmpty()) {
            complete(job, JobResult.AUDIO_NOT_PROCESSABLE);
            return;
        }
        if (discarded(job)) {
            return;
        }
        Instant now = clock.now();
        LessonReport report =
                transaction.run(
                        () -> {
                            Lesson lesson =
                                    Lesson.create(
                                            job.teacherId(),
                                            job.studentId(),
                                            clock.todayInSchoolTimezone(),
                                            now);
                            reports.saveLesson(lesson);
                            LessonReport created =
                                    LessonReport.create(
                                            lesson,
                                            transcription.get().text(),
                                            job.requestChannel(),
                                            now);
                            reports.save(created);
                            job.linkReport(ReportType.LESSON, created.id());
                            // a transcrição foi registrada antes de o relatório existir (FR-056)
                            costs.linkReport(job.id(), ReportType.LESSON, created.id());
                            job.advance(JobStep.STRUCTURING);
                            queue.update(job);
                            return created;
                        });
        notification.notifyStep(job);
        structure(job, report);
    }

    private void newAttempt(ProcessingJob job) {
        LessonReport report = reports.searchById(job.reportId()).orElseThrow();
        advance(job, JobStep.STRUCTURING);
        structure(job, report);
    }

    private void structure(ProcessingJob job, LessonReport report) {
        Optional<ResponseLlm<AiLessonReportOutput>> response =
                callLlm(
                        job,
                        TaskLlm.LESSON_STRUCTURING,
                        AiTask.STRUCTURING,
                        new TaskInput.LessonStructuring(report.transcription()));
        if (response.isEmpty()) {
            boolean saved =
                    withOpenReport(
                            job,
                            current -> {
                                current.failStructuring(clock.now());
                                return true;
                            });
            if (saved) {
                complete(job, JobResult.STRUCTURING_FAILED);
            }
            return;
        }
        AiLessonReportOutput output = response.get().output();
        String prompt = response.get().versionPrompt();
        final boolean[] review = new boolean[1];
        boolean saved =
                withOpenReport(
                        job,
                        current -> {
                            Instant now = clock.now();
                            review[0] =
                                    current.registerStructuring(
                                            output.content(), output.pendingQuestions(), now);
                            if (review[0]) {
                                createVersion(
                                        current,
                                        VersionOrigin.INITIAL_STRUCTURING,
                                        null,
                                        job,
                                        prompt);
                            }
                            return true;
                        });
        if (saved) {
            complete(job, review[0] ? JobResult.READY_FOR_REVIEW : JobResult.PENDING_QUESTIONS);
        }
    }

    // ------------------------------------------------------------ respostas às perguntas

    private void answer(ProcessingJob job) {
        Optional<String> text = requestText(job);
        if (text.isEmpty()) {
            endStepWithoutChange(job, JobResult.ANSWER_FAILED);
            return;
        }
        LessonReport report = reports.searchById(job.reportId()).orElseThrow();
        if (job.medium() == Medium.AUDIO) {
            withOpenReport(
                    job,
                    current -> {
                        current.startStep(ReportStep.INCORPORATING_ANSWERS, clock.now());
                        return true;
                    });
        }
        advance(job, JobStep.INCORPORATING_ANSWERS);
        int round = report.questionRounds();
        Optional<ResponseLlm<AiLessonReportOutput>> response =
                callLlm(
                        job,
                        TaskLlm.ANSWER_INCORPORATION,
                        AiTask.ANSWER_INCORPORATION,
                        new TaskInput.AnswerIncorporation(
                                report.content(), report.pendingQuestions(), text.get(), round));
        if (response.isEmpty()) {
            boolean saved =
                    withOpenReport(
                            job,
                            current -> {
                                registerInteraction(
                                        current,
                                        job,
                                        InteractionType.QUESTION_ANSWER,
                                        text.get(),
                                        round,
                                        InteractionResult.FAILED,
                                        null);
                                current.completeStep(clock.now());
                                return true;
                            });
            if (saved) {
                complete(job, JobResult.ANSWER_FAILED);
            }
            return;
        }
        AiLessonReportOutput output = response.get().output();
        final boolean[] review = new boolean[1];
        boolean saved =
                withOpenReport(
                        job,
                        current -> {
                            ReportInteraction interaction =
                                    registerInteraction(
                                            current,
                                            job,
                                            InteractionType.QUESTION_ANSWER,
                                            text.get(),
                                            round,
                                            InteractionResult.APPLIED,
                                            null);
                            review[0] =
                                    current.incorporateAnswer(
                                            output.content(),
                                            output.pendingQuestions(),
                                            clock.now());
                            if (review[0]) {
                                createVersion(
                                        current,
                                        VersionOrigin.QUESTION_ANSWER,
                                        interaction,
                                        job,
                                        response.get().versionPrompt());
                            }
                            return true;
                        });
        if (saved) {
            complete(job, review[0] ? JobResult.READY_FOR_REVIEW : JobResult.PENDING_QUESTIONS);
        }
    }

    // ------------------------------------------------------------ alteração

    private void changeLesson(ProcessingJob job) {
        Optional<String> text = requestText(job);
        if (text.isEmpty()) {
            endStepWithoutChange(job, JobResult.CHANGE_FAILED);
            return;
        }
        int baseVersion =
                job.parameter(ProcessingJob.BASE_VERSION).map(Integer::parseInt).orElse(0);
        LessonReport report = reports.searchById(job.reportId()).orElseThrow();
        if (report.currentVersion() != baseVersion) {
            boolean saved =
                    withOpenReport(
                            job,
                            current -> {
                                registerInteraction(
                                        current,
                                        job,
                                        InteractionType.CHANGE,
                                        text.get(),
                                        null,
                                        InteractionResult.REJECTED_STALE_VERSION,
                                        null);
                                current.completeStep(clock.now());
                                return true;
                            });
            if (saved) {
                complete(job, JobResult.STALE_VERSION);
            }
            return;
        }
        withOpenReport(
                job,
                current -> {
                    current.startStep(ReportStep.APPLYING_CHANGE, clock.now());
                    return true;
                });
        advance(job, JobStep.APPLYING_CHANGE);
        Optional<ResponseLlm<AiLessonReportOutput>> response =
                callLlm(
                        job,
                        TaskLlm.LESSON_REVISION,
                        AiTask.REVISION,
                        new TaskInput.LessonRevision(report.content(), text.get()));
        if (response.isEmpty()) {
            boolean saved =
                    withOpenReport(
                            job,
                            current -> {
                                registerInteraction(
                                        current,
                                        job,
                                        InteractionType.CHANGE,
                                        text.get(),
                                        null,
                                        InteractionResult.FAILED,
                                        null);
                                current.completeStep(clock.now());
                                return true;
                            });
            if (saved) {
                complete(job, JobResult.CHANGE_FAILED);
            }
            return;
        }
        AiLessonReportOutput output = response.get().output();
        final JobResult[] result = new JobResult[1];
        boolean saved =
                withOpenReport(
                        job,
                        current -> {
                            Instant now = clock.now();
                            if (output.withoutChange()
                                    || output.content().equals(current.content())) {
                                InteractionResult withoutChange =
                                        output.withoutChange() && output.indicatedActionRequest()
                                                ? InteractionResult.ACTION_REQUEST_IGNORED
                                                : InteractionResult.NO_CONTENT_CHANGE;
                                registerInteraction(
                                        current,
                                        job,
                                        InteractionType.CHANGE,
                                        text.get(),
                                        null,
                                        withoutChange,
                                        null);
                                current.completeStep(now);
                                result[0] =
                                        withoutChange == InteractionResult.ACTION_REQUEST_IGNORED
                                                ? JobResult.ACTION_REQUEST_IGNORED
                                                : JobResult.NO_CONTENT_CHANGE;
                                return true;
                            }
                            int created = current.applyChange(output.content(), now);
                            ReportInteraction interaction =
                                    registerInteraction(
                                            current,
                                            job,
                                            InteractionType.CHANGE,
                                            text.get(),
                                            null,
                                            InteractionResult.APPLIED,
                                            created);
                            createVersion(
                                    current,
                                    VersionOrigin.CHANGE,
                                    interaction,
                                    job,
                                    response.get().versionPrompt());
                            result[0] = JobResult.NEW_VERSION;
                            return true;
                        });
        if (saved) {
            complete(job, result[0]);
        }
    }

    // ------------------------------------------------------------ apoio

    /** Texto digitado ou transcrito do áudio; vazio se a transcrição falhou (nada é aplicado). */
    private Optional<String> requestText(ProcessingJob job) {
        if (job.medium() == Medium.TEXT) {
            return job.parameter(ProcessingJob.TEXT);
        }
        advance(job, JobStep.TRANSCRIBING_REQUEST);
        Optional<TranscriptionResult> transcription = transcribe(job);
        transcription.ifPresent(
                t -> {
                    job.defineParameter(ProcessingJob.UNDERSTOOD_TEXT, t.text().strip());
                    queue.update(job);
                });
        return transcription.map(t -> t.text().strip());
    }

    private void endStepWithoutChange(ProcessingJob job, JobResult result) {
        boolean saved =
                withOpenReport(
                        job,
                        current -> {
                            current.completeStep(clock.now());
                            return true;
                        });
        if (saved) {
            complete(job, result);
        }
    }

    private Optional<TranscriptionResult> transcribe(ProcessingJob job) {
        long start = System.nanoTime();
        try {
            TranscriptionResult result = audio.transcribe(font(job));
            costs.register(
                    AiCall.create(
                            job.id(),
                            job.reportType(),
                            job.reportId(),
                            AiTask.TRANSCRIPTION,
                            result.modelProvider(),
                            BigDecimal.valueOf(result.durationSeconds()),
                            null,
                            null,
                            elapsed(start),
                            result.estimatedCost(),
                            true,
                            null,
                            clock.now()));
            return Optional.of(result);
        } catch (AudioNotProcessableException
                | UnsupportedFormatException
                | ProviderUnavailableException failure) {
            registerAiFailure(job, AiTask.TRANSCRIPTION, failureCode(failure), start);
            return Optional.empty();
        } finally {
            job.defineTemporaryFile(null);
        }
    }

    private Optional<ResponseLlm<AiLessonReportOutput>> callLlm(
            ProcessingJob job, TaskLlm task, AiTask aiTask, TaskInput input) {
        return callTypedLlm(job, task, aiTask, input, AiLessonReportOutput.class);
    }

    private <T> Optional<ResponseLlm<T>> callTypedLlm(
            ProcessingJob job, TaskLlm task, AiTask aiTask, TaskInput input, Class<T> outputType) {
        long start = System.nanoTime();
        try {
            ResponseLlm<T> response = llm.generateJson(task, input, outputType);
            costs.register(
                    AiCall.create(
                            job.id(),
                            job.reportType(),
                            job.reportId(),
                            aiTask,
                            response.modelProvider(),
                            null,
                            response.inputTokens(),
                            response.outputTokens(),
                            elapsed(start),
                            response.estimatedCost(),
                            true,
                            null,
                            clock.now()));
            return Optional.of(response);
        } catch (InvalidAiOutputException failure) {
            registerAiFailure(
                    job,
                    aiTask,
                    failureCode(failure),
                    start,
                    failure.inputTokens(),
                    failure.outputTokens(),
                    failure.estimatedCost());
            return Optional.empty();
        } catch (ProviderUnavailableException failure) {
            registerAiFailure(
                    job,
                    aiTask,
                    failureCode(failure),
                    start,
                    failure.inputTokens(),
                    failure.outputTokens(),
                    failure.estimatedCost());
            return Optional.empty();
        }
    }

    static String failureCode(RuntimeException failure) {
        return switch (failure) {
            case AudioNotProcessableException a -> "AUDIO_" + a.reason().name();
            case UnsupportedFormatException f -> "UNSUPPORTED_FORMAT";
            case InvalidAiOutputException s -> "INVALID_OUTPUT";
            default -> "PROVIDER_UNAVAILABLE";
        };
    }

    private void registerAiFailure(ProcessingJob job, AiTask task, String code, long start) {
        registerAiFailure(job, task, code, start, null, null, null);
    }

    /** O consumo das chamadas que falharam por saída inválida também custa (FR-056). */
    private void registerAiFailure(
            ProcessingJob job,
            AiTask task,
            String code,
            long start,
            Integer inputTokens,
            Integer outputTokens,
            BigDecimal estimatedCost) {
        costs.register(
                AiCall.create(
                        job.id(),
                        job.reportType(),
                        job.reportId(),
                        task,
                        null,
                        null,
                        inputTokens,
                        outputTokens,
                        elapsed(start),
                        estimatedCost == null ? BigDecimal.ZERO : estimatedCost,
                        false,
                        code,
                        clock.now()));
    }

    private static long elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    static AudioInput font(ProcessingJob job) {
        if (job.temporaryFile() != null) {
            long size = job.parameter(ProcessingJob.FILE_SIZE).map(Long::parseLong).orElse(0L);
            return new TemporaryFile(
                    Path.of(job.temporaryFile()),
                    size,
                    job.parameter(ProcessingJob.FILE_MIME).orElse(null));
        }
        return new ChannelFileReference(
                job.parameter(ProcessingJob.CHANNEL_FILE).orElseThrow(),
                job.parameter(ProcessingJob.UNIQUE_CHANNEL_FILE).orElse(null),
                job.parameter(ProcessingJob.FILE_MIME).orElse(null),
                job.parameter(ProcessingJob.FILE_SIZE).map(Long::parseLong).orElse(0L));
    }

    private void discardFile(ProcessingJob job) {
        if (job.temporaryFile() != null) {
            audio.discard(font(job));
            job.defineTemporaryFile(null);
        }
    }

    private void createVersion(
            LessonReport report,
            VersionOrigin origin,
            ReportInteraction interaction,
            ProcessingJob job,
            String prompt) {
        ReportVersion version =
                ReportVersion.create(
                        ReportType.LESSON,
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

    private ReportInteraction registerInteraction(
            LessonReport report,
            ProcessingJob job,
            InteractionType type,
            String text,
            Integer round,
            InteractionResult result,
            Integer resultingVersion) {
        int baseVersion =
                job.parameter(ProcessingJob.BASE_VERSION)
                        .map(Integer::parseInt)
                        .orElse(report.currentVersion());
        ReportInteraction interaction =
                ReportInteraction.create(
                        ReportType.LESSON,
                        report.id(),
                        type,
                        job.requestChannel(),
                        job.medium(),
                        text,
                        baseVersion,
                        round,
                        result,
                        resultingVersion,
                        clock.now());
        interactions.save(interaction);
        return interaction;
    }

    /** Operação de gravação sobre o relatório; devolve o que a operação devolve. */
    private interface ReportOperation {
        boolean apply(LessonReport current);
    }

    /**
     * Relê o relatório numa transação e aplica a operação. Se o relatório foi cancelado ou o
     * trabalho descartado (ou outro canal gravou antes), o resultado é descartado e devolve {@code
     * false}.
     */
    private boolean withOpenReport(ProcessingJob job, ReportOperation operation) {
        try {
            return transaction.run(
                    () -> {
                        LessonReport current =
                                reports.searchForUpdate(job.reportId()).orElseThrow();
                        if (current.status() == ReportStatus.CANCELLED
                                || current.status() == ReportStatus.APPROVED
                                || isDiscarded(job)) {
                            return false;
                        }
                        boolean applied = operation.apply(current);
                        reports.save(current);
                        return applied;
                    });
        } catch (ConcurrencyConflictException conflict) {
            LessonReport current = reports.searchById(job.reportId()).orElseThrow();
            if (current.status() == ReportStatus.CANCELLED || isDiscarded(job)) {
                markDiscarded(job);
                return false;
            }
            throw conflict;
        }
    }

    private boolean isDiscarded(ProcessingJob job) {
        return queue.searchById(job.id()).map(t -> t.status() == JobStatus.DISCARDED).orElse(true);
    }

    /** Verifica o descarte; se descartado, encerra o trabalho sem resultado. */
    private boolean discarded(ProcessingJob job) {
        if (isDiscarded(job)) {
            markDiscarded(job);
            return true;
        }
        return false;
    }

    private void markDiscarded(ProcessingJob job) {
        job.discard(clock.now());
    }

    private void advance(ProcessingJob job, JobStep step) {
        job.advance(step);
        queue.update(job);
        notification.notifyStep(job);
    }

    private void complete(ProcessingJob job, JobResult result) {
        if (isDiscarded(job)) {
            markDiscarded(job);
            return;
        }
        Instant now = clock.now();
        job.complete(result, now);
        transaction.run(
                () -> {
                    queue.update(job);
                    if (complement != null
                            && job.reportType() != null
                            && job.reportType() != ReportType.LESSON) {
                        complement.registerTime(job);
                    }
                    if (job.reportId() != null && job.reportType() == ReportType.LESSON) {
                        reports.searchById(job.reportId())
                                .ifPresent(
                                        report -> {
                                            report.addProcessingTime(job.durationMs());
                                            reports.save(report);
                                        });
                    }
                });
        notification.notifyResult(job);
    }

    /** Erro inesperado: o trabalho falha e o relatório não fica preso numa etapa (FR-022). */
    private void unexpectedFailure(ProcessingJob job) {
        JobResult result =
                switch (job.type()) {
                    case QUESTION_ANSWER -> JobResult.ANSWER_FAILED;
                    case CHANGE -> JobResult.CHANGE_FAILED;
                    case SEMESTER_CONSOLIDATION -> JobResult.CONSOLIDATION_FAILED;
                    default ->
                            job.reportId() == null
                                    ? JobResult.AUDIO_NOT_PROCESSABLE
                                    : JobResult.STRUCTURING_FAILED;
                };
        try {
            if (complement != null && complement.serves(job)) {
                complement.failed(job);
            } else if (job.reportId() != null && job.reportType() == ReportType.LESSON) {
                withOpenReport(
                        job,
                        current -> {
                            if (current.status() == ReportStatus.PROCESSING) {
                                current.failStructuring(clock.now());
                            } else {
                                current.completeStep(clock.now());
                            }
                            return true;
                        });
            }
            if (job.status().inProgress()) {
                complete(job, result);
            }
        } catch (RuntimeException ignored) {
            // o erro original é relatado por quem chamou
        }
    }
}
