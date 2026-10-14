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
        if (report.status() == ReportStatus.APROVADO || report.status() == ReportStatus.CANCELADO) {
            conversation.cleanFocus();
            String code =
                    report.status() == ReportStatus.APROVADO
                            ? "RELATORIO_JA_APROVADO"
                            : "RELATORIO_CANCELADO_ERRO";
            return List.of(
                    new Message(
                            chatId,
                            withPrefix(prefix, presenter.text(code)),
                            presenter.homeActions()));
        }
        conversation.focus(new FocusReport(ReportType.AULA, report.id()), report.currentVersion());
        if (inProcessing(view)) {
            return List.of(
                    new Message(chatId, withPrefix(prefix, processing(view)), Keyboard.NONE));
        }
        return switch (report.status()) {
            case AGUARDANDO_RESPOSTAS -> List.of(questions(chatId, report, prefix));
            case AGUARDANDO_CONFERENCIA -> review(chatId, teacherId, view, prefix);
            default -> List.of(failure(chatId, prefix));
        };
    }

    public static boolean inProcessing(LessonReportView view) {
        return view.jobInProgress().isPresent()
                || view.report().step() != null
                || view.report().status() == ReportStatus.EM_PROCESSAMENTO;
    }

    public String processing(LessonReportView view) {
        String step =
                view.jobInProgress()
                        .map(t -> t.step().name())
                        .or(() -> Optional.ofNullable(view.report().step()).map(Enum::name))
                        .orElse("ESTRUTURANDO");
        return presenter.text(
                "PROCESSAMENTO_EM_ANDAMENTO", Map.of("etapa", presenter.formatting().step(step)));
    }

    public Message questions(long chatId, LessonReport report, String prefix) {
        StringBuilder numbered = new StringBuilder();
        List<String> questions = report.pendingQuestions();
        for (int i = 0; i < questions.size(); i++) {
            numbered.append('\n').append(i + 1).append(". ").append(questions.get(i));
        }
        String text =
                presenter.text("PERGUNTAS_PENDENTES", Map.of("perguntasNumeradas", numbered))
                        + "\n\n<i>"
                        + presenter.text("LEMBRETE_FOCO")
                        + "</i>";
        return new Message(
                chatId,
                withPrefix(prefix, text),
                Keyboard.of(
                        presenter.button("BOTAO_CANCELAR_RELATORIO", "canc"),
                        presenter.button("BOTAO_REGISTRAR_AULA", "reg")));
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
                        () -> outputs.add(presenter.simple(chatId, "CONFERENCIA_PDF_GERANDO")));
        outputs.add(reviewActions(chatId, report.currentVersion(), null));
        return outputs;
    }

    public Optional<TelegramOutput> reviewDocument(
            long chatId, UUID teacherId, LessonReport report) {
        try {
            GetReportDocumentUseCase.ObtainedDocument document =
                    getDocument.review(
                            teacherId, ReportType.AULA, report.id(), report.currentVersion());
            return Optional.of(
                    new Document(
                            chatId,
                            document.content(),
                            document.fileName(),
                            null,
                            DocumentReference.review(
                                    teacherId,
                                    ReportType.AULA,
                                    report.id(),
                                    report.currentVersion()),
                            presenter.noticePdfUnavailable(chatId, "pdf", null)));
        } catch (DomainException withoutDocument) {
            return Optional.empty();
        }
    }

    /** Lembrete de foco com os botões da conferência ({@code aprov} cita a versão apresentada). */
    public Message reviewActions(long chatId, int version, String prefix) {
        String text = "<i>" + presenter.text("LEMBRETE_FOCO") + "</i>";
        return new Message(
                chatId,
                withPrefix(prefix, text),
                new Keyboard(
                        List.of(
                                List.of(
                                        new Button(
                                                presenter.label("BOTAO_APROVAR"),
                                                CallbackDataCodec.encode(
                                                        "aprov", Integer.toString(version)))),
                                List.of(presenter.button("BOTAO_SOLICITAR_ALTERACAO", "alt")),
                                List.of(
                                        presenter.button("BOTAO_VER_PDF", "pdf"),
                                        presenter.button("BOTAO_CANCELAR_RELATORIO", "canc")),
                                List.of(presenter.button("BOTAO_REGISTRAR_AULA", "reg")))));
    }

    public String reviewText(LessonReportView view) {
        LessonReport report = view.report();
        LessonReportContent content = report.content();
        StringBuilder text = new StringBuilder();
        text.append("<b>").append(presenter.text("CONFERENCIA_TITULO")).append("</b>\n");
        line(text, "CABECALHO_ALUNO", view.studentName());
        line(text, "CABECALHO_PROFESSOR", view.teacherName());
        line(text, "CABECALHO_DATA_AULA", presenter.formatting().data(view.lesson().lessonDate()));
        text.append(
                presenter.text("CONFERENCIA_VERSAO", Map.of("versao", report.currentVersion())));
        list(text, "SECAO_CONTEUDOS_TRABALHADOS", content.workedTopics());
        paragraph(text, "SECAO_EVOLUCAO", content.progress());
        list(text, "SECAO_DIFICULDADES", content.difficulties());
        list(text, "SECAO_ATIVIDADES_PROPOSTAS", content.proposedActivities());
        paragraph(text, "SECAO_OBSERVACOES", content.observations());
        return text.toString();
    }

    public Message failure(long chatId, String prefix) {
        return new Message(
                chatId,
                withPrefix(prefix, presenter.text("FALHA_PROCESSAMENTO_OPCOES")),
                Keyboard.of(
                        presenter.button("BOTAO_TENTAR_NOVAMENTE", "tent"),
                        presenter.button("BOTAO_CANCELAR_RELATORIO", "canc")));
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
            text.append('\n').append(presenter.text("SECAO_VAZIA"));
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
                                ? presenter.text("SECAO_VAZIA")
                                : TelegramPresenter.escape(value));
    }

    static String withPrefix(String prefix, String text) {
        return prefix == null || prefix.isBlank() ? text : prefix + "\n\n" + text;
    }
}
