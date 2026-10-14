package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.inbound.telegram.TelegramKeywords.Command;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import br.com.felipeblaft.reports.usecase.job.DiscardRegistrationInProcessingUseCase;
import br.com.felipeblaft.reports.usecase.lesson.CancelLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetOpenReportUseCase;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Retomada entre canais (US3, sequência 5.3): depois do aviso "Você tem um relatório em aberto",
 * {@code cont} reapresenta a etapa atual (processamento, perguntas ou conferência na versão
 * vigente) e {@code desc} pede confirmação, descarta e segue para o novo registro, reaproveitando o
 * áudio pendente se ainda for válido.
 */
@Component
@Order(5)
public class TelegramResumptionFlow implements TelegramFlow {

    static final String DISCARD_CONFIRMATION = "discard";

    private final GetOpenReportUseCase getOpen;
    private final CancelLessonReportUseCase cancel;
    private final DiscardRegistrationInProcessingUseCase discardRegistration;
    private final SearchStudentsUseCase searchStudents;
    private final TelegramLessonReportPresentation presentation;
    private final TelegramPresenter presenter;
    private final TelegramLessonRegistrationFlow registration;
    private final TelegramConversationInterpreter interpreter;
    private final BusinessParameters parameters;
    private final ClockPort clock;

    @SuppressWarnings("java:S107")
    public TelegramResumptionFlow(
            GetOpenReportUseCase getOpen,
            CancelLessonReportUseCase cancel,
            DiscardRegistrationInProcessingUseCase discardRegistration,
            SearchStudentsUseCase searchStudents,
            TelegramLessonReportPresentation presentation,
            TelegramPresenter presenter,
            TelegramLessonRegistrationFlow registration,
            TelegramConversationInterpreter interpreter,
            BusinessParameters parameters,
            ClockPort clock) {
        this.getOpen = getOpen;
        this.cancel = cancel;
        this.discardRegistration = discardRegistration;
        this.searchStudents = searchStudents;
        this.presentation = presentation;
        this.presenter = presenter;
        this.registration = registration;
        this.interpreter = interpreter;
        this.parameters = parameters;
        this.clock = clock;
    }

    /** Aluno do aviso em aberto guardado na conversa, se a conversa está nessa etapa. */
    private Optional<UUID> noticeStudent(TelegramContext context) {
        TelegramConversation conversation = context.conversation();
        if (conversation.step() != ConversationFlowStep.OPEN_REPORT_NOTICE) {
            return Optional.empty();
        }
        return conversation
                .data(TelegramLessonRegistrationFlow.STUDENT_WITH_OPEN_REPORT)
                .map(UUID::fromString);
    }

    @Override
    public boolean aoCallback(TelegramContext context, String action, String argument) {
        switch (action) {
            case "continue" -> {
                return noticeStudent(context)
                        .map(student -> proceed(context, student))
                        .orElse(false);
            }
            case "discard" -> {
                return noticeStudent(context).map(student -> askDiscard(context)).orElse(false);
            }
            case "discard-ok" -> {
                Optional<UUID> student = noticeStudent(context);
                if (student.isEmpty()
                        || !context.conversation()
                                .pendingConfirmation()
                                .filter(DISCARD_CONFIRMATION::equals)
                                .isPresent()) {
                    return false;
                }
                context.conversation().cleanConfirmation();
                return discard(context, student.get());
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean onCommand(TelegramContext context, Command command) {
        Optional<UUID> student = noticeStudent(context);
        if (student.isEmpty()) {
            return false;
        }
        return switch (command) {
            case CONTINUE -> proceed(context, student.get());
            case DISCARD -> askDiscard(context);
            default -> false;
        };
    }

    @Override
    public boolean onConfirm(TelegramContext context, String confirmation) {
        if (!DISCARD_CONFIRMATION.equals(confirmation)) {
            return false;
        }
        return noticeStudent(context).map(student -> discard(context, student)).orElse(false);
    }

    @Override
    public String whatICanReceive(TelegramContext context) {
        return noticeStudent(context).isPresent()
                ? presenter.label("CONTINUE_BUTTON") + " / " + presenter.label("DISCARD_BUTTON")
                : null;
    }

    // ------------------------------------------------------------------ ações

    private boolean proceed(TelegramContext context, UUID studentId) {
        TelegramConversation conversation = context.conversation();
        conversation.discardAudio();
        conversation.endFlow();
        Optional<OpenReport> openReport = getOpen.run(context.teacher().id(), studentId);
        if (openReport.isEmpty()) {
            // concluído ou descartado no outro canal enquanto isso: segue para um novo registro
            student(context, studentId)
                    .ifPresent(a -> interpreter.selectForRegistration(context, a));
            return true;
        }
        OpenReport report = openReport.get();
        if (report.reportId() != null && report.type() == ReportType.LESSON) {
            presentation
                    .present(
                            context.chatId(),
                            context.teacher().id(),
                            report.reportId(),
                            conversation,
                            null)
                    .forEach(context::answer);
        } else {
            String step = report.step() == null ? "TRANSCRIBING" : report.step().name();
            context.answer(
                    presenter.simple(
                            context.chatId(),
                            "PROCESSING_IN_PROGRESS",
                            Map.of("step", presenter.formatting().step(step))));
        }
        return true;
    }

    private boolean askDiscard(TelegramContext context) {
        context.conversation().askConfirmation(DISCARD_CONFIRMATION);
        context.answer(
                presenter.confirmation(
                        context.chatId(),
                        presenter.text("CONFIRM_DISCARD"),
                        presenter.button("CONFIRM_DISCARD_BUTTON", "discard-ok")));
        return true;
    }

    private boolean discard(TelegramContext context, UUID studentId) {
        UUID teacherId = context.teacher().id();
        getOpen.run(teacherId, studentId)
                .ifPresent(
                        openReport -> {
                            if (openReport.reportId() != null) {
                                cancel.discard(teacherId, openReport.reportId());
                            } else if (openReport.jobId() != null) {
                                discardRegistration.run(teacherId, openReport.jobId());
                            }
                        });
        TelegramConversation conversation = context.conversation();
        conversation.cleanFocus();
        conversation.discardAudioIfExpired(clock.now(), parameters.pendingAudioExpiry());
        Optional<StudentSummary> student = student(context, studentId);
        if (student.isEmpty()) {
            interpreter.showHome(context);
            return true;
        }
        context.answer(presenter.simple(context.chatId(), "REPORT_CANCELLED"));
        if (conversation.pendingAudio().isPresent()) {
            // o áudio enviado antes do aviso ainda vale: vira o novo registro
            registration.registerPendingAudio(context, student.get(), context.callbackMessageId());
        } else {
            interpreter.selectForRegistration(context, student.get());
        }
        return true;
    }

    private Optional<StudentSummary> student(TelegramContext context, UUID studentId) {
        return searchStudents.run(context.teacher().id(), "").students().stream()
                .filter(a -> a.id().equals(studentId))
                .findFirst();
    }
}
