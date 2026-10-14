package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.FocusReport;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.inbound.telegram.TelegramKeywords.Command;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Button;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Message;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.StepMessage;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.TelegramFile;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.TelegramMessage;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import br.com.felipeblaft.reports.usecase.job.QueryJobUseCase;
import br.com.felipeblaft.reports.usecase.lesson.AnswerQuestionsUseCase;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.CancelLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase.LessonReportView;
import br.com.felipeblaft.reports.usecase.lesson.RequestLessonReportChangeUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RetryLessonReportUseCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Relatório de aula em foco (estados #3 a #6 da tabela, sequência 5.2, passos 5 a 8): resposta às
 * perguntas e pedido de alteração por texto ou áudio sem confirmação (FR-027b), aprovação e
 * cancelamento com confirmação, nova tentativa e o botão "Continuar no chat".
 */
@Component
@Order(10)
public class TelegramLessonReportFlow implements TelegramFlow {

    static final String APPROVE_CONFIRMATION = "approve:";
    static final String CANCEL_CONFIRMATION = "cancel";

    private final TelegramLessonReportPresentation presentation;
    private final TelegramPresenter presenter;
    private final AnswerQuestionsUseCase answer;
    private final RequestLessonReportChangeUseCase change;
    private final RetryLessonReportUseCase retry;
    private final ApproveLessonReportUseCase approve;
    private final CancelLessonReportUseCase cancel;
    private final QueryJobUseCase queryJob;
    private final SearchStudentsUseCase searchStudents;
    private final TelegramConversationInterpreter interpreter;

    @SuppressWarnings("java:S107")
    public TelegramLessonReportFlow(
            TelegramLessonReportPresentation presentation,
            TelegramPresenter presenter,
            AnswerQuestionsUseCase answer,
            RequestLessonReportChangeUseCase change,
            RetryLessonReportUseCase retry,
            ApproveLessonReportUseCase approve,
            CancelLessonReportUseCase cancel,
            QueryJobUseCase queryJob,
            SearchStudentsUseCase searchStudents,
            TelegramConversationInterpreter interpreter) {
        this.presentation = presentation;
        this.presenter = presenter;
        this.answer = answer;
        this.change = change;
        this.retry = retry;
        this.approve = approve;
        this.cancel = cancel;
        this.queryJob = queryJob;
        this.searchStudents = searchStudents;
        this.interpreter = interpreter;
    }

    // ------------------------------------------------------------------ foco

    /** Relatório de aula em foco ainda aberto; um foco que não vale mais é limpo. */
    private Optional<LessonReportView> focus(TelegramContext context) {
        TelegramConversation conversation = context.conversation();
        Optional<FocusReport> focus =
                conversation.focusReport().filter(f -> f.type() == ReportType.LESSON);
        if (focus.isEmpty()) {
            return Optional.empty();
        }
        try {
            LessonReportView view = presentation.get(context.teacher().id(), focus.get().id());
            if (!view.report().status().isOpen()) {
                conversation.cleanFocus();
                return Optional.empty();
            }
            return Optional.of(view);
        } catch (DomainException notFound) {
            conversation.cleanFocus();
            return Optional.empty();
        }
    }

    private int baseVersion(TelegramContext context, LessonReport report) {
        Integer presented = context.conversation().presentedVersion();
        return presented == null ? report.currentVersion() : presented;
    }

    // ------------------------------------------------------------------ texto e áudio

    @Override
    public boolean onText(TelegramContext context, String text) {
        Optional<LessonReportView> focus = focus(context);
        if (focus.isEmpty()) {
            return false;
        }
        LessonReportView view = focus.get();
        LessonReport report = view.report();
        if (TelegramLessonReportPresentation.inProcessing(view)) {
            context.answer(
                    new Message(context.chatId(), presentation.processing(view), Keyboard.NONE));
            return true;
        }
        UUID teacherId = context.teacher().id();
        switch (report.status()) {
            case AWAITING_ANSWERS ->
                    step(
                            context,
                            null,
                            answer.answerText(teacherId, report.id(), text, Channel.TELEGRAM));
            case AWAITING_REVIEW -> {
                Optional<StudentSummary> otherStudent =
                        searchStudents
                                .exactMatch(teacherId, text)
                                .filter(a -> !a.id().equals(report.studentId()));
                if (otherStudent.isPresent()) {
                    askStudentSwitch(context, view, otherStudent.get());
                    return true;
                }
                withCurrentVersion(
                        context,
                        view,
                        () ->
                                step(
                                        context,
                                        null,
                                        change.changeByText(
                                                teacherId,
                                                report.id(),
                                                text,
                                                baseVersion(context, report),
                                                Channel.TELEGRAM)));
            }
            default -> context.answer(presentation.failure(context.chatId(), null));
        }
        return true;
    }

    @Override
    public boolean aoAudio(TelegramContext context, TelegramMessage message, TelegramFile file) {
        Optional<LessonReportView> focus = focus(context);
        if (focus.isEmpty()) {
            return false;
        }
        LessonReportView view = focus.get();
        LessonReport report = view.report();
        if (TelegramLessonReportPresentation.inProcessing(view)) {
            context.answer(
                    new Message(context.chatId(), presentation.processing(view), Keyboard.NONE));
            return true;
        }
        UUID teacherId = context.teacher().id();
        ChannelFileReference audio = TelegramLessonRegistrationFlow.reference(message, file);
        switch (report.status()) {
            case AWAITING_ANSWERS ->
                    step(
                            context,
                            null,
                            answer.answerAudio(teacherId, report.id(), audio, Channel.TELEGRAM));
            case AWAITING_REVIEW ->
                    withCurrentVersion(
                            context,
                            view,
                            () ->
                                    step(
                                            context,
                                            null,
                                            change.changeByAudio(
                                                    teacherId,
                                                    report.id(),
                                                    audio,
                                                    baseVersion(context, report),
                                                    Channel.TELEGRAM)));
            default -> context.answer(presentation.failure(context.chatId(), null));
        }
        return true;
    }

    /** Nome exato de outro aluno durante a conferência: pergunta antes de trocar (estado #4). */
    private void askStudentSwitch(
            TelegramContext context, LessonReportView view, StudentSummary student) {
        TelegramConversation conversation = context.conversation();
        conversation.store(
                TelegramConversationInterpreter.STUDENT_OPTIONS, student.id().toString());
        conversation.defineStep(ConversationFlowStep.CHOOSING_STUDENT);
        context.answer(
                new Message(
                        context.chatId(),
                        presenter.text(
                                "SWITCH_STUDENT_DURING_REVIEW",
                                Map.of(
                                        "student",
                                        student.name(),
                                        "currentStudent",
                                        view.studentName())),
                        Keyboard.of(
                                new Button(
                                        presenter.label(
                                                "CONFIRM_STUDENT_BUTTON",
                                                Map.of("student", student.name())),
                                        CallbackDataCodec.encode("student", "0")),
                                presenter.button(
                                        "BACK_BUTTON",
                                        Map.of(),
                                        "focus",
                                        "a" + view.report().id()))));
    }

    /** Versão apresentada desatualizada: avisa e reapresenta a conferência vigente (FR-033). */
    private void withCurrentVersion(
            TelegramContext context, LessonReportView view, Runnable action) {
        try {
            action.run();
        } catch (DomainException error) {
            if (error.code() != ErrorCode.STALE_VERSION) {
                throw error;
            }
            context.answer(presenter.error(context.chatId(), error.code(), error.details()));
            present(context, view.report().id(), null);
        }
    }

    private void step(TelegramContext context, Long edit, ProcessingJob job) {
        context.answer(new StepMessage(context.chatId(), edit, stepText(presenter, job), job.id()));
    }

    static String stepText(TelegramPresenter presenter, ProcessingJob job) {
        return "<i>"
                + TelegramPresenter.escape(presenter.formatting().step(job.step().name()))
                + "…</i>";
    }

    // ------------------------------------------------------------------ comandos e botões

    @Override
    public boolean onCommand(TelegramContext context, Command command) {
        if (command != Command.APPROVE && command != Command.CANCEL && command != Command.RETRY) {
            return false;
        }
        Optional<LessonReportView> focus = focus(context);
        if (focus.isEmpty()) {
            return false;
        }
        LessonReport report = focus.get().report();
        switch (command) {
            case APPROVE -> askApproval(context, focus.get(), baseVersion(context, report));
            case CANCEL -> askCancellation(context);
            default -> retryAgain(context, report.id());
        }
        return true;
    }

    @Override
    public boolean aoCallback(TelegramContext context, String action, String argument) {
        switch (action) {
            case "focus" -> {
                return continueInChat(context, argument);
            }
            case "approve", "approve-ok", "change", "cancel", "cancel-ok", "pdf", "retry" -> {
                Optional<LessonReportView> focus = focus(context);
                if (focus.isEmpty()) {
                    return false;
                }
                return actionInFocus(context, focus.get(), action, argument);
            }
            default -> {
                return false;
            }
        }
    }

    private boolean actionInFocus(
            TelegramContext context, LessonReportView view, String action, String argument) {
        TelegramConversation conversation = context.conversation();
        LessonReport report = view.report();
        switch (action) {
            case "approve" -> {
                Integer version = number(argument);
                if (version == null) {
                    return false;
                }
                conversation.cleanConfirmation();
                askApproval(context, view, version);
            }
            case "approve-ok" -> {
                Integer version = number(argument);
                if (version == null
                        || !conversation
                                .pendingConfirmation()
                                .filter((APPROVE_CONFIRMATION + version)::equals)
                                .isPresent()) {
                    return false;
                }
                conversation.cleanConfirmation();
                approveVersion(context, report.id(), version);
            }
            case "cancel-ok" -> {
                if (!conversation
                        .pendingConfirmation()
                        .filter(CANCEL_CONFIRMATION::equals)
                        .isPresent()) {
                    return false;
                }
                conversation.cleanConfirmation();
                cancelReport(context, report.id());
            }
            case "cancel" -> {
                conversation.cleanConfirmation();
                askCancellation(context);
            }
            case "change" -> {
                conversation.cleanConfirmation();
                if (report.status() != ReportStatus.AWAITING_REVIEW) {
                    return false;
                }
                context.answer(presenter.simple(context.chatId(), "CHANGE_REQUEST_LABEL"));
            }
            case "pdf" -> {
                if (report.status() != ReportStatus.AWAITING_REVIEW) {
                    return false;
                }
                presentation
                        .reviewDocument(context.chatId(), context.teacher().id(), report)
                        .ifPresentOrElse(
                                context::answer,
                                () ->
                                        context.answer(
                                                presenter.simple(
                                                        context.chatId(),
                                                        "REVIEW_PDF_GENERATING")));
            }
            default -> {
                conversation.cleanConfirmation();
                retryAgain(context, report.id());
            }
        }
        return true;
    }

    @Override
    public boolean onConfirm(TelegramContext context, String confirmation) {
        if (!confirmation.startsWith(APPROVE_CONFIRMATION)
                && !confirmation.equals(CANCEL_CONFIRMATION)) {
            return false;
        }
        Optional<LessonReportView> focus = focus(context);
        if (focus.isEmpty()) {
            return false;
        }
        UUID reportId = focus.get().report().id();
        if (confirmation.equals(CANCEL_CONFIRMATION)) {
            cancelReport(context, reportId);
            return true;
        }
        Integer version = number(confirmation.substring(APPROVE_CONFIRMATION.length()));
        if (version == null) {
            return false;
        }
        approveVersion(context, reportId, version);
        return true;
    }

    @Override
    public String whatICanReceive(TelegramContext context) {
        return focus(context)
                .map(
                        view ->
                                view.report().status() == ReportStatus.AWAITING_ANSWERS
                                        ? presenter.label("RECEIVE_REPLY")
                                        : presenter.label("RECEIVE_CHANGE"))
                .orElse(null);
    }

    // ------------------------------------------------------------------ ações

    private void askApproval(TelegramContext context, LessonReportView view, int version) {
        LessonReport report = view.report();
        if (report.status() != ReportStatus.AWAITING_REVIEW
                || TelegramLessonReportPresentation.inProcessing(view)) {
            throw new DomainException(ErrorCode.ACTION_NOT_ALLOWED_AT_STEP);
        }
        if (version != report.currentVersion()) {
            context.answer(
                    presenter.error(
                            context.chatId(),
                            ErrorCode.STALE_VERSION,
                            Map.of("currentVersion", report.currentVersion())));
            present(context, report.id(), null);
            return;
        }
        context.conversation().askConfirmation(APPROVE_CONFIRMATION + version);
        context.answer(
                presenter.confirmation(
                        context.chatId(),
                        presenter.text("CONFIRM_APPROVAL", Map.of("version", version)),
                        presenter.button(
                                "CONFIRM_APPROVAL_BUTTON",
                                Map.of("version", version),
                                "approve-ok",
                                Integer.toString(version))));
    }

    private void approveVersion(TelegramContext context, UUID reportId, int version) {
        ApproveLessonReportUseCase.Approval approval;
        try {
            approval = approve.run(context.teacher().id(), reportId, version, Channel.TELEGRAM);
        } catch (DomainException error) {
            if (error.code() != ErrorCode.STALE_VERSION) {
                throw error;
            }
            context.answer(presenter.error(context.chatId(), error.code(), error.details()));
            present(context, reportId, null);
            return;
        }
        TelegramConversation conversation = context.conversation();
        conversation.cleanFocus();
        conversation.cleanSelection();
        conversation.endFlow();
        context.answer(
                new Message(
                        context.chatId(),
                        presenter.text(
                                "REPORT_APPROVED",
                                Map.of(
                                        "data", presenter.formatting().data(approval.approvedAt()),
                                        "hora",
                                                presenter
                                                        .formatting()
                                                        .hora(approval.approvedAt()))),
                        Keyboard.of(
                                presenter.button("REGISTER_OTHER_LESSON_BUTTON", "register"),
                                presenter.button("HISTORY_BUTTON", "history"))));
    }

    private void askCancellation(TelegramContext context) {
        context.conversation().askConfirmation(CANCEL_CONFIRMATION);
        context.answer(
                presenter.confirmation(
                        context.chatId(),
                        presenter.text("CONFIRM_CANCELLATION"),
                        presenter.button("CONFIRM_CANCELLATION_BUTTON", "cancel-ok")));
    }

    private void cancelReport(TelegramContext context, UUID reportId) {
        cancel.cancel(context.teacher().id(), reportId);
        TelegramConversation conversation = context.conversation();
        conversation.cleanFocus();
        conversation.cleanSelection();
        conversation.endFlow();
        context.answer(presenter.simple(context.chatId(), "REPORT_CANCELLED"));
        interpreter.showHome(context);
    }

    private void retryAgain(TelegramContext context, UUID reportId) {
        ProcessingJob job = retry.run(context.teacher().id(), reportId, Channel.TELEGRAM);
        step(context, context.callbackMessageId(), job);
    }

    /** Botão "Continuar no chat" / "Continuar: {aluno}": revalida e põe o relatório em foco. */
    private boolean continueInChat(TelegramContext context, String argument) {
        if (argument == null || argument.length() < 2) {
            return false;
        }
        UUID id;
        try {
            id = UUID.fromString(argument.substring(1));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        context.conversation().cleanConfirmation();
        switch (argument.charAt(0)) {
            case 'a' -> {
                context.conversation().discardAudio();
                present(context, id, null);
                return true;
            }
            case 't' -> {
                QueryJobUseCase.JobView job = queryJob.run(context.teacher().id(), id);
                if (job.job().reportId() != null && job.job().reportType() == ReportType.LESSON) {
                    present(context, job.job().reportId(), null);
                } else if (job.job().status().inProgress()) {
                    context.answer(
                            presenter.simple(
                                    context.chatId(),
                                    "PROCESSING_IN_PROGRESS",
                                    Map.of(
                                            "step",
                                            presenter.formatting().step(job.job().step().name()))));
                } else {
                    return false;
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void present(TelegramContext context, UUID reportId, String prefix) {
        List<TelegramOutput> outputs =
                presentation.present(
                        context.chatId(),
                        context.teacher().id(),
                        reportId,
                        context.conversation(),
                        prefix);
        outputs.forEach(context::answer);
    }

    private static Integer number(String argument) {
        try {
            return argument == null ? null : Integer.valueOf(argument);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
