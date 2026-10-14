package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.config.SemesterReportSections;
import br.com.felipeblaft.reports.config.SemesterReportSections.Field;
import br.com.felipeblaft.reports.config.SemesterReportSections.Section;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.telegram.FocusReport;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Document;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Message;
import br.com.felipeblaft.reports.usecase.document.GetReportDocumentUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase.SemesterReportView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Apresenta o semestral no chat (sequência 5.4): conferência com "N relatórios de aula foram
 * considerados", o aviso de relatórios em aberto, as 10 seções em texto, o PDF e as mesmas ações da
 * conferência de aula. Põe o semestral em foco com a versão apresentada.
 */
@Component
public class TelegramSemesterReportPresentation {

    private final GetSemesterReportUseCase getReport;
    private final GetReportDocumentUseCase getDocument;
    private final TelegramPresenter presenter;
    private final TelegramLessonReportPresentation lessonPresentation;
    private final SemesterReportSections sections;

    public TelegramSemesterReportPresentation(
            GetSemesterReportUseCase getReport,
            GetReportDocumentUseCase getDocument,
            TelegramPresenter presenter,
            TelegramLessonReportPresentation lessonPresentation,
            SemesterReportSections sections) {
        this.getReport = getReport;
        this.getDocument = getDocument;
        this.presenter = presenter;
        this.lessonPresentation = lessonPresentation;
        this.sections = sections;
    }

    public SemesterReportView get(UUID teacherId, UUID reportId) {
        return getReport.run(teacherId, reportId);
    }

    public static boolean inProcessing(SemesterReportView view) {
        return view.jobInProgress().isPresent() || view.report().step() != null;
    }

    public String processing(SemesterReportView view) {
        String step =
                view.jobInProgress()
                        .map(t -> t.step().name())
                        .or(() -> Optional.ofNullable(view.report().step()).map(Enum::name))
                        .orElse("APLICANDO_ALTERACAO");
        return presenter.text(
                "PROCESSAMENTO_EM_ANDAMENTO", Map.of("etapa", presenter.formatting().step(step)));
    }

    public List<TelegramOutput> present(
            long chatId,
            UUID teacherId,
            UUID reportId,
            TelegramConversation conversation,
            String prefix) {
        SemesterReportView view = get(teacherId, reportId);
        SemesterReport report = view.report();
        if (!report.status().isOpen()) {
            conversation.cleanFocus();
            String code =
                    report.status() == ReportStatus.APROVADO
                            ? "RELATORIO_JA_APROVADO"
                            : "RELATORIO_CANCELADO_ERRO";
            return List.of(
                    new Message(
                            chatId,
                            TelegramLessonReportPresentation.withPrefix(
                                    prefix, presenter.text(code)),
                            presenter.homeActions()));
        }
        conversation.focus(
                new FocusReport(ReportType.SEMESTRAL, report.id()), report.currentVersion());
        if (inProcessing(view)) {
            return List.of(
                    new Message(
                            chatId,
                            TelegramLessonReportPresentation.withPrefix(prefix, processing(view)),
                            Keyboard.NONE));
        }
        List<TelegramOutput> outputs = new ArrayList<>();
        outputs.add(
                new Message(
                        chatId,
                        TelegramLessonReportPresentation.withPrefix(prefix, reviewText(report)),
                        Keyboard.NONE));
        reviewDocument(chatId, teacherId, report)
                .ifPresentOrElse(
                        outputs::add,
                        () -> outputs.add(presenter.simple(chatId, "CONFERENCIA_PDF_GERANDO")));
        outputs.add(lessonPresentation.reviewActions(chatId, report.currentVersion(), null));
        return outputs;
    }

    public Optional<TelegramOutput> reviewDocument(
            long chatId, UUID teacherId, SemesterReport report) {
        try {
            GetReportDocumentUseCase.ObtainedDocument document =
                    getDocument.review(
                            teacherId, ReportType.SEMESTRAL, report.id(), report.currentVersion());
            return Optional.of(
                    new Document(
                            chatId,
                            document.content(),
                            document.fileName(),
                            null,
                            DocumentReference.review(
                                    teacherId,
                                    ReportType.SEMESTRAL,
                                    report.id(),
                                    report.currentVersion()),
                            presenter.noticePdfUnavailable(chatId, "pdf", null)));
        } catch (DomainException withoutDocument) {
            return Optional.empty();
        }
    }

    public String reviewText(SemesterReport report) {
        StringBuilder text = new StringBuilder();
        text.append("<b>").append(presenter.text("CONFERENCIA_TITULO")).append("</b>\n");
        text.append(presenter.text("CABECALHO_ALUNO"))
                .append(": ")
                .append(TelegramPresenter.escape(report.generalInformation().name()))
                .append('\n');
        text.append(presenter.text("CABECALHO_PERIODO"))
                .append(": ")
                .append(TelegramPresenter.escape(sections.periodLabel(report.period().key())))
                .append('\n');
        text.append(
                presenter.text("CONFERENCIA_VERSAO", Map.of("versao", report.currentVersion())));
        text.append("\n\n")
                .append(
                        presenter.text(
                                "AULAS_CONSIDERADAS",
                                Map.of("quantidade", report.consideredLessonCount())));
        if (report.openReportCount() > 0) {
            text.append('\n')
                    .append(
                            presenter.text(
                                    "HA_RELATORIOS_EM_ABERTO",
                                    Map.of("quantidade", report.openReportCount())));
        }
        for (Section section : sections.sections(report.generalInformation(), report.content())) {
            text.append("\n\n<b>")
                    .append(section.getNumber())
                    .append(". ")
                    .append(TelegramPresenter.escape(section.getTitle()))
                    .append("</b>");
            for (Field field : section.getFields()) {
                text.append('\n');
                if (field.getLabel() != null) {
                    text.append("<i>")
                            .append(TelegramPresenter.escape(field.getLabel()))
                            .append("</i>: ");
                }
                if (field.empty()) {
                    text.append(presenter.text("SECAO_VAZIA"));
                } else if (field.getItems().isEmpty()) {
                    text.append(TelegramPresenter.escape(field.getText()));
                } else {
                    for (String item : field.getItems()) {
                        text.append("\n• ").append(TelegramPresenter.escape(item));
                    }
                }
            }
        }
        return text.toString();
    }
}
