package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.config.SemesterReportSections;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.semester.SemesterReport;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
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
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import br.com.felipeblaft.reports.usecase.lesson.ApproveLessonReportUseCase.Approval;
import br.com.felipeblaft.reports.usecase.semester.ApproveSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.CancelSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GenerateSemesterReportUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterPreviewUseCase.Preview;
import br.com.felipeblaft.reports.usecase.semester.GetSemesterReportUseCase.SemesterReportView;
import br.com.felipeblaft.reports.usecase.semester.ListAvailableSemestersUseCase;
import br.com.felipeblaft.reports.usecase.semester.ListAvailableSemestersUseCase.Semester;
import br.com.felipeblaft.reports.usecase.semester.RequestSemesterReportChangeUseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Relatório semestral pelo bot (sequência 5.4, estado #8): aluno, período, prévia, geração e a
 * conferência com alteração, aprovação e cancelamento — os mesmos casos de uso da Web.
 */
@Component
@Order(8)
public class TelegramSemesterFlow implements TelegramFlow {

    static final String STUDENT = "semesterStudent";
    static final String OPTIONS = "semesterOptions";
    static final String PERIOD = "semesterPeriod";

    private final ListAvailableSemestersUseCase list;
    private final GetSemesterPreviewUseCase preview;
    private final GenerateSemesterReportUseCase generate;
    private final RequestSemesterReportChangeUseCase change;
    private final ApproveSemesterReportUseCase approve;
    private final CancelSemesterReportUseCase cancel;
    private final SearchStudentsUseCase searchStudents;
    private final TelegramSemesterReportPresentation presentation;
    private final TelegramPresenter presenter;
    private final SemesterReportSections sections;
    private final TelegramConversationInterpreter interpreter;

    @SuppressWarnings("java:S107")
    public TelegramSemesterFlow(
            ListAvailableSemestersUseCase list,
            GetSemesterPreviewUseCase preview,
            GenerateSemesterReportUseCase generate,
            RequestSemesterReportChangeUseCase change,
            ApproveSemesterReportUseCase approve,
            CancelSemesterReportUseCase cancel,
            SearchStudentsUseCase searchStudents,
            TelegramSemesterReportPresentation presentation,
            TelegramPresenter presenter,
            SemesterReportSections sections,
            TelegramConversationInterpreter interpreter) {
        this.list = list;
        this.preview = preview;
        this.generate = generate;
        this.change = change;
        this.approve = approve;
        this.cancel = cancel;
        this.searchStudents = searchStudents;
        this.presentation = presentation;
        this.presenter = presenter;
        this.sections = sections;
        this.interpreter = interpreter;
    }

    // ------------------------------------------------------------------ escolha do período

    @Override
    public boolean onChooseStudent(TelegramContext context, StudentSummary student) {
        if (context.conversation().flow() != ConversationFlow.SEMESTRAL) {
            return false;
        }
        offerPeriods(context, student);
        return true;
    }

    private void offerPeriods(TelegramContext context, StudentSummary student) {
        TelegramConversation conversation = context.conversation();
        conversation.cleanFocus();
        conversation.startFlow(
                ConversationFlow.SEMESTRAL, ConversationFlowStep.ESCOLHENDO_SEMESTRE);
        conversation.store(STUDENT, student.id().toString());
        List<Semester> semesters = list.run(context.teacher().id(), student.id());
        if (semesters.isEmpty()) {
            context.answer(presenter.simple(context.chatId(), "SEM_SEMESTRES"));
            return;
        }
        conversation.store(
                OPTIONS, String.join(",", semesters.stream().map(s -> s.period().key()).toList()));
        StringBuilder text =
                new StringBuilder(
                        presenter.text("ESCOLHER_SEMESTRE", Map.of("aluno", student.name())));
        List<List<Button>> rows = new ArrayList<>();
        for (int i = 0; i < semesters.size(); i++) {
            Semester semester = semesters.get(i);
            String label = sections.periodLabel(semester.period().key());
            text.append('\n')
                    .append(
                            presenter.text(
                                    "SEMESTRE_INTERVALO",
                                    Map.of(
                                            "rotulo", label,
                                            "inicio",
                                                    presenter
                                                            .formatting()
                                                            .data(semester.period().start()),
                                            "fim",
                                                    presenter
                                                            .formatting()
                                                            .data(semester.period().end()))));
            String button =
                    (semester.preSelected() ? "★ " : "")
                            + label
                            + (semester.inProgress()
                                    ? " · " + presenter.label("SEMESTRE_EM_ANDAMENTO")
                                    : "");
            rows.add(
                    List.of(
                            new Button(
                                    button, CallbackDataCodec.encode("per", Integer.toString(i)))));
        }
        rows.add(List.of(presenter.button("BOTAO_INICIO", "inicio")));
        context.answer(new Message(context.chatId(), text.toString(), new Keyboard(rows)));
    }

    private Optional<UUID> student(TelegramContext context) {
        return context.conversation().data(STUDENT).map(UUID::fromString);
    }

    private Optional<StudentSummary> summary(TelegramContext context, UUID studentId) {
        return searchStudents.run(context.teacher().id(), "").students().stream()
                .filter(a -> a.id().equals(studentId))
                .findFirst();
    }

    private boolean choosePeriod(TelegramContext context, String key) {
        Optional<UUID> studentId = student(context);
        if (studentId.isEmpty()) {
            return false;
        }
        TelegramConversation conversation = context.conversation();
        conversation.store(PERIOD, key);
        conversation.defineStep(ConversationFlowStep.CONFIRMANDO_GERACAO_SEMESTRAL);
        Preview p = preview.run(context.teacher().id(), studentId.get(), key);
        String label = sections.periodLabel(key);
        long chat = context.chatId();
        Button back = presenter.button("BOTAO_VOLTAR", "voltar");
        if (p.approvedSemesterId().isPresent()) {
            context.answer(
                    new Message(chat, presenter.text("SEMESTRAL_JA_APROVADO"), Keyboard.of(back)));
        } else if (p.openSemester().isPresent()) {
            conversation.defineStep(ConversationFlowStep.AVISO_RELATORIO_EM_ABERTO);
            context.answer(presenter.openNotice(chat, p.openSemester().get()));
        } else if (p.approvedLessons() == 0) {
            context.answer(
                    new Message(
                            chat,
                            presenter.text("SEM_AULAS_APROVADAS_NO_PERIODO"),
                            Keyboard.of(back)));
        } else {
            String text =
                    presenter.text(
                            "SEMESTRAL_PREVIA",
                            Map.of("rotulo", label, "quantidade", p.approvedLessons()));
            if (p.openLessonReports() > 0) {
                text +=
                        "\n"
                                + presenter.text(
                                        "HA_RELATORIOS_EM_ABERTO",
                                        Map.of("quantidade", p.openLessonReports()));
                context.answer(
                        new Message(
                                chat,
                                text,
                                Keyboard.of(
                                        presenter.button("BOTAO_GERAR_MESMO_ASSIM", "gerarsim"),
                                        back)));
            } else {
                context.answer(
                        new Message(
                                chat,
                                text,
                                Keyboard.of(
                                        presenter.button("BOTAO_GERAR_SEMESTRAL", "gerar"), back)));
            }
        }
        return true;
    }

    private void generate(TelegramContext context, boolean evenWithOpenReport) {
        UUID studentId = student(context).orElseThrow();
        String key = context.conversation().data(PERIOD).orElseThrow();
        ProcessingJob job =
                generate.run(
                        context.teacher().id(),
                        studentId,
                        key,
                        evenWithOpenReport,
                        Channel.TELEGRAM);
        context.conversation().endFlow();
        context.answer(
                new StepMessage(
                        context.chatId(),
                        context.callbackMessageId(),
                        TelegramLessonReportFlow.stepText(presenter, job),
                        job.id()));
    }

    // ------------------------------------------------------------------ semestral em foco

    private Optional<SemesterReportView> focus(TelegramContext context) {
        TelegramConversation conversation = context.conversation();
        Optional<FocusReport> focus =
                conversation.focusReport().filter(f -> f.type() == ReportType.SEMESTRAL);
        if (focus.isEmpty()) {
            return Optional.empty();
        }
        try {
            SemesterReportView view = presentation.get(context.teacher().id(), focus.get().id());
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

    private int baseVersion(TelegramContext context, SemesterReport report) {
        Integer presented = context.conversation().presentedVersion();
        return presented == null ? report.currentVersion() : presented;
    }

    @Override
    public boolean onText(TelegramContext context, String text) {
        Optional<SemesterReportView> focus = focus(context);
        if (focus.isPresent()) {
            SemesterReportView view = focus.get();
            if (TelegramSemesterReportPresentation.inProcessing(view)) {
                context.answer(
                        new Message(
                                context.chatId(), presentation.processing(view), Keyboard.NONE));
                return true;
            }
            SemesterReport report = view.report();
            withCurrentVersion(
                    context,
                    report.id(),
                    () ->
                            step(
                                    context,
                                    change.changeByText(
                                            context.teacher().id(),
                                            report.id(),
                                            text,
                                            baseVersion(context, report),
                                            Channel.TELEGRAM)));
            return true;
        }
        TelegramConversation conversation = context.conversation();
        if (conversation.flow() == ConversationFlow.SEMESTRAL
                && conversation.step() == ConversationFlowStep.ESCOLHENDO_SEMESTRE) {
            String normalized = StudentSearch.normalize(text);
            for (String key : options(conversation)) {
                if (normalized.equals(key)
                        || normalized.equals(StudentSearch.normalize(sections.periodLabel(key)))) {
                    return choosePeriod(context, key);
                }
            }
        }
        return false;
    }

    @Override
    public boolean aoAudio(TelegramContext context, TelegramMessage message, TelegramFile file) {
        Optional<SemesterReportView> focus = focus(context);
        if (focus.isEmpty()) {
            return false;
        }
        SemesterReportView view = focus.get();
        if (TelegramSemesterReportPresentation.inProcessing(view)) {
            context.answer(
                    new Message(context.chatId(), presentation.processing(view), Keyboard.NONE));
            return true;
        }
        SemesterReport report = view.report();
        withCurrentVersion(
                context,
                report.id(),
                () ->
                        step(
                                context,
                                change.changeByAudio(
                                        context.teacher().id(),
                                        report.id(),
                                        TelegramLessonRegistrationFlow.reference(message, file),
                                        baseVersion(context, report),
                                        Channel.TELEGRAM)));
        return true;
    }

    private void withCurrentVersion(TelegramContext context, UUID reportId, Runnable action) {
        try {
            action.run();
        } catch (DomainException error) {
            if (error.code() != ErrorCode.VERSAO_DESATUALIZADA) {
                throw error;
            }
            context.answer(presenter.error(context.chatId(), error.code(), error.details()));
            present(context, reportId);
        }
    }

    private void step(TelegramContext context, ProcessingJob job) {
        context.answer(
                new StepMessage(
                        context.chatId(),
                        null,
                        TelegramLessonReportFlow.stepText(presenter, job),
                        job.id()));
    }

    private void present(TelegramContext context, UUID reportId) {
        presentation
                .present(
                        context.chatId(),
                        context.teacher().id(),
                        reportId,
                        context.conversation(),
                        null)
                .forEach(context::answer);
    }

    private List<String> options(TelegramConversation conversation) {
        return conversation.data(OPTIONS).map(s -> List.of(s.split(","))).orElse(List.of());
    }

    // ------------------------------------------------------------------ botões e comandos

    @Override
    public boolean aoCallback(TelegramContext context, String action, String argument) {
        TelegramConversation conversation = context.conversation();
        boolean inFlow = conversation.flow() == ConversationFlow.SEMESTRAL;
        switch (action) {
            case "per" -> {
                List<String> options = options(conversation);
                try {
                    int index = Integer.parseInt(argument);
                    if (!inFlow || index < 0 || index >= options.size()) {
                        return false;
                    }
                    conversation.cleanConfirmation();
                    return choosePeriod(context, options.get(index));
                } catch (NumberFormatException invalid) {
                    return false;
                }
            }
            case "gerar", "gerarsim" -> {
                if (!inFlow
                        || conversation.step()
                                != ConversationFlowStep.CONFIRMANDO_GERACAO_SEMESTRAL) {
                    return false;
                }
                generate(context, action.equals("gerarsim"));
                return true;
            }
            case "voltar" -> {
                Optional<StudentSummary> student =
                        student(context).flatMap(id -> summary(context, id));
                if (!inFlow || student.isEmpty()) {
                    return false;
                }
                offerPeriods(context, student.get());
                return true;
            }
            case "cont" -> {
                return inFlow
                        && conversation.step() == ConversationFlowStep.AVISO_RELATORIO_EM_ABERTO
                        && proceed(context);
            }
            case "desc" -> {
                if (!inFlow
                        || conversation.step() != ConversationFlowStep.AVISO_RELATORIO_EM_ABERTO) {
                    return false;
                }
                askDiscard(context);
                return true;
            }
            case "descok" -> {
                if (!inFlow
                        || !conversation
                                .pendingConfirmation()
                                .filter(TelegramResumptionFlow.DISCARD_CONFIRMATION::equals)
                                .isPresent()) {
                    return false;
                }
                conversation.cleanConfirmation();
                return discard(context);
            }
            case "foco" -> {
                return continueInChat(context, argument);
            }
            case "aprov", "aprovok", "alt", "canc", "cancok", "pdf" -> {
                Optional<SemesterReportView> focus = focus(context);
                return focus.isPresent() && actionInFocus(context, focus.get(), action, argument);
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean onCommand(TelegramContext context, Command command) {
        TelegramConversation conversation = context.conversation();
        if (conversation.flow() == ConversationFlow.SEMESTRAL
                && conversation.step() == ConversationFlowStep.AVISO_RELATORIO_EM_ABERTO) {
            if (command == Command.CONTINUE) {
                return proceed(context);
            }
            if (command == Command.DISCARD) {
                askDiscard(context);
                return true;
            }
        }
        if (command != Command.APPROVE && command != Command.CANCEL) {
            return false;
        }
        Optional<SemesterReportView> focus = focus(context);
        if (focus.isEmpty()) {
            return false;
        }
        if (command == Command.APPROVE) {
            askApproval(context, focus.get(), baseVersion(context, focus.get().report()));
        } else {
            askCancellation(context);
        }
        return true;
    }

    @Override
    public boolean onConfirm(TelegramContext context, String confirmation) {
        TelegramConversation conversation = context.conversation();
        if (TelegramResumptionFlow.DISCARD_CONFIRMATION.equals(confirmation)
                && conversation.flow() == ConversationFlow.SEMESTRAL) {
            return discard(context);
        }
        Optional<SemesterReportView> focus = focus(context);
        if (focus.isEmpty()) {
            return false;
        }
        UUID id = focus.get().report().id();
        if (TelegramLessonReportFlow.CANCEL_CONFIRMATION.equals(confirmation)) {
            cancelSemester(context, id);
            return true;
        }
        if (confirmation.startsWith(TelegramLessonReportFlow.APPROVE_CONFIRMATION)) {
            try {
                approveVersion(
                        context,
                        id,
                        Integer.parseInt(
                                confirmation.substring(
                                        TelegramLessonReportFlow.APPROVE_CONFIRMATION.length())));
                return true;
            } catch (NumberFormatException invalid) {
                return false;
            }
        }
        return false;
    }

    @Override
    public String whatICanReceive(TelegramContext context) {
        if (focus(context).isPresent()) {
            return presenter.label("RECEBER_ALTERACAO");
        }
        return null;
    }

    private boolean actionInFocus(
            TelegramContext context, SemesterReportView view, String action, String argument) {
        TelegramConversation conversation = context.conversation();
        SemesterReport report = view.report();
        switch (action) {
            case "aprov" -> {
                Integer version = number(argument);
                if (version == null) {
                    return false;
                }
                conversation.cleanConfirmation();
                askApproval(context, view, version);
            }
            case "aprovok" -> {
                Integer version = number(argument);
                if (version == null
                        || !conversation
                                .pendingConfirmation()
                                .filter(
                                        (TelegramLessonReportFlow.APPROVE_CONFIRMATION + version)
                                                ::equals)
                                .isPresent()) {
                    return false;
                }
                conversation.cleanConfirmation();
                approveVersion(context, report.id(), version);
            }
            case "cancok" -> {
                if (!conversation
                        .pendingConfirmation()
                        .filter(TelegramLessonReportFlow.CANCEL_CONFIRMATION::equals)
                        .isPresent()) {
                    return false;
                }
                conversation.cleanConfirmation();
                cancelSemester(context, report.id());
            }
            case "canc" -> {
                conversation.cleanConfirmation();
                askCancellation(context);
            }
            case "alt" ->
                    context.answer(presenter.simple(context.chatId(), "PEDIDO_ALTERACAO_ROTULO"));
            default ->
                    presentation
                            .reviewDocument(context.chatId(), context.teacher().id(), report)
                            .ifPresentOrElse(
                                    context::answer,
                                    () ->
                                            context.answer(
                                                    presenter.simple(
                                                            context.chatId(),
                                                            "CONFERENCIA_PDF_GERANDO")));
        }
        return true;
    }

    private void askApproval(TelegramContext context, SemesterReportView view, int version) {
        SemesterReport report = view.report();
        if (TelegramSemesterReportPresentation.inProcessing(view)) {
            throw new DomainException(ErrorCode.ACAO_NAO_PERMITIDA_NA_ETAPA);
        }
        if (version != report.currentVersion()) {
            context.answer(
                    presenter.error(
                            context.chatId(),
                            ErrorCode.VERSAO_DESATUALIZADA,
                            Map.of("versaoVigente", report.currentVersion())));
            present(context, report.id());
            return;
        }
        context.conversation()
                .askConfirmation(TelegramLessonReportFlow.APPROVE_CONFIRMATION + version);
        context.answer(
                presenter.confirmation(
                        context.chatId(),
                        presenter.text("CONFIRMAR_APROVACAO", Map.of("versao", version)),
                        presenter.button(
                                "BOTAO_CONFIRMAR_APROVACAO",
                                Map.of("versao", version),
                                "aprovok",
                                Integer.toString(version))));
    }

    private void approveVersion(TelegramContext context, UUID reportId, int version) {
        Approval approval;
        try {
            approval = approve.run(context.teacher().id(), reportId, version, Channel.TELEGRAM);
        } catch (DomainException error) {
            if (error.code() != ErrorCode.VERSAO_DESATUALIZADA) {
                throw error;
            }
            context.answer(presenter.error(context.chatId(), error.code(), error.details()));
            present(context, reportId);
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
                                "RELATORIO_APROVADO",
                                Map.of(
                                        "data", presenter.formatting().data(approval.approvedAt()),
                                        "hora",
                                                presenter
                                                        .formatting()
                                                        .hora(approval.approvedAt()))),
                        Keyboard.of(
                                presenter.button("BOTAO_HISTORICO", "hist"),
                                presenter.button("BOTAO_INICIO", "inicio"))));
    }

    private void askCancellation(TelegramContext context) {
        context.conversation().askConfirmation(TelegramLessonReportFlow.CANCEL_CONFIRMATION);
        context.answer(
                presenter.confirmation(
                        context.chatId(),
                        presenter.text("CONFIRMAR_CANCELAMENTO"),
                        presenter.button("BOTAO_CONFIRMAR_CANCELAMENTO", "cancok")));
    }

    private void cancelSemester(TelegramContext context, UUID reportId) {
        cancel.cancel(context.teacher().id(), reportId);
        TelegramConversation conversation = context.conversation();
        conversation.cleanFocus();
        conversation.cleanSelection();
        conversation.endFlow();
        context.answer(presenter.simple(context.chatId(), "RELATORIO_CANCELADO"));
        interpreter.showHome(context);
    }

    // ------------------------------------------------------------------ retomada do semestral

    private Optional<OpenReport> open(TelegramContext context) {
        Optional<UUID> studentId = student(context);
        Optional<String> key = context.conversation().data(PERIOD);
        if (studentId.isEmpty() || key.isEmpty()) {
            return Optional.empty();
        }
        return preview.run(context.teacher().id(), studentId.get(), key.get()).openSemester();
    }

    private boolean proceed(TelegramContext context) {
        Optional<OpenReport> openReport = open(context);
        context.conversation().endFlow();
        if (openReport.isEmpty()) {
            interpreter.showHome(context);
            return true;
        }
        if (openReport.get().reportId() != null) {
            present(context, openReport.get().reportId());
        } else {
            String step =
                    openReport.get().step() == null
                            ? "CONSOLIDANDO"
                            : openReport.get().step().name();
            context.answer(
                    presenter.simple(
                            context.chatId(),
                            "PROCESSAMENTO_EM_ANDAMENTO",
                            Map.of("etapa", presenter.formatting().step(step))));
        }
        return true;
    }

    private void askDiscard(TelegramContext context) {
        context.conversation().askConfirmation(TelegramResumptionFlow.DISCARD_CONFIRMATION);
        context.answer(
                presenter.confirmation(
                        context.chatId(),
                        presenter.text("CONFIRMAR_DESCARTE"),
                        presenter.button("BOTAO_CONFIRMAR_DESCARTE", "descok")));
    }

    private boolean discard(TelegramContext context) {
        Optional<OpenReport> openReport = open(context);
        UUID teacherId = context.teacher().id();
        openReport.ifPresent(
                a -> {
                    if (a.reportId() != null) {
                        cancel.cancel(teacherId, a.reportId());
                    } else if (a.jobId() != null) {
                        cancel.discardConsolidation(teacherId, a.jobId());
                    }
                });
        context.answer(presenter.simple(context.chatId(), "RELATORIO_CANCELADO"));
        Optional<String> key = context.conversation().data(PERIOD);
        return key.map(c -> choosePeriod(context, c)).orElse(true);
    }

    /** Botão "Continuar: {aluno}" de um semestral (argumento {@code s<UUID>}). */
    private boolean continueInChat(TelegramContext context, String argument) {
        if (argument == null || argument.length() < 2 || argument.charAt(0) != 's') {
            return false;
        }
        try {
            UUID id = UUID.fromString(argument.substring(1));
            context.conversation().cleanConfirmation();
            present(context, id);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static Integer number(String argument) {
        try {
            return argument == null ? null : Integer.valueOf(argument);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
