package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.LessonReport;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.telegram.FocusReport;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Button;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Document;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Message;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.GetLessonReportUseCase.LessonReportView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Apresenta a etapa atual de um relatório de aula no chat (sequência 5.2): processamento,
 * perguntas, conferência com o PDF, falha ou situação final. Ao apresentar perguntas ou
 * conferência, põe o relatório em foco e grava a versão apresentada na conversa (FR-027b, FR-033).
 */
@Component
public class TelegramLessonReportPresentation {

    private final GetLessonReportUseCase getReport;
    private final GetReportDocumentUseCase getDocument;
    private final TelegramPresenter presenter;

    public TelegramLessonReportPresentation(
            GetLessonReportUseCase getReport,
            GetReportDocumentUseCase getDocument,
            TelegramPresenter presenter) {
        this.getReport = getReport;
        this.getDocument = getDocument;
        this.presenter = presenter;
    }

    public LessonReportView get(UUID teacherId, UUID reportId) {
        return getReport.run(teacherId, reportId);
    }

    /** Apresenta a etapa atual; {@code prefixo} (já escapado) vem antes, se houver. */
    public List<TelegramOutput> present(
            long chatId,
            UUID teacherId,
            UUID reportId,
            TelegramConversation conversation,
            String prefix) {
        return present(chatId, teacherId, get(teacherId, reportId), conversation, prefix);
    }

    public List<TelegramOutput> present(
            long chatId,
            UUID teacherId,
            LessonReportView view,
            TelegramConversation conversation,
            String prefix) {
        LessonReport report = view.report();
        if (report.status() == ReportStatus.APPROVED || report.status() == ReportStatus.CANCELLED) {
            conversation.cleanFocus();
            String code =
                    report.status() == ReportStatus.APPROVED
                            ? "REPORT_ALREADY_APPROVED"
                            : "REPORT_CANCELLED_ERROR";
            return List.of(
                    new Message(
                            chatId,
                            withPrefix(prefix, presenter.text(code)),
                            presenter.homeActions()));
        }
        conversation.focus(
                new FocusReport(ReportType.LESSON, report.id()), report.currentVersion());
        if (inProcessing(view)) {
            return List.of(
                    new Message(chatId, withPrefix(prefix, processing(view)), Keyboard.NONE));
        }
        return switch (report.status()) {
            case AWAITING_ANSWERS -> List.of(questions(chatId, report, prefix));
            case AWAITING_REVIEW -> review(chatId, teacherId, view, prefix);
            default -> List.of(failure(chatId, prefix));
        };
    }

    public static boolean inProcessing(LessonReportView view) {
        return view.jobInProgress().isPresent()
                || view.report().step() != null
                || view.report().status() == ReportStatus.PROCESSING;
    }

    public String processing(LessonReportView view) {
        String step =
                view.jobInProgress()
                        .map(t -> t.step().name())
                        .or(() -> Optional.ofNullable(view.report().step()).map(Enum::name))
                        .orElse("STRUCTURING");
        return presenter.text(
                "PROCESSING_IN_PROGRESS", Map.of("step", presenter.formatting().step(step)));
    }

    public Message questions(long chatId, LessonReport report, String prefix) {
        StringBuilder numbered = new StringBuilder();
        List<String> questions = report.pendingQuestions();
        for (int i = 0; i < questions.size(); i++) {
            numbered.append('\n').append(i + 1).append(". ").append(questions.get(i));
        }
        String text =
                presenter.text("PENDING_QUESTIONS", Map.of("numberedQuestions", numbered))
                        + "\n\n<i>"
                        + presenter.text("FOCUS_REMINDER")
                        + "</i>";
        return new Message(
                chatId,
                withPrefix(prefix, text),
                Keyboard.of(
                        presenter.button("CANCEL_REPORT_BUTTON", "cancel"),
                        presenter.button("REGISTER_LESSON_BUTTON", "register")));
    }

    /** Cabeçalho e seções em texto, o PDF como documento e as ações com o lembrete de foco. */
    public List<TelegramOutput> review(
            long chatId, UUID teacherId, LessonReportView view, String prefix) {
        LessonReport report = view.report();
        List<TelegramOutput> outputs = new ArrayList<>();
        outputs.add(new Message(chatId, withPrefix(prefix, reviewText(view)), Keyboard.NONE));
        reviewDocument(chatId, teacherId, report)
                .ifPresentOrElse(
                        outputs::add,
                        () -> outputs.add(presenter.simple(chatId, "REVIEW_PDF_GENERATING")));
        outputs.add(reviewActions(chatId, report.currentVersion(), null));
        return outputs;
    }

    public Optional<TelegramOutput> reviewDocument(
            long chatId, UUID teacherId, LessonReport report) {
        try {
            GetReportDocumentUseCase.ObtainedDocument document =
                    getDocument.prepareReview(
                            teacherId, ReportType.LESSON, report.id(), report.currentVersion());
            return Optional.of(
                    new Document(
                            chatId,
                            document.content(),
                            document.fileName(),
                            null,
                            DocumentReference.review(
                                    teacherId,
                                    ReportType.LESSON,
                                    report.id(),
                                    report.currentVersion()),
                            presenter.noticePdfUnavailable(chatId, "pdf", null)));
        } catch (DomainException withoutDocument) {
            return Optional.empty();
        }
    }

    /** Lembrete de foco com os botões da conferência ({@code aprov} cita a versão apresentada). */
    public Message reviewActions(long chatId, int version, String prefix) {
        String text = "<i>" + presenter.text("FOCUS_REMINDER") + "</i>";
        return new Message(
                chatId,
                withPrefix(prefix, text),
                new Keyboard(
                        List.of(
                                List.of(
                                        new Button(
                                                presenter.label("APPROVE_BUTTON"),
                                                CallbackDataCodec.encode(
                                                        "approve", Integer.toString(version)))),
                                List.of(presenter.button("REQUEST_CHANGE_BUTTON", "change")),
                                List.of(
                                        presenter.button("VIEW_PDF_BUTTON", "pdf"),
                                        presenter.button("CANCEL_REPORT_BUTTON", "cancel")),
                                List.of(presenter.button("REGISTER_LESSON_BUTTON", "register")))));
    }

    public String reviewText(LessonReportView view) {
        LessonReport report = view.report();
        LessonReportContent content = report.content();
        StringBuilder text = new StringBuilder();
        text.append("<b>").append(presenter.text("REVIEW_TITLE")).append("</b>\n");
        line(text, "STUDENT_HEADER", view.studentName());
        line(text, "TEACHER_HEADER", view.teacherName());
        line(text, "LESSON_DATE_HEADER", presenter.formatting().data(view.lesson().lessonDate()));
        text.append(presenter.text("REVIEW_VERSION", Map.of("version", report.currentVersion())));
        list(text, "WORKED_TOPICS_SECTION", content.workedTopics());
        paragraph(text, "PROGRESS_SECTION", content.progress());
        list(text, "DIFFICULTIES_SECTION", content.difficulties());
        list(text, "PROPOSED_ACTIVITIES_SECTION", content.proposedActivities());
        paragraph(text, "OBSERVATIONS_SECTION", content.observations());
        return text.toString();
    }

    public Message failure(long chatId, String prefix) {
        return new Message(
                chatId,
                withPrefix(prefix, presenter.text("PROCESSING_FAILURE_OPTIONS")),
                Keyboard.of(
                        presenter.button("RETRY_BUTTON", "retry"),
                        presenter.button("CANCEL_REPORT_BUTTON", "cancel")));
    }

    private void line(StringBuilder text, String label, String value) {
        text.append(presenter.text(label))
                .append(": ")
                .append(TelegramPresenter.escape(value))
                .append('\n');
    }

    private void list(StringBuilder text, String section, List<String> items) {
        text.append("\n\n<b>").append(presenter.text(section)).append("</b>");
        if (items.isEmpty()) {
            text.append('\n').append(presenter.text("EMPTY_SECTION"));
        }
        for (String item : items) {
            text.append("\n• ").append(TelegramPresenter.escape(item));
        }
    }

    private void paragraph(StringBuilder text, String section, String value) {
        text.append("\n\n<b>")
                .append(presenter.text(section))
                .append("</b>\n")
                .append(
                        value == null || value.isBlank()
                                ? presenter.text("EMPTY_SECTION")
                                : TelegramPresenter.escape(value));
    }

    static String withPrefix(String prefix, String text) {
        return prefix == null || prefix.isBlank() ? text : prefix + "\n\n" + text;
    }
}
