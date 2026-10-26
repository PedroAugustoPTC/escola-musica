package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Button;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Message;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Monta as mensagens do bot a partir do catálogo único (FR-003): mesmos textos da Web, adaptados ao
 * chat (HTML escapado, listas e botões). Não decide nada; só apresenta.
 */
@Component
public class TelegramPresenter {

    public static final int TEXT_LIMIT = 4096;

    private final MessageCatalog catalog;
    private final MessageFormatting formatting;

    public TelegramPresenter(MessageCatalog catalog, MessageFormatting formatting) {
        this.catalog = catalog;
        this.formatting = formatting;
    }

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Texto do catálogo, escapado para HTML. */
    public String text(String code, Map<String, ?> parameters) {
        return escape(catalog.text(code, parameters));
    }

    public String text(String code) {
        return text(code, Map.of());
    }

    public String label(String code, Map<String, ?> parameters) {
        return catalog.text(code, parameters);
    }

    public String label(String code) {
        return catalog.text(code);
    }

    public MessageFormatting formatting() {
        return formatting;
    }

    public Message simple(long chatId, String code, Map<String, ?> parameters) {
        return new Message(chatId, text(code, parameters), Keyboard.NONE);
    }

    public Message simple(long chatId, String code) {
        return simple(chatId, code, Map.of());
    }

    /**
     * Erro do núcleo em texto do catálogo. Duplicidade de CPF ou de e-mail nunca mostra ao
     * professor quem já tem o dado: vira o texto neutro {@code CPF_UNAVAILABLE}, sem detalhes.
     */
    public Message error(long chatId, ErrorCode code, Map<String, Object> details) {
        boolean duplicate = code == ErrorCode.DUPLICATE_CPF || code == ErrorCode.DUPLICATE_EMAIL;
        ErrorCode shown = duplicate ? ErrorCode.CPF_UNAVAILABLE : code;
        Map<String, Object> parameters = duplicate ? Map.of() : details;
        return new Message(
                chatId,
                escape(catalog.text(shown.messageKey(), formatting.errorParameters(shown, parameters))),
                Keyboard.NONE);
    }

    public Button button(String labelCode, String action) {
        return new Button(label(labelCode), CallbackDataCodec.encode(action));
    }

    public Button button(
            String labelCode, Map<String, ?> parameters, String action, String argument) {
        return new Button(label(labelCode, parameters), CallbackDataCodec.encode(action, argument));
    }

    /**
     * Aviso de que o PDF não pôde ser enviado, com o botão que o pede de novo: {@code acao} e
     * {@code argumento} são os da ação que gerou o PDF (FR-007c).
     */
    public Message noticePdfUnavailable(long chatId, String action, String argument) {
        Button askAgain =
                argument == null
                        ? button("VIEW_PDF_BUTTON", action)
                        : button("VIEW_PDF_BUTTON", Map.of(), action, argument);
        return new Message(chatId, text("PDF_UNAVAILABLE_NOTICE"), Keyboard.of(askAgain));
    }

    public Keyboard homeActions() {
        return new Keyboard(
                List.of(
                        List.of(button("REGISTER_LESSON_BUTTON", "register")),
                        List.of(button("SEMESTER_REPORT_BUTTON", "semester")),
                        List.of(button("HISTORY_BUTTON", "history")),
                        List.of(
                                button("MY_STUDENTS_BUTTON", "reg-list"),
                                button("REGISTER_STUDENT_BUTTON", "reg-new"))));
    }

    /** Convite para cadastrar o primeiro aluno (FR-042): sem alunos, só esta ação faz sentido. */
    public Keyboard registerFirstStudentAction() {
        return Keyboard.of(button("REGISTER_STUDENT_BUTTON", "reg-new"));
    }

    /** Aviso "Você ainda não tem alunos" com o botão de cadastrar o primeiro. */
    public Message noStudents(long chatId) {
        return new Message(chatId, text("NO_STUDENTS"), registerFirstStudentAction());
    }

    /** Bloco de início (FR-012): saudação, relatórios em aberto, total e até 5 alunos, ações. */
    public Message start(long chatId, GetTeacherHomeUseCase.Home start, String prefix) {
        StringBuilder text = new StringBuilder();
        if (prefix != null) {
            text.append(prefix).append("\n\n");
        }
        text.append("<b>")
                .append(text("GREETING", Map.of("name", start.teacher().firstName())))
                .append("</b>\n")
                .append(text("HOME_SUBTITLE"));
        if (start.withoutStudents()) {
            text.append("\n\n").append(text("NO_STUDENTS"));
            return new Message(chatId, text.toString(), registerFirstStudentAction());
        }
        text.append("\n\n<b>").append(text("HOME_OPEN_REPORTS")).append("</b>\n");
        if (start.openReports().isEmpty()) {
            text.append(text("HOME_WITHOUT_OPEN_REPORTS"));
        } else {
            for (OpenReport openReport : start.openReports()) {
                text.append("• ").append(escape(openItem(openReport))).append('\n');
            }
        }
        text.append("\n\n<b>")
                .append(text("HOME_TOTAL_STUDENTS", Map.of("total", start.totalStudents())))
                .append("</b>");
        for (StudentSummary student : start.students()) {
            text.append("\n• ").append(escape(studentLine(student)));
        }
        List<List<Button>> rows = new ArrayList<>();
        for (OpenReport openReport : start.openReports()) {
            if (openReport.type() == ReportType.LESSON || openReport.reportId() != null) {
                String argument =
                        openReport.reportId() != null
                                ? (openReport.type() == ReportType.LESSON ? "a" : "s")
                                        + openReport.reportId()
                                : "t" + openReport.jobId();
                rows.add(
                        List.of(
                                button(
                                        "CONTINUE_REPORT_BUTTON",
                                        Map.of(
                                                "student",
                                                openReport.studentName(),
                                                "data",
                                                formatting.data(openReport.updatedAt())),
                                        "focus",
                                        argument)));
            }
        }
        rows.addAll(homeActions().rows());
        return new Message(chatId, text.toString(), new Keyboard(rows));
    }

    public String openItem(OpenReport openReport) {
        String status =
                openReport.status() != null
                        ? label("STATUS_" + openReport.status().name())
                        : label("STATUS_PROCESSING");
        return label(
                "OPEN_ITEM",
                Map.of(
                        "student", openReport.studentName(),
                        "type", label("TYPE_" + openReport.type().name()),
                        "outcome", status,
                        "data", formatting.data(openReport.updatedAt()),
                        "time", formatting.time(openReport.updatedAt())));
    }

    public String studentLine(StudentSummary student) {
        StringBuilder line = new StringBuilder(student.name());
        if (student.instrument() != null) {
            line.append(" · ").append(student.instrument());
        }
        if (student.hasOpenReport()) {
            line.append(" · ").append(label("OPEN_REPORT_MARK"));
        }
        return line.toString();
    }

    /** Lista de alunos como botões {@code aluno:<índice>} (o índice fica guardado na conversa). */
    public Message studentList(long chatId, String header, List<StudentSummary> students) {
        List<List<Button>> rows = new ArrayList<>();
        for (int i = 0; i < students.size(); i++) {
            StudentSummary student = students.get(i);
            String label =
                    student.hasOpenReport()
                            ? student.name() + " · " + label("OPEN_REPORT_MARK")
                            : student.name();
            rows.add(
                    List.of(
                            new Button(
                                    label,
                                    CallbackDataCodec.encode("student", Integer.toString(i)))));
        }
        rows.add(List.of(button("HOME_BUTTON", "home")));
        return new Message(chatId, header, new Keyboard(rows));
    }

    /**
     * Texto não reconhecido: a mesma lista de alunos, seguida do menu do professor (contracts/
     * telegram-student-registry.md §6). Nunca cita Web nem administrador.
     */
    public Message studentListWithMenu(long chatId, String header, List<StudentSummary> students) {
        Message list = studentList(chatId, header, students);
        List<List<Button>> rows = new ArrayList<>(list.keyboard().rows());
        Button home = rows.removeLast().getFirst();
        rows.addAll(homeActions().rows());
        rows.add(List.of(home));
        return new Message(chatId, header, new Keyboard(rows));
    }

    /**
     * Aviso "Você tem um relatório em aberto" (FR-034) com o canal de origem e a última alteração,
     * e os botões "Continuar de onde parei" / "Descartar e começar um novo".
     */
    public Message openNotice(long chatId, OpenReport openReport) {
        return new Message(
                chatId,
                error(
                                chatId,
                                ErrorCode.REPORT_ALREADY_OPEN,
                                Map.of("openReport", openReport.details()))
                        .text(),
                Keyboard.of(
                        button("CONTINUE_BUTTON", "continue"),
                        button("DISCARD_BUTTON", "discard")));
    }

    public Message confirmation(long chatId, String text, Button confirm) {
        return new Message(
                chatId,
                text,
                new Keyboard(List.of(List.of(confirm), List.of(button("BACK_BUTTON", "home")))));
    }

    /**
     * Divide textos longos em partes de até 4096 caracteres, quebrando em linhas quando possível.
     */
    public static List<String> splitText(String text) {
        List<String> parts = new ArrayList<>();
        String remaining = text;
        while (remaining.length() > TEXT_LIMIT) {
            int cut = remaining.lastIndexOf('\n', TEXT_LIMIT);
            if (cut <= 0) {
                cut = TEXT_LIMIT;
            }
            parts.add(remaining.substring(0, cut));
            remaining = remaining.substring(cut).stripLeading();
        }
        parts.add(remaining);
        return parts;
    }
}
