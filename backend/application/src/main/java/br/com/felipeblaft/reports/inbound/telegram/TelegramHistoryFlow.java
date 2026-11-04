package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Button;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Document;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Message;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase.ObtainedDocument;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase.History;
import br.com.felipeblaft.reports.usecase.history.QueryStudentHistoryUseCase.HistoryItem;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Histórico pelo bot (sequência 5.5, estado #9): lista numerada dos relatórios aprovados, 10 por
 * página com "Mais", com os mesmos rótulos da Web; tocar num item envia o PDF oficial.
 */
@Component
@Order(9)
public class TelegramHistoryFlow implements TelegramFlow {

    static final int PER_PAGE = 10;
    static final String STUDENT = "historyStudent";
    static final String ITEMS = "historyItems";

    private final QueryStudentHistoryUseCase query;
    private final GetReportDocumentUseCase getDocument;
    private final TelegramPresenter presenter;

    public TelegramHistoryFlow(
            QueryStudentHistoryUseCase query,
            GetReportDocumentUseCase getDocument,
            TelegramPresenter presenter) {
        this.query = query;
        this.getDocument = getDocument;
        this.presenter = presenter;
    }

    @Override
    public boolean onChooseStudent(TelegramContext context, StudentSummary student) {
        TelegramConversation conversation = context.conversation();
        if (conversation.flow() != ConversationFlow.HISTORY) {
            return false;
        }
        conversation.cleanFocus();
        History history = query.run(context.teacher().id(), student.id());
        if (history.items().isEmpty()) {
            conversation.endFlow();
            context.answer(
                    new Message(
                            context.chatId(),
                            presenter.text("EMPTY_HISTORY"),
                            Keyboard.of(presenter.button("REGISTER_LESSON_BUTTON", "register"))));
            return true;
        }
        conversation.defineStep(ConversationFlowStep.CHOOSING_HISTORY_ITEM);
        conversation.store(STUDENT, student.id().toString());
        conversation.store(
                ITEMS,
                String.join(
                        ",",
                        history.items().stream()
                                .map(
                                        i ->
                                                (i.type() == ReportType.LESSON ? "a" : "s")
                                                        + i.reportId())
                                .toList()));
        showPage(context, history, 0);
        return true;
    }

    private void showPage(TelegramContext context, History history, int page) {
        List<HistoryItem> items = history.items();
        int start = page * PER_PAGE;
        int end = Math.min(items.size(), start + PER_PAGE);
        StringBuilder text =
                new StringBuilder("<b>")
                        .append(
                                presenter.text(
                                        "HISTORY_TITLE",
                                        Map.of("student", history.student().name())))
                        .append("</b>");
        List<List<Button>> rows = new ArrayList<>();
        for (int i = start; i < end; i++) {
            HistoryItem item = items.get(i);
            String label = presenter.formatting().historyLabel(item);
            text.append('\n')
                    .append(i + 1)
                    .append(". ")
                    .append(TelegramPresenter.escape(label))
                    .append(" · ")
                    .append(
                            TelegramPresenter.escape(
                                    presenter.formatting().approvedAt(item.approvedAt())));
            rows.add(
                    List.of(
                            new Button(
                                    label,
                                    CallbackDataCodec.encode(
                                            "history-item", Integer.toString(i)))));
        }
        if (end < items.size()) {
            rows.add(
                    List.of(
                            presenter.button(
                                    "MORE_BUTTON",
                                    Map.of(),
                                    "history-more",
                                    Integer.toString(page + 1))));
        }
        rows.add(List.of(presenter.button("HOME_BUTTON", "home")));
        context.answer(new Message(context.chatId(), text.toString(), new Keyboard(rows)));
    }

    @Override
    public boolean aoCallback(TelegramContext context, String action, String argument) {
        TelegramConversation conversation = context.conversation();
        if (conversation.step() != ConversationFlowStep.CHOOSING_HISTORY_ITEM) {
            return false;
        }
        Optional<UUID> student = conversation.data(STUDENT).map(UUID::fromString);
        Integer number = number(argument);
        if (student.isEmpty() || number == null) {
            return false;
        }
        switch (action) {
            case "history-more" -> {
                History history = query.run(context.teacher().id(), student.get());
                if (number < 0 || number * PER_PAGE >= history.items().size()) {
                    return false;
                }
                showPage(context, history, number);
                return true;
            }
            case "history-item" -> {
                List<String> items =
                        conversation.data(ITEMS).map(s -> List.of(s.split(","))).orElse(List.of());
                if (number < 0 || number >= items.size()) {
                    return false;
                }
                String item = items.get(number);
                ReportType type = item.charAt(0) == 'a' ? ReportType.LESSON : ReportType.SEMESTER;
                ObtainedDocument document =
                        getDocument.prepareOfficial(
                                context.teacher().id(), type, UUID.fromString(item.substring(1)));
                context.answer(
                        new Document(
                                context.chatId(),
                                document.content(),
                                document.fileName(),
                                null,
                                DocumentReference.official(
                                        context.teacher().id(),
                                        type,
                                        UUID.fromString(item.substring(1))),
                                presenter.noticePdfUnavailable(
                                        context.chatId(),
                                        "history-item",
                                        Integer.toString(number))));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public String whatICanReceive(TelegramContext context) {
        return context.conversation().step() == ConversationFlowStep.CHOOSING_HISTORY_ITEM
                ? presenter.label("RECEIVE_STUDENT")
                : null;
    }

    private static Integer number(String argument) {
        try {
            return argument == null ? null : Integer.valueOf(argument);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
