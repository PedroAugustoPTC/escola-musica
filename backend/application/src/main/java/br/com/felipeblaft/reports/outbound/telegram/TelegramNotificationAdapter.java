package br.com.felipeblaft.reports.outbound.telegram;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.JobResult;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.telegram.FocusReport;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.inbound.telegram.TelegramActionPublicationPort;
import br.com.felipeblaft.reports.inbound.telegram.TelegramLessonReportPresentation;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Message;
import br.com.felipeblaft.reports.inbound.telegram.TelegramPresenter;
import br.com.felipeblaft.reports.inbound.telegram.TelegramSemesterReportPresentation;
import br.com.felipeblaft.reports.port.ConcurrencyConflictException;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherNotificationPort;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase.LessonReportView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Avisos ativos ao professor pelo bot (FR-021, contracts/telegram-conversa.md §5.2 e §6).
 *
 * <ul>
 *   <li>Trabalho pedido no Telegram: a mensagem de etapa é editada no lugar e o resultado é
 *       entregue pela mesma apresentação da conversa, que põe o relatório em foco.
 *   <li>Trabalho pedido na Web, com vínculo: só um aviso curto com "Continuar no chat", sem mudar o
 *       foco da conversa até o professor tocar no botão.
 * </ul>
 *
 * Falhas de envio nunca interrompem o processamento.
 */
@Component
public class TelegramNotificationAdapter implements TeacherNotificationPort {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramNotificationAdapter.class);
    private static final int SAVE_ATTEMPTS = 3;

    private final TelegramLinkRepository links;
    private final StudentRepository students;
    private final JobQueuePort queue;
    private final TelegramLessonReportPresentation presentation;
    private final TelegramSemesterReportPresentation semesterPresentation;
    private final TelegramPresenter presenter;
    private final TelegramActionPublicationPort publication;

    public TelegramNotificationAdapter(
            TelegramLinkRepository links,
            StudentRepository students,
            JobQueuePort queue,
            TelegramLessonReportPresentation presentation,
            TelegramSemesterReportPresentation semesterPresentation,
            TelegramPresenter presenter,
            TelegramActionPublicationPort publication) {
        this.links = links;
        this.students = students;
        this.queue = queue;
        this.presentation = presentation;
        this.semesterPresentation = semesterPresentation;
        this.presenter = presenter;
        this.publication = publication;
    }

    // ------------------------------------------------------------------ etapas

    @Override
    public void notifyStep(ProcessingJob job) {
        if (job.requestChannel() != Channel.TELEGRAM || job.step() == JobStep.UPLOADING) {
            return;
        }
        guarded(
                "step",
                () -> {
                    Optional<TelegramLink> link = links.searchByTeacherId(job.teacherId());
                    if (link.isEmpty()) {
                        return;
                    }
                    // a etapa edita a mensagem de etapa já conhecida do trabalho; sem ela, o envio
                    // direto
                    // nada faz e o despacho do orquestrador a resolve pelo trabalho no momento da
                    // entrega
                    publication.publish(
                            "step:" + job.id() + ":" + job.step().name(),
                            job.teacherId(),
                            List.of(
                                    new TelegramOutput.StepMessage(
                                            link.get().chatId(),
                                            stepMessage(job).orElse(null),
                                            stepText(job),
                                            job.id())));
                });
    }

    /** Id da mensagem de etapa: no trabalho em memória ou, se o envio o gravou depois, no banco. */
    private Optional<Long> stepMessage(ProcessingJob job) {
        Optional<String> value =
                job.parameter(ProcessingJob.STEP_MESSAGE)
                        .or(
                                () ->
                                        queue.searchById(job.id())
                                                .flatMap(
                                                        t ->
                                                                t.parameter(
                                                                        ProcessingJob
                                                                                .STEP_MESSAGE)));
        value.ifPresent(v -> job.defineParameter(ProcessingJob.STEP_MESSAGE, v));
        try {
            return value.map(Long::valueOf);
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
    }

    private String stepText(ProcessingJob job) {
        return "<i>"
                + TelegramPresenter.escape(presenter.formatting().step(job.step().name()))
                + "…</i>";
    }

    // ------------------------------------------------------------------ demora

    @Override
    public void notifyDelay(ProcessingJob job) {
        if (job.requestChannel() != Channel.TELEGRAM) {
            return;
        }
        guarded(
                "delay",
                () ->
                        links.searchByTeacherId(job.teacherId())
                                .ifPresent(
                                        link ->
                                                publication.publish(
                                                        "delay:" + job.id(),
                                                        job.teacherId(),
                                                        List.of(
                                                                presenter.simple(
                                                                        link.chatId(),
                                                                        "PROCESSING_DELAYED")))));
    }

    // ------------------------------------------------------------------ resultado

    @Override
    public void notifyResult(ProcessingJob job) {
        if (job.result() == null || job.result() == JobResult.DISCARDED) {
            return;
        }
        guarded(
                "result",
                () -> {
                    if (job.requestChannel() == Channel.WEB) {
                        webNotice(job);
                    } else {
                        resultInChat(job);
                    }
                });
    }

    private void webNotice(ProcessingJob job) {
        Optional<TelegramLink> link = links.searchByTeacherId(job.teacherId());
        if (link.isEmpty()) {
            return;
        }
        String code =
                switch (job.result()) {
                    case READY_FOR_REVIEW -> "WEB_RESULT_NOTICE_READY";
                    case PENDING_QUESTIONS -> "WEB_RESULT_NOTICE_QUESTIONS";
                    case NEW_VERSION -> "WEB_RESULT_NOTICE_NEW_VERSION";
                    case STRUCTURING_FAILED, AUDIO_NOT_PROCESSABLE, CONSOLIDATION_FAILED ->
                            "WEB_RESULT_NOTICE_FAILURE";
                    default -> null;
                };
        if (code == null) {
            return;
        }
        String student = students.searchById(job.studentId()).map(a -> a.name()).orElse("");
        boolean semester = job.reportType() == ReportType.SEMESTER;
        Integer version = null;
        if (job.reportId() != null) {
            version =
                    semester
                            ? semesterPresentation
                                    .get(job.teacherId(), job.reportId())
                                    .report()
                                    .currentVersion()
                            : presentation
                                    .get(job.teacherId(), job.reportId())
                                    .report()
                                    .currentVersion();
        }
        Keyboard keyboard =
                job.reportId() == null
                        ? Keyboard.NONE
                        : Keyboard.of(
                                presenter.button(
                                        "CONTINUE_IN_CHAT",
                                        Map.of(),
                                        "focus",
                                        (semester ? "s" : "a") + job.reportId()));
        publication.publish(
                "result:" + job.id(),
                job.teacherId(),
                List.of(
                        new Message(
                                link.get().chatId(),
                                presenter.text(
                                        code,
                                        Map.of(
                                                "student",
                                                student,
                                                "version",
                                                version == null ? "" : version)),
                                keyboard)));
    }

    /**
     * Resultado de um trabalho pedido no chat: grava o foco na conversa (com nova tentativa diante
     * de outro update simultâneo) e só depois envia as mensagens.
     */
    private void resultInChat(ProcessingJob job) {
        for (int attempt = 1; attempt <= SAVE_ATTEMPTS; attempt++) {
            Optional<TelegramLink> link = links.searchByTeacherId(job.teacherId());
            if (link.isEmpty()) {
                return;
            }
            List<TelegramOutput> outputs = resultOutputs(job, link.get());
            try {
                links.save(link.get());
            } catch (OptimisticLockingFailureException | ConcurrencyConflictException conflict) {
                if (attempt < SAVE_ATTEMPTS) {
                    continue;
                }
                LOG.warn("telegram-focus-not-saved jobId={}", job.id());
            }
            List<TelegramOutput> all = new ArrayList<>();
            readyStep(job, link.get().chatId()).ifPresent(all::add);
            all.addAll(outputs);
            if (!all.isEmpty()) {
                publication.publish("result:" + job.id(), job.teacherId(), all);
            }
            return;
        }
    }

    /** A mensagem de etapa vira "Pronto" (editada no lugar) antes das mensagens do resultado. */
    private Optional<TelegramOutput> readyStep(ProcessingJob job, long chatId) {
        if (job.result() != JobResult.READY_FOR_REVIEW && job.result() != JobResult.NEW_VERSION) {
            return Optional.empty();
        }
        return Optional.of(
                new TelegramOutput.StepMessage(
                        chatId,
                        stepMessage(job).orElse(null),
                        "<i>" + presenter.text("STEP_READY") + "</i>",
                        job.id()));
    }

    List<TelegramOutput> resultOutputs(ProcessingJob job, TelegramLink link) {
        long chatId = link.chatId();
        TelegramConversation conversation = link.conversation();
        Optional<String> understood = understoodText(job);
        switch (job.result()) {
            case AUDIO_NOT_PROCESSABLE -> {
                return List.of(presenter.simple(chatId, "AUDIO_NOT_PROCESSABLE"));
            }
            case CONSOLIDATION_FAILED -> {
                return List.of(presenter.simple(chatId, "CONSOLIDATION_FAILED"));
            }
            case CONSENT_REQUIRED -> {
                return List.of(presenter.simple(chatId, "CONSENT_REQUIRED"));
            }
            default -> {
                // demais resultados têm relatório de aula
            }
        }
        if (job.reportId() == null) {
            return List.of();
        }
        if (job.reportType() == ReportType.SEMESTER) {
            return semesterOutputs(job, chatId, conversation, understood);
        }
        LessonReportView view;
        try {
            view = presentation.get(job.teacherId(), job.reportId());
        } catch (DomainException notFound) {
            return List.of();
        }
        LessonReport report = view.report();
        int current = report.currentVersion();
        switch (job.result()) {
            case NO_CONTENT_CHANGE, ACTION_REQUEST_IGNORED, CHANGE_FAILED -> {
                conversation.focus(new FocusReport(ReportType.LESSON, report.id()), current);
                String text =
                        presenter.text(job.result().name(), Map.of("version", current))
                                + understood
                                        .map(
                                                t ->
                                                        "\n"
                                                                + presenter.text(
                                                                        "UNDERSTOOD_TEXT",
                                                                        Map.of("text", t)))
                                        .orElse("");
                return List.of(presentation.reviewActions(chatId, current, text));
            }
            default -> {
                return new ArrayList<>(
                        presentation.present(
                                chatId,
                                job.teacherId(),
                                view,
                                conversation,
                                prefix(job, current, understood)));
            }
        }
    }

    private List<TelegramOutput> semesterOutputs(
            ProcessingJob job,
            long chatId,
            TelegramConversation conversation,
            Optional<String> understood) {
        String prefix =
                switch (job.result()) {
                    case NEW_VERSION, NO_CONTENT_CHANGE, ACTION_REQUEST_IGNORED, CHANGE_FAILED -> {
                        int current =
                                semesterPresentation
                                        .get(job.teacherId(), job.reportId())
                                        .report()
                                        .currentVersion();
                        String code =
                                job.result() == JobResult.NEW_VERSION
                                        ? "CHANGE_APPLIED"
                                        : job.result().name();
                        yield presenter.text(code, Map.of("version", current))
                                + understood
                                        .map(
                                                t ->
                                                        "\n"
                                                                + presenter.text(
                                                                        "UNDERSTOOD_TEXT",
                                                                        Map.of("text", t)))
                                        .orElse("");
                    }
                    case STALE_VERSION ->
                            presenter.text(
                                    "STALE_VERSION",
                                    Map.of(
                                            "currentVersion",
                                            semesterPresentation
                                                    .get(job.teacherId(), job.reportId())
                                                    .report()
                                                    .currentVersion()));
                    default -> null;
                };
        try {
            return new ArrayList<>(
                    semesterPresentation.present(
                            chatId, job.teacherId(), job.reportId(), conversation, prefix));
        } catch (DomainException notFound) {
            return List.of();
        }
    }

    private String prefix(ProcessingJob job, int current, Optional<String> understood) {
        return switch (job.result()) {
            case NEW_VERSION ->
                    presenter.text("CHANGE_APPLIED", Map.of("version", current))
                            + understood
                                    .map(
                                            t ->
                                                    "\n"
                                                            + presenter.text(
                                                                    "UNDERSTOOD_TEXT",
                                                                    Map.of("text", t)))
                                    .orElse("");
            case STALE_VERSION ->
                    TelegramPresenter.escape(
                            presenter.label(
                                    ErrorCode.STALE_VERSION.messageKey(),
                                    Map.of("currentVersion", current)));
            case ANSWER_FAILED -> presenter.text("ANSWER_FAILED");
            case READY_FOR_REVIEW, PENDING_QUESTIONS ->
                    job.type() == JobType.QUESTION_ANSWER && understood.isPresent()
                            ? presenter.text(
                                    "ANSWER_INCORPORATED", Map.of("text", understood.get()))
                            : null;
            default -> null;
        };
    }

    private static Optional<String> understoodText(ProcessingJob job) {
        return job.parameter(ProcessingJob.UNDERSTOOD_TEXT)
                .or(() -> job.parameter(ProcessingJob.TEXT))
                .filter(t -> !t.isBlank());
    }

    private static void guarded(String notice, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            LOG.warn(
                    "telegram-notification-failed notice={} type={}",
                    notice,
                    error.getClass().getSimpleName());
        }
    }
}
