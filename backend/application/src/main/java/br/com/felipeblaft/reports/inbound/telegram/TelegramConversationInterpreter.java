package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.StudentSearch.ResultType;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.inbound.telegram.CallbackDataCodec.CallbackData;
import br.com.felipeblaft.reports.inbound.telegram.TelegramKeywords.Command;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.CallbackTelegram;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.TelegramFile;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.TelegramMessage;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.usecase.access.EndTelegramLinkUseCase;
import br.com.felipeblaft.reports.usecase.access.IdentifyTelegramTeacherUseCase;
import br.com.felipeblaft.reports.usecase.access.LinkTelegramUseCase;
import br.com.felipeblaft.reports.usecase.access.UpdateTelegramConversationUseCase;
import br.com.felipeblaft.reports.usecase.home.GetTeacherHomeUseCase;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Interpreta cada update a partir do estado persistido (FR-036) e traduz para os mesmos casos de
 * uso da Web (Princípio I). A ordem de decisão segue a tabela da seção 4 de
 * contracts/telegram-conversa.md: não vinculado (#1), confirmação pendente (#2), palavras-chave,
 * estados com relatório em foco ou áudio pendente (fluxos), e por fim a busca de aluno (#10).
 */
@Component
public class TelegramConversationInterpreter {

    public static final String STUDENT_OPTIONS = "options";
    public static final String SIGN_OUT_CONFIRMATION = "sign-out";

    private final IdentifyTelegramTeacherUseCase identify;
    private final LinkTelegramUseCase linkUseCase;
    private final EndTelegramLinkUseCase endLink;
    private final UpdateTelegramConversationUseCase updateConversation;
    private final GetTeacherHomeUseCase getHome;
    private final SearchStudentsUseCase searchStudents;
    private final TelegramPresenter presenter;
    private final ClockPort clock;
    private final BusinessParameters parameters;
    private final ObjectProvider<TelegramFlow> flowsProvider;
    private volatile List<TelegramFlow> flows;

    public TelegramConversationInterpreter(
            IdentifyTelegramTeacherUseCase identify,
            LinkTelegramUseCase linkUseCase,
            EndTelegramLinkUseCase endLink,
            UpdateTelegramConversationUseCase updateConversation,
            GetTeacherHomeUseCase getHome,
            SearchStudentsUseCase searchStudents,
            TelegramPresenter presenter,
            ClockPort clock,
            BusinessParameters parameters,
            ObjectProvider<TelegramFlow> flowsProvider) {
        this.identify = identify;
        this.linkUseCase = linkUseCase;
        this.endLink = endLink;
        this.updateConversation = updateConversation;
        this.getHome = getHome;
        this.searchStudents = searchStudents;
        this.presenter = presenter;
        this.clock = clock;
        this.parameters = parameters;
        this.flowsProvider = flowsProvider;
    }

    /**
     * Fluxos resolvidos sob demanda: eles também usam este interpretador (sem ciclo na criação).
     */
    private List<TelegramFlow> flows() {
        List<TelegramFlow> current = flows;
        if (current == null) {
            current = flowsProvider.orderedStream().toList();
            flows = current;
        }
        return current;
    }

    public List<TelegramOutput> interpret(UpdateTelegram update) {
        if (update.callbackQuery() != null) {
            return callback(update.callbackQuery(), update.updateId());
        }
        if (update.message() != null && update.message().from() != null) {
            return message(update.message(), update.updateId());
        }
        return List.of();
    }

    // ------------------------------------------------------------------ mensagens

    private List<TelegramOutput> message(TelegramMessage message, long updateId) {
        TelegramContext context =
                new TelegramContext(message.from().id(), message.chat().id(), null)
                        .withIncomingMessage(message.messageId())
                        .withUpdate(updateId);
        if (!message.chat().privateChat()) {
            context.answer(presenter.simple(context.chatId(), "PRIVATE_CONVERSATION_ONLY"));
            return context.outputs();
        }
        load(context);
        try {
            if (!context.linked()) {
                unlinked(context, message);
            } else {
                linked(context, message);
            }
        } catch (DomainException error) {
            answerError(context, error);
        } finally {
            save(context);
        }
        return context.outputs();
    }

    /**
     * Erro do núcleo vira o texto do catálogo. Acesso encerrado (ou professor inativo, que para o
     * professor é a mesma coisa) mostra só a mensagem de acesso encerrado e não grava mais a
     * conversa: o vínculo acaba de ser (ou está para ser) desfeito pelo núcleo.
     */
    void answerError(TelegramContext context, DomainException error) {
        ErrorCode code = error.code();
        if (code == ErrorCode.ACCESS_ENDED || code == ErrorCode.TEACHER_INACTIVE) {
            context.answer(presenter.error(context.chatId(), ErrorCode.ACCESS_ENDED, Map.of()));
            context.markEndedLink();
            return;
        }
        context.answer(presenter.error(context.chatId(), code, error.details()));
    }

    private void load(TelegramContext context) {
        identify.run(context.userId()).ifPresent(context::identify);
        if (context.linked() && context.link().chatId() != context.chatId()) {
            context.link().updateChat(context.chatId(), clock.now());
        }
    }

    private void save(TelegramContext context) {
        if (context.linked()) {
            updateConversation.run(context.link());
        }
    }

    /** Estado #1: não vinculado. Texto com cara de código é tentativa de vinculação (FR-004). */
    private void unlinked(TelegramContext context, TelegramMessage message) {
        String text = message.text();
        boolean command = text != null && TelegramKeywords.identify(text).isPresent();
        if (text == null || command || !AccessCode.looksLikeCode(text)) {
            context.answer(presenter.simple(context.chatId(), "CODE_REQUIRED"));
            return;
        }
        LinkTelegramUseCase.Result result =
                linkUseCase.run(context.userId(), context.chatId(), text);
        identify.run(context.userId()).ifPresent(context::identify);
        String prefix =
                presenter.text("LINK_CREATED", Map.of("name", result.teacher().firstName()));
        context.answer(presenter.start(context.chatId(), getHome.run(result.teacher()), prefix));
    }

    private void linked(TelegramContext context, TelegramMessage message) {
        TelegramConversation conversation = context.conversation();
        if (conversation.discardAudioIfExpired(clock.now(), parameters.pendingAudioExpiry())) {
            context.answer(presenter.simple(context.chatId(), "PENDING_AUDIO_EXPIRED"));
        }
        TelegramFile file = message.audioFile();
        if (message.text() != null) {
            text(context, message.text().strip());
        } else if (file != null) {
            audio(context, message, file);
        } else {
            unsupportedType(context);
        }
    }

    private void text(TelegramContext context, String text) {
        TelegramConversation conversation = context.conversation();
        if (CpfShape.matches(text) && !acceptsCpfShapedText(conversation)) {
            rejectCpfShapedText(context);
            return;
        }
        Optional<Command> command = TelegramKeywords.identify(text);
        Optional<String> confirmation = conversation.pendingConfirmation();
        if (confirmation.isPresent()) {
            conversation.cleanConfirmation();
            if (command.orElse(null) == Command.YES) {
                confirm(context, confirmation.get());
                return;
            }
            // qualquer outro texto desiste da confirmação e é reinterpretado (estado #2)
        }
        if (command.isPresent() && command.get() != Command.YES) {
            command(context, command.get());
            return;
        }
        for (TelegramFlow flow : flows()) {
            if (flow.onText(context, text)) {
                return;
            }
        }
        searchStudent(context, text);
    }

    /**
     * Só o CPF do cadastro e o telefone do responsável (11 dígitos) aceitam texto com essa forma.
     */
    private static boolean acceptsCpfShapedText(TelegramConversation conversation) {
        return conversation.flow() == ConversationFlow.STUDENT_REGISTRY
                && (conversation.step() == ConversationFlowStep.REGISTRY_ASK_CPF
                        || conversation.step() == ConversationFlowStep.REGISTRY_ASK_GUARDIAN_PHONE);
    }

    /**
     * Texto com forma de CPF fora da pergunta do CPF nunca vira busca nem é guardado: a mensagem do
     * professor é apagada antes de qualquer resposta e a resposta não o repete (FR-036).
     */
    private void rejectCpfShapedText(TelegramContext context) {
        if (context.incomingMessageId() != null) {
            context.answer(
                    new TelegramOutput.DeleteMessage(
                            context.chatId(), context.incomingMessageId()));
        }
        context.answer(presenter.error(context.chatId(), ErrorCode.VALIDATION_FAILED, Map.of()));
    }

    /** Avisa os fluxos de que o professor está saindo deles (descarte do que estiver em curso). */
    public void leaveFlows(TelegramContext context) {
        for (TelegramFlow flow : flows()) {
            flow.onLeave(context);
        }
    }

    private void audio(TelegramContext context, TelegramMessage message, TelegramFile file) {
        context.conversation().cleanConfirmation();
        for (TelegramFlow flow : flows()) {
            if (flow.aoAudio(context, message, file)) {
                return;
            }
        }
        unsupportedType(context);
    }

    public void unsupportedType(TelegramContext context) {
        String whatICanReceive = null;
        if (!context.linked()) {
            whatICanReceive = presenter.label("RECEIVE_CODE");
        } else if (context.conversation().pendingConfirmation().isPresent()) {
            whatICanReceive = presenter.label("RECEIVE_CONFIRMATION");
        } else {
            for (TelegramFlow flow : flows()) {
                whatICanReceive = flow.whatICanReceive(context);
                if (whatICanReceive != null) {
                    break;
                }
            }
            if (whatICanReceive == null) {
                whatICanReceive =
                        context.conversation().step() == ConversationFlowStep.CHOOSING_STUDENT
                                ? presenter.label("RECEIVE_STUDENT")
                                : presenter.label("RECEIVE_START");
            }
        }
        context.answer(
                presenter.simple(
                        context.chatId(),
                        "UNSUPPORTED_TYPE",
                        Map.of("whatICanReceive", whatICanReceive)));
    }

    // ------------------------------------------------------------------ comandos

    private void command(TelegramContext context, Command command) {
        TelegramConversation conversation = context.conversation();
        switch (command) {
            case HOME -> {
                leaveFlows(context);
                conversation.endFlow();
                conversation.cleanFocus();
                conversation.discardAudio();
                showHome(context);
            }
            case REGISTER_LESSON ->
                    startStudentChoice(context, ConversationFlow.LESSON_REGISTRATION);
            case SEMESTER -> startStudentChoice(context, ConversationFlow.SEMESTER);
            case HISTORY -> startStudentChoice(context, ConversationFlow.HISTORY);
            case SIGN_OUT -> {
                conversation.askConfirmation(SIGN_OUT_CONFIRMATION);
                context.answer(
                        presenter.confirmation(
                                context.chatId(),
                                presenter.text("CONFIRM_SIGN_OUT"),
                                presenter.button("CONFIRM_SIGN_OUT_BUTTON", "sign-out-ok")));
            }
            case HELP ->
                    context.answer(
                            new TelegramOutput.Message(
                                    context.chatId(),
                                    presenter.text("HELP"),
                                    presenter.homeActions()));
            default -> {
                for (TelegramFlow flow : flows()) {
                    if (flow.onCommand(context, command)) {
                        return;
                    }
                }
                context.answer(
                        presenter.error(
                                context.chatId(), ErrorCode.ACTION_NOT_ALLOWED_AT_STEP, Map.of()));
            }
        }
    }

    private void confirm(TelegramContext context, String confirmation) {
        if (SIGN_OUT_CONFIRMATION.equals(confirmation)) {
            leaveFlows(context);
            endLink.run(context.userId());
            context.markEndedLink();
            context.answer(presenter.simple(context.chatId(), "LINK_ENDED"));
            return;
        }
        for (TelegramFlow flow : flows()) {
            if (flow.onConfirm(context, confirmation)) {
                return;
            }
        }
        context.answer(presenter.simple(context.chatId(), "EXPIRED_ACTION"));
        showHome(context);
    }

    // ------------------------------------------------------------------ início e alunos

    public void showHome(TelegramContext context) {
        context.answer(presenter.start(context.chatId(), getHome.run(context.teacher()), null));
    }

    /**
     * Começa um fluxo pela escolha do aluno ("Para qual aluno?"). Limpa o foco, sem cancelar nada.
     */
    public void startStudentChoice(TelegramContext context, ConversationFlow flow) {
        TelegramConversation conversation = context.conversation();
        leaveFlows(context);
        conversation.cleanFocus();
        conversation.discardAudio();
        conversation.startFlow(flow, ConversationFlowStep.CHOOSING_STUDENT);
        SearchStudentsUseCase.Result all = searchStudents.run(context.teacher().id(), "");
        if (all.students().isEmpty()) {
            context.answer(presenter.noStudents(context.chatId()));
            return;
        }
        offerStudents(context, presenter.text("CHOOSE_STUDENT"), all.students());
    }

    /** Lista de alunos como botões; as opções ficam na conversa para validar o toque. */
    public void offerStudents(
            TelegramContext context, String header, List<StudentSummary> students) {
        TelegramConversation conversation = context.conversation();
        conversation.defineStep(ConversationFlowStep.CHOOSING_STUDENT);
        conversation.store(
                STUDENT_OPTIONS,
                String.join(",", students.stream().map(a -> a.id().toString()).toList()));
        context.answer(presenter.studentList(context.chatId(), header, students));
    }

    /** Estado #10 (e #7 a #9): o texto é o nome, ou parte do nome, de um aluno (FR-013). */
    private void searchStudent(TelegramContext context, String text) {
        SearchStudentsUseCase.Result result = searchStudents.run(context.teacher().id(), text);
        if (result.students().isEmpty()) {
            context.answer(presenter.noStudents(context.chatId()));
            return;
        }
        if (result.type() == ResultType.FOUND && result.students().size() == 1) {
            chooseStudent(context, result.students().getFirst());
        } else if (result.type() == ResultType.FOUND) {
            offerStudents(context, presenter.text("SEVERAL_STUDENTS_FOUND"), result.students());
        } else if (result.type() == ResultType.NONE) {
            // texto não reconhecido: a lista de alunos seguida do menu do professor
            TelegramConversation conversation = context.conversation();
            conversation.defineStep(ConversationFlowStep.CHOOSING_STUDENT);
            conversation.store(
                    STUDENT_OPTIONS,
                    String.join(
                            ",", result.students().stream().map(a -> a.id().toString()).toList()));
            context.answer(
                    presenter.studentListWithMenu(
                            context.chatId(),
                            presenter.text("NO_STUDENT_FOUND", Map.of("search", text)),
                            result.students()));
        } else {
            offerStudents(context, presenter.text("CHOOSE_STUDENT"), result.students());
        }
    }

    public void chooseStudent(TelegramContext context, StudentSummary student) {
        for (TelegramFlow flow : flows()) {
            if (flow.onChooseStudent(context, student)) {
                return;
            }
        }
        selectForRegistration(context, student);
    }

    /** Aluno selecionado para registrar aula: pede o áudio (sequência 5.2, passo 2). */
    public void selectForRegistration(TelegramContext context, StudentSummary student) {
        TelegramConversation conversation = context.conversation();
        conversation.cleanFocus();
        conversation.selectStudent(student.id());
        conversation.startFlow(
                ConversationFlow.LESSON_REGISTRATION, ConversationFlowStep.AWAITING_AUDIO);
        context.answer(
                presenter.simple(
                        context.chatId(),
                        "SELECTED_STUDENT",
                        Map.of(
                                "data",
                                presenter.formatting().data(clock.todayInSchoolTimezone()),
                                "student",
                                student.name(),
                                "limitMb",
                                parameters.audioLimitMb())));
    }

    // ------------------------------------------------------------------ botões

    private List<TelegramOutput> callback(CallbackTelegram callback, long updateId) {
        long chatId =
                callback.message() != null && callback.message().chat() != null
                        ? callback.message().chat().id()
                        : callback.from().id();
        Long messageId = callback.message() != null ? callback.message().messageId() : null;
        TelegramContext context =
                new TelegramContext(callback.from().id(), chatId, messageId).withUpdate(updateId);
        context.answer(new TelegramOutput.CallbackReply(callback.id()));
        if (callback.message() != null
                && callback.message().chat() != null
                && !callback.message().chat().privateChat()) {
            // botões também só valem em conversa privada: nada é executado nem gravado
            context.answer(presenter.simple(chatId, "PRIVATE_CONVERSATION_ONLY"));
            return context.outputs();
        }
        load(context);
        try {
            if (!context.linked()) {
                context.answer(presenter.simple(chatId, "CODE_REQUIRED"));
                return context.outputs();
            }
            Optional<CallbackData> data = CallbackDataCodec.decode(callback.data());
            if (data.isEmpty() || !handleCallback(context, data.get())) {
                expiredAction(context);
            }
        } catch (DomainException error) {
            answerError(context, error);
        } finally {
            save(context);
        }
        return context.outputs();
    }

    public void expiredAction(TelegramContext context) {
        context.answer(presenter.simple(context.chatId(), "EXPIRED_ACTION"));
        showHome(context);
    }

    private boolean handleCallback(TelegramContext context, CallbackData data) {
        TelegramConversation conversation = context.conversation();
        switch (data.action()) {
            case "home" -> {
                conversation.cleanConfirmation();
                command(context, Command.HOME);
                return true;
            }
            case "register" -> {
                conversation.cleanConfirmation();
                startStudentChoice(context, ConversationFlow.LESSON_REGISTRATION);
                return true;
            }
            case "semester" -> {
                conversation.cleanConfirmation();
                startStudentChoice(context, ConversationFlow.SEMESTER);
                return true;
            }
            case "history" -> {
                conversation.cleanConfirmation();
                startStudentChoice(context, ConversationFlow.HISTORY);
                return true;
            }
            case "sign-out-ok" -> {
                if (!conversation
                        .pendingConfirmation()
                        .filter(SIGN_OUT_CONFIRMATION::equals)
                        .isPresent()) {
                    return false;
                }
                conversation.cleanConfirmation();
                confirm(context, SIGN_OUT_CONFIRMATION);
                return true;
            }
            case "student" -> {
                return studentChosenFromList(context, data.argument());
            }
            default -> {
                for (TelegramFlow flow : flows()) {
                    if (flow.aoCallback(context, data.action(), data.argument())) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    private boolean studentChosenFromList(TelegramContext context, String argument) {
        TelegramConversation conversation = context.conversation();
        if (conversation.step() != ConversationFlowStep.CHOOSING_STUDENT) {
            return false;
        }
        Optional<UUID> studentId = option(conversation, argument);
        if (studentId.isEmpty()) {
            return false;
        }
        conversation.cleanConfirmation();
        SearchStudentsUseCase.Result all = searchStudents.run(context.teacher().id(), "");
        Optional<StudentSummary> student =
                all.students().stream().filter(a -> a.id().equals(studentId.get())).findFirst();
        if (student.isEmpty()) {
            return false;
        }
        chooseStudent(context, student.get());
        return true;
    }

    static Optional<UUID> option(TelegramConversation conversation, String argument) {
        try {
            int index = Integer.parseInt(argument);
            List<String> options =
                    conversation
                            .data(STUDENT_OPTIONS)
                            .map(s -> List.of(s.split(",")))
                            .orElse(List.of());
            if (index < 0 || index >= options.size() || options.get(index).isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(options.get(index)));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }
}
