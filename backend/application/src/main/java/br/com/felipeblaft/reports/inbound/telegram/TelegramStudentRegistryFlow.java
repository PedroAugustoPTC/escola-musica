package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.inbound.telegram.TelegramKeywords.Command;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Button;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Message;
import br.com.felipeblaft.reports.usecase.registry.CancelStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.registry.CollectStudentRegistrationFieldUseCase;
import br.com.felipeblaft.reports.usecase.registry.ConfirmStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.registry.GetStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.ListStudentsUseCase;
import br.com.felipeblaft.reports.usecase.registry.StartStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * "Meus alunos" no Telegram (contracts/telegram-student-registry.md): lista paginada, cartão,
 * cadastro guiado, edição, inativação e reativação. É só adaptador: valida nada, decide nada e não
 * grava nada; cada passo chama o caso de uso do núcleo (o mesmo da Web) e apresenta o resultado com
 * os textos do catálogo. Todo o estado está na conversa persistida e no cadastro em andamento do
 * núcleo; nada fica em memória. O CPF nunca é guardado, mostrado nem repetido aqui: a mensagem que
 * o traz é apagada antes da próxima pergunta.
 */
@Component
@Order(4)
public class TelegramStudentRegistryFlow implements TelegramFlow {

    static final int PER_PAGE = 6;

    static final String KIND = "regKind";
    static final String FILTER = "regFilter";
    static final String PAGE = "regPage";
    static final String SEARCH = "regSearch";
    static final String STUDENT = "regStudent";
    static final String TOKEN = "regToken";
    static final String EDITED = "regEdited";

    private static final Set<ConversationFlowStep> DRAFT_STEPS =
            EnumSet.of(
                    ConversationFlowStep.REGISTRY_ASK_NAME,
                    ConversationFlowStep.REGISTRY_ASK_BIRTH_DATE,
                    ConversationFlowStep.REGISTRY_ASK_CPF,
                    ConversationFlowStep.REGISTRY_ASK_GUARDIAN_NAME,
                    ConversationFlowStep.REGISTRY_ASK_GUARDIAN_PHONE,
                    ConversationFlowStep.REGISTRY_ASK_GUARDIAN_EMAIL,
                    ConversationFlowStep.REGISTRY_ASK_INSTRUMENT,
                    ConversationFlowStep.REGISTRY_EDIT_FIELD,
                    ConversationFlowStep.REGISTRY_CONFIRM);

    private static final Set<ErrorCode> RETRY_THE_QUESTION =
            EnumSet.of(
                    ErrorCode.VALIDATION_FAILED,
                    ErrorCode.CPF_UNAVAILABLE,
                    ErrorCode.STUDENT_ALREADY_YOURS,
                    ErrorCode.DUPLICATE_CPF);

    private final StartStudentRegistrationUseCase start;
    private final CollectStudentRegistrationFieldUseCase collect;
    private final ConfirmStudentRegistrationUseCase confirm;
    private final CancelStudentRegistrationUseCase cancel;
    private final ListStudentsUseCase list;
    private final GetStudentUseCase get;
    private final TelegramConversationInterpreter interpreter;
    private final TelegramPresenter presenter;

    @SuppressWarnings("java:S107")
    public TelegramStudentRegistryFlow(
            StartStudentRegistrationUseCase start,
            CollectStudentRegistrationFieldUseCase collect,
            ConfirmStudentRegistrationUseCase confirm,
            CancelStudentRegistrationUseCase cancel,
            ListStudentsUseCase list,
            GetStudentUseCase get,
            TelegramConversationInterpreter interpreter,
            TelegramPresenter presenter) {
        this.start = start;
        this.collect = collect;
        this.confirm = confirm;
        this.cancel = cancel;
        this.list = list;
        this.get = get;
        this.interpreter = interpreter;
        this.presenter = presenter;
    }

    // ------------------------------------------------------------------ entradas

    @Override
    public boolean onCommand(TelegramContext context, Command command) {
        switch (command) {
            case MY_STUDENTS -> {
                openList(context);
                return true;
            }
            case REGISTER_STUDENT -> {
                startRegistration(context);
                return true;
            }
            default -> {
                if (!active(context)) {
                    return false;
                }
                return activeCommand(context, command);
            }
        }
    }

    private boolean activeCommand(TelegramContext context, Command command) {
        ConversationFlowStep step = context.conversation().step();
        switch (command) {
            case SKIP -> {
                if (step != ConversationFlowStep.REGISTRY_ASK_INSTRUMENT) {
                    return false;
                }
                collectField(context, Field.INSTRUMENT, "");
                return true;
            }
            case BACK -> {
                return back(context);
            }
            case CANCEL -> {
                if (DRAFT_STEPS.contains(step)) {
                    cancelDraft(context);
                } else {
                    context.conversation().endFlow();
                    interpreter.showHome(context);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean onText(TelegramContext context, String text) {
        if (!active(context)) {
            return false;
        }
        ConversationFlowStep step = context.conversation().step();
        switch (step) {
            case REGISTRY_ASK_NAME -> collectField(context, Field.NAME, text);
            case REGISTRY_ASK_BIRTH_DATE -> collectField(context, Field.BIRTH_DATE, text);
            case REGISTRY_ASK_CPF -> {
                // a mensagem com o CPF sai do chat antes de qualquer resposta (FR-036)
                deleteIncomingMessage(context);
                collectField(context, Field.CPF, text);
            }
            case REGISTRY_ASK_GUARDIAN_PHONE -> guardianPhone(context, text);
            case REGISTRY_ASK_GUARDIAN_NAME, REGISTRY_ASK_GUARDIAN_EMAIL ->
                    collectField(context, Field.GUARDIAN, text);
            case REGISTRY_ASK_INSTRUMENT -> collectField(context, Field.INSTRUMENT, text);
            case REGISTRY_LIST -> search(context, text);
            case REGISTRY_CONFIRM -> confirmByText(context, text);
            default ->
                    context.answer(
                            presenter.error(
                                    context.chatId(),
                                    ErrorCode.ACTION_NOT_ALLOWED_AT_STEP,
                                    Map.of()));
        }
        return true;
    }

    /**
     * Um CPF válido digitado no lugar do telefone do responsável: a mensagem sai do chat, o valor
     * não chega ao núcleo nem é repetido, e a pergunta do telefone volta com o motivo (FR-036).
     */
    private void guardianPhone(TelegramContext context, String text) {
        if (!CpfShape.isValid(text)) {
            collectField(context, Field.GUARDIAN, text);
            return;
        }
        deleteIncomingMessage(context);
        ask(
                context,
                ConversationFlowStep.REGISTRY_ASK_GUARDIAN_PHONE,
                presenter.error(context.chatId(), ErrorCode.INVALID_PHONE, Map.of()).text());
    }

    private void deleteIncomingMessage(TelegramContext context) {
        if (context.incomingMessageId() != null) {
            context.answer(
                    new TelegramOutput.DeleteMessage(
                            context.chatId(),
                            context.incomingMessageId(),
                            presenter.simple(context.chatId(), "CPF_MESSAGE_NOT_DELETED")));
        }
    }

    @Override
    public boolean aoCallback(TelegramContext context, String action, String argument) {
        if (!action.startsWith("reg-")) {
            return false;
        }
        switch (action) {
            case "reg-list" -> openList(context);
            case "reg-new" -> startRegistration(context);
            // dono, token e prazo são conferidos pelo núcleo; aqui só a conversa e o tamanho do token
            case "reg-confirm" -> {
                if (active(context)
                        && DRAFT_STEPS.contains(context.conversation().step())
                        && CallbackDataCodec.fits("reg-confirm", argument)) {
                    confirmWith(context, argument);
                } else {
                    expired(context);
                }
            }
            case "reg-cancel" -> {
                if (active(context) && DRAFT_STEPS.contains(context.conversation().step())) {
                    cancelDraft(context);
                } else {
                    expired(context);
                }
            }
            default -> {
                if (!active(context) || !stateButton(context, action, argument)) {
                    expired(context);
                }
            }
        }
        return true;
    }

    /** Botões que só valem num estado específico; {@code false} se o estado não é o esperado. */
    private boolean stateButton(TelegramContext context, String action, String argument) {
        ConversationFlowStep step = context.conversation().step();
        switch (action) {
            case "reg-page" -> {
                Integer page = number(argument);
                if (step != ConversationFlowStep.REGISTRY_LIST || page == null || page < 0) {
                    return false;
                }
                context.conversation().store(PAGE, page.toString());
                showList(context);
                return true;
            }
            case "reg-filter" -> {
                StudentStatusFilter filter = filter(argument);
                if (step != ConversationFlowStep.REGISTRY_LIST || filter == null) {
                    return false;
                }
                context.conversation().store(FILTER, filter.name());
                context.conversation().store(PAGE, "0");
                showList(context);
                return true;
            }
            case "reg-open" -> {
                return step == ConversationFlowStep.REGISTRY_LIST && openCard(context, argument);
            }
            case "reg-edit" -> {
                if (step == ConversationFlowStep.REGISTRY_CARD && argument.isEmpty()) {
                    startEdit(context);
                    return true;
                }
                return step == ConversationFlowStep.REGISTRY_EDIT_FIELD
                        && chooseEditField(context, argument);
            }
            case "reg-inactivate" -> {
                return step == ConversationFlowStep.REGISTRY_CARD
                        && startStatusChange(context, Kind.INACTIVATE);
            }
            case "reg-reactivate" -> {
                return step == ConversationFlowStep.REGISTRY_CARD
                        && startStatusChange(context, Kind.REACTIVATE);
            }
            case "reg-skip" -> {
                if (step != ConversationFlowStep.REGISTRY_ASK_INSTRUMENT) {
                    return false;
                }
                collectField(context, Field.INSTRUMENT, "");
                return true;
            }
            case "reg-back" -> {
                return back(context);
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public void onLeave(TelegramContext context) {
        if (active(context) && DRAFT_STEPS.contains(context.conversation().step())) {
            discardQuietly(context);
        }
    }

    // ------------------------------------------------------------------ lista e cartão

    private void openList(TelegramContext context) {
        onLeave(context);
        TelegramConversation conversation = context.conversation();
        conversation.cleanFocus();
        conversation.discardAudio();
        conversation.startFlow(
                ConversationFlow.STUDENT_REGISTRY, ConversationFlowStep.REGISTRY_LIST);
        conversation.store(FILTER, StudentStatusFilter.ACTIVE.name());
        conversation.store(PAGE, "0");
        showList(context);
    }

    private void search(TelegramContext context, String text) {
        context.conversation().store(SEARCH, text);
        context.conversation().store(PAGE, "0");
        showList(context);
    }

    private void showList(TelegramContext context) {
        TelegramConversation conversation = context.conversation();
        StudentStatusFilter filter =
                conversation
                        .data(FILTER)
                        .map(StudentStatusFilter::valueOf)
                        .orElse(StudentStatusFilter.ACTIVE);
        int page = conversation.data(PAGE).map(Integer::parseInt).orElse(0);
        String search = conversation.data(SEARCH).orElse("");
        PageResult<StudentView> result =
                list.run(
                        actor(context),
                        new ListStudentsUseCase.Command(
                                search, filter, new PageRequest(page, PER_PAGE)));
        conversation.defineStep(ConversationFlowStep.REGISTRY_LIST);
        conversation.store(
                TelegramConversationInterpreter.STUDENT_OPTIONS,
                String.join(",", result.items().stream().map(s -> s.id().toString()).toList()));

        long chatId = context.chatId();
        if (result.items().isEmpty() && search.isBlank() && filter == StudentStatusFilter.ACTIVE) {
            context.answer(presenter.noStudents(chatId));
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (int i = 0; i < result.items().size(); i++) {
            rows.add(
                    List.of(
                            new Button(
                                    result.items().get(i).name(),
                                    CallbackDataCodec.encode("reg-open", Integer.toString(i)))));
        }
        long pages = Math.max(1, (result.total() + PER_PAGE - 1) / PER_PAGE);
        List<Button> navigation = new ArrayList<>();
        if (page > 0) {
            navigation.add(
                    presenter.button(
                            "PREVIOUS_PAGE_BUTTON",
                            Map.of(),
                            "reg-page",
                            Integer.toString(page - 1)));
        }
        if (page + 1 < pages) {
            navigation.add(
                    presenter.button(
                            "NEXT_PAGE_BUTTON", Map.of(), "reg-page", Integer.toString(page + 1)));
        }
        if (!navigation.isEmpty()) {
            rows.add(navigation);
        }
        rows.add(
                List.of(
                        presenter.button("FILTER_ACTIVE_BUTTON", Map.of(), "reg-filter", "ACTIVE"),
                        presenter.button(
                                "FILTER_INACTIVE_BUTTON", Map.of(), "reg-filter", "INACTIVE")));
        rows.add(List.of(presenter.button("HOME_BUTTON", "home")));
        String text =
                result.items().isEmpty()
                        ? presenter.text("REGISTRY_EMPTY_FILTER")
                        : presenter.text(
                                "REGISTRY_MY_STUDENTS",
                                Map.of(
                                        "status",
                                        presenter.label(
                                                filter == StudentStatusFilter.INACTIVE
                                                        ? "REGISTRY_FILTER_INACTIVE"
                                                        : "REGISTRY_FILTER_ACTIVE"),
                                        "page",
                                        page + 1,
                                        "pages",
                                        pages));
        context.answer(new Message(chatId, text, new Keyboard(rows)));
    }

    private boolean openCard(TelegramContext context, String argument) {
        TelegramConversation conversation = context.conversation();
        Optional<UUID> studentId = TelegramConversationInterpreter.option(conversation, argument);
        if (studentId.isEmpty()) {
            // posição adulterada ou de lista antiga: tratada como aluno inexistente (FR-025)
            context.answer(
                    presenter.error(context.chatId(), ErrorCode.RESOURCE_NOT_FOUND, Map.of()));
            return true;
        }
        StudentView student =
                get.run(
                        actor(context),
                        new GetStudentUseCase.Command(studentId.get(), correlation(context)));
        conversation.store(STUDENT, student.id().toString());
        conversation.defineStep(ConversationFlowStep.REGISTRY_CARD);
        String empty = presenter.label("EMPTY_SECTION");
        String text =
                presenter.text(
                        "REGISTRY_CARD",
                        Map.of(
                                "name",
                                student.name(),
                                "age",
                                student.age(),
                                "instrument",
                                student.instrument() == null ? empty : student.instrument(),
                                "status",
                                presenter.label(
                                        student.active()
                                                ? "STUDENT_STATUS_ACTIVE"
                                                : "STUDENT_STATUS_INACTIVE"),
                                "guardian",
                                student.guardian() == null ? empty : student.guardian().name()));
        context.answer(
                new Message(
                        context.chatId(),
                        text,
                        new Keyboard(
                                List.of(
                                        List.of(presenter.button("EDIT_BUTTON", "reg-edit")),
                                        List.of(
                                                student.active()
                                                        ? presenter.button(
                                                                "INACTIVATE_BUTTON",
                                                                "reg-inactivate")
                                                        : presenter.button(
                                                                "REACTIVATE_BUTTON",
                                                                "reg-reactivate")),
                                        List.of(presenter.button("BACK_BUTTON", "reg-back"))))));
        return true;
    }

    private boolean back(TelegramContext context) {
        ConversationFlowStep step = context.conversation().step();
        if (step == ConversationFlowStep.REGISTRY_CARD) {
            showList(context);
            return true;
        }
        if (step == ConversationFlowStep.REGISTRY_LIST) {
            context.conversation().endFlow();
            interpreter.showHome(context);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ cadastro e edição

    private void startRegistration(TelegramContext context) {
        TelegramConversation conversation = context.conversation();
        start.run(
                actor(context),
                new StartStudentRegistrationUseCase.Command(linkId(context), Kind.CREATE, null));
        conversation.cleanFocus();
        conversation.discardAudio();
        conversation.startFlow(
                ConversationFlow.STUDENT_REGISTRY, ConversationFlowStep.REGISTRY_ASK_NAME);
        conversation.store(KIND, Kind.CREATE.name());
        ask(context, ConversationFlowStep.REGISTRY_ASK_NAME, null);
    }

    private void startEdit(TelegramContext context) {
        TelegramConversation conversation = context.conversation();
        UUID studentId = UUID.fromString(conversation.data(STUDENT).orElseThrow());
        start.run(
                actor(context),
                new StartStudentRegistrationUseCase.Command(
                        linkId(context), Kind.UPDATE, studentId));
        conversation.store(KIND, Kind.UPDATE.name());
        conversation.store(EDITED, "");
        conversation.defineStep(ConversationFlowStep.REGISTRY_EDIT_FIELD);
        context.answer(
                new Message(
                        context.chatId(),
                        presenter.text("REGISTRY_EDIT_FIELD"),
                        new Keyboard(
                                List.of(
                                        List.of(
                                                editButton("REGISTRY_FIELD_NAME", "name"),
                                                editButton("REGISTRY_FIELD_BIRTH_DATE", "birth")),
                                        List.of(
                                                editButton("REGISTRY_FIELD_CPF", "cpf"),
                                                editButton("REGISTRY_FIELD_GUARDIAN", "guardian")),
                                        List.of(
                                                editButton(
                                                        "REGISTRY_FIELD_INSTRUMENT", "instrument")),
                                        List.of(
                                                presenter.button(
                                                        "CANCEL_BUTTON", "reg-cancel"))))));
    }

    private Button editButton(String label, String field) {
        return presenter.button(label, Map.of(), "reg-edit", field);
    }

    private boolean chooseEditField(TelegramContext context, String argument) {
        ConversationFlowStep step =
                switch (argument) {
                    case "name" -> ConversationFlowStep.REGISTRY_ASK_NAME;
                    case "birth" -> ConversationFlowStep.REGISTRY_ASK_BIRTH_DATE;
                    case "cpf" -> ConversationFlowStep.REGISTRY_ASK_CPF;
                    case "guardian" -> ConversationFlowStep.REGISTRY_ASK_GUARDIAN_NAME;
                    case "instrument" -> ConversationFlowStep.REGISTRY_ASK_INSTRUMENT;
                    default -> null;
                };
        if (step == null) {
            return false;
        }
        ask(context, step, null);
        return true;
    }

    private boolean startStatusChange(TelegramContext context, Kind kind) {
        TelegramConversation conversation = context.conversation();
        UUID studentId = UUID.fromString(conversation.data(STUDENT).orElseThrow());
        StartStudentRegistrationUseCase.Result started =
                start.run(
                        actor(context),
                        new StartStudentRegistrationUseCase.Command(
                                linkId(context), kind, studentId));
        StudentView student =
                get.run(
                        actor(context),
                        new GetStudentUseCase.Command(studentId, correlation(context)));
        conversation.store(KIND, kind.name());
        conversation.store(TOKEN, started.confirmationToken());
        conversation.defineStep(ConversationFlowStep.REGISTRY_CONFIRM);
        context.answer(
                new Message(
                        context.chatId(),
                        presenter.text(
                                kind == Kind.INACTIVATE
                                        ? "CONFIRM_INACTIVATE_STUDENT"
                                        : "CONFIRM_REACTIVATE_STUDENT",
                                Map.of("name", student.name())),
                        confirmKeyboard(started.confirmationToken())));
        return true;
    }

    /** Pergunta de uma etapa; {@code prefix} traz o motivo do erro anterior, se houver. */
    private void ask(TelegramContext context, ConversationFlowStep step, String prefix) {
        context.conversation().defineStep(step);
        String question = presenter.text(questionKey(step));
        String text = prefix == null ? question : prefix + "\n\n" + question;
        List<List<Button>> rows = new ArrayList<>();
        if (step == ConversationFlowStep.REGISTRY_ASK_INSTRUMENT) {
            rows.add(List.of(presenter.button("SKIP_BUTTON", "reg-skip")));
        }
        rows.add(List.of(presenter.button("CANCEL_BUTTON", "reg-cancel")));
        context.answer(new Message(context.chatId(), text, new Keyboard(rows)));
    }

    private static String questionKey(ConversationFlowStep step) {
        return switch (step) {
            case REGISTRY_ASK_NAME -> "REGISTRY_ASK_NAME";
            case REGISTRY_ASK_BIRTH_DATE -> "REGISTRY_ASK_BIRTH_DATE";
            case REGISTRY_ASK_CPF -> "REGISTRY_ASK_CPF";
            case REGISTRY_ASK_GUARDIAN_NAME -> "REGISTRY_ASK_GUARDIAN_NAME";
            case REGISTRY_ASK_GUARDIAN_PHONE -> "REGISTRY_ASK_GUARDIAN_PHONE";
            case REGISTRY_ASK_GUARDIAN_EMAIL -> "REGISTRY_ASK_GUARDIAN_EMAIL";
            case REGISTRY_ASK_INSTRUMENT -> "REGISTRY_ASK_INSTRUMENT";
            default -> throw new IllegalArgumentException("etapa sem pergunta: " + step);
        };
    }

    private void collectField(TelegramContext context, Field field, String text) {
        TelegramConversation conversation = context.conversation();
        ConversationFlowStep step = conversation.step();
        CollectStudentRegistrationFieldUseCase.Result result;
        try {
            result =
                    collect.run(
                            actor(context),
                            new CollectStudentRegistrationFieldUseCase.Command(
                                    linkId(context), field, text));
        } catch (DomainException error) {
            fieldFailed(context, step, error);
            return;
        }
        if (Kind.UPDATE.name().equals(conversation.data(KIND).orElse(null))) {
            markEdited(conversation, field);
        }
        if (step == ConversationFlowStep.REGISTRY_ASK_GUARDIAN_NAME) {
            ask(context, ConversationFlowStep.REGISTRY_ASK_GUARDIAN_PHONE, null);
        } else if (step == ConversationFlowStep.REGISTRY_ASK_GUARDIAN_PHONE) {
            ask(context, ConversationFlowStep.REGISTRY_ASK_GUARDIAN_EMAIL, null);
        } else if (result.nextField() != null) {
            if (result.nextField() == Field.GUARDIAN
                    && Kind.UPDATE.name().equals(conversation.data(KIND).orElse(null))) {
                markEdited(conversation, Field.GUARDIAN);
            }
            ask(context, stepOf(result.nextField()), null);
        } else {
            showSummary(context, result);
        }
    }

    private static ConversationFlowStep stepOf(Field field) {
        return switch (field) {
            case NAME -> ConversationFlowStep.REGISTRY_ASK_NAME;
            case BIRTH_DATE -> ConversationFlowStep.REGISTRY_ASK_BIRTH_DATE;
            case CPF -> ConversationFlowStep.REGISTRY_ASK_CPF;
            case GUARDIAN -> ConversationFlowStep.REGISTRY_ASK_GUARDIAN_NAME;
            case INSTRUMENT -> ConversationFlowStep.REGISTRY_ASK_INSTRUMENT;
        };
    }

    private void fieldFailed(
            TelegramContext context, ConversationFlowStep step, DomainException error) {
        ErrorCode code = error.code();
        if (code == ErrorCode.CONFIRMATION_EXPIRED) {
            context.conversation().endFlow();
            context.answer(
                    new Message(
                            context.chatId(),
                            presenter.text("REGISTRY_DRAFT_EXPIRED"),
                            presenter.homeActions()));
            return;
        }
        if (RETRY_THE_QUESTION.contains(code)) {
            // o motivo do campo e a mesma pergunta, sem repetir o que foi digitado
            ask(context, step, reasonOf(context, error));
            return;
        }
        if (code == ErrorCode.OPERATION_TEMPORARILY_BLOCKED) {
            context.answer(presenter.error(context.chatId(), code, error.details()));
            return;
        }
        context.conversation().endFlow();
        throw error;
    }

    /** Texto do primeiro campo recusado (catálogo), ou o genérico de validação. */
    private String reasonOf(TelegramContext context, DomainException error) {
        ErrorCode reason = error.code();
        if (error.details().get("fields") instanceof List<?> fields
                && !fields.isEmpty()
                && fields.getFirst() instanceof Map<?, ?> first
                && first.get("code") instanceof String code) {
            try {
                reason = ErrorCode.valueOf(code);
            } catch (IllegalArgumentException unknown) {
                reason = ErrorCode.VALIDATION_FAILED;
            }
        }
        return presenter.error(context.chatId(), reason, error.details()).text();
    }

    private static void markEdited(TelegramConversation conversation, Field field) {
        Set<String> edited = new LinkedHashSet<>();
        conversation
                .data(EDITED)
                .filter(s -> !s.isBlank())
                .ifPresent(s -> edited.addAll(List.of(s.split(","))));
        edited.add(field.name());
        conversation.store(EDITED, String.join(",", edited));
    }

    // ------------------------------------------------------------------ resumo e confirmação

    private void showSummary(
            TelegramContext context, CollectStudentRegistrationFieldUseCase.Result result) {
        TelegramConversation conversation = context.conversation();
        CollectStudentRegistrationFieldUseCase.Summary summary = result.summary();
        if (summary == null || result.confirmationToken() == null) {
            throw new IllegalStateException("cadastro completo sem resumo nem token");
        }
        boolean update = Kind.UPDATE.name().equals(conversation.data(KIND).orElse(null));
        Set<String> edited = new LinkedHashSet<>();
        conversation
                .data(EDITED)
                .filter(s -> !s.isBlank())
                .ifPresent(s -> edited.addAll(List.of(s.split(","))));
        List<String> lines = new ArrayList<>();
        addLine(lines, update, edited, Field.NAME, "REGISTRY_FIELD_NAME", summary.name());
        addLine(
                lines,
                update,
                edited,
                Field.BIRTH_DATE,
                "REGISTRY_FIELD_BIRTH_DATE",
                summary.birthDate() == null
                        ? null
                        : presenter.formatting().data(summary.birthDate()));
        if (update && edited.contains(Field.CPF.name())) {
            lines.add(presenter.label("REGISTRY_CPF_WILL_CHANGE"));
        }
        addLine(
                lines,
                update,
                edited,
                Field.INSTRUMENT,
                "REGISTRY_FIELD_INSTRUMENT",
                summary.instrument());
        addLine(
                lines,
                update,
                edited,
                Field.GUARDIAN,
                "REGISTRY_FIELD_GUARDIAN",
                summary.guardianName());
        String joined = String.join("\n", lines);

        conversation.store(TOKEN, result.confirmationToken());
        conversation.defineStep(ConversationFlowStep.REGISTRY_CONFIRM);
        String text;
        if (update) {
            UUID studentId = UUID.fromString(conversation.data(STUDENT).orElseThrow());
            StudentView student =
                    get.run(
                            actor(context),
                            new GetStudentUseCase.Command(studentId, correlation(context)));
            text =
                    presenter.text(
                            "REGISTRY_CONFIRM_UPDATE",
                            Map.of("name", student.name(), "summary", joined));
        } else {
            text = presenter.text("REGISTRY_CONFIRM_CREATE", Map.of("summary", joined));
        }
        context.answer(
                new Message(context.chatId(), text, confirmKeyboard(result.confirmationToken())));
    }

    private void addLine(
            List<String> lines,
            boolean update,
            Set<String> edited,
            Field field,
            String labelKey,
            String value) {
        if (value == null || value.isBlank() || (update && !edited.contains(field.name()))) {
            return;
        }
        lines.add(presenter.label(labelKey) + ": " + value);
    }

    /**
     * Botões do resumo. O token vai em `callback_data` (64 bytes no máximo); se não couber, só o
     * "Cancelar" é oferecido e a confirmação segue por "sim", com o token guardado na conversa.
     */
    private Keyboard confirmKeyboard(String token) {
        List<List<Button>> rows = new ArrayList<>();
        if (CallbackDataCodec.fits("reg-confirm", token)) {
            rows.add(List.of(presenter.button("CONFIRM_BUTTON", Map.of(), "reg-confirm", token)));
        }
        rows.add(List.of(presenter.button("CANCEL_BUTTON", "reg-cancel")));
        return new Keyboard(rows);
    }

    private void confirmByText(TelegramContext context, String text) {
        Optional<Command> command = TelegramKeywords.identify(text);
        Optional<String> token = context.conversation().data(TOKEN);
        if (command.orElse(null) == Command.YES && token.isPresent()) {
            confirmWith(context, token.get());
            return;
        }
        context.answer(presenter.simple(context.chatId(), "RECEIVE_CONFIRMATION"));
    }

    private void confirmWith(TelegramContext context, String token) {
        TelegramConversation conversation = context.conversation();
        Optional<Kind> kind = conversation.data(KIND).flatMap(TelegramStudentRegistryFlow::kindOf);
        if (kind.isEmpty()) {
            expired(context);
            return;
        }
        StudentView student;
        try {
            student =
                    confirm.run(
                            actor(context),
                            new ConfirmStudentRegistrationUseCase.Command(
                                    linkId(context), token, correlation(context)));
        } catch (DomainException error) {
            if (error.code() == ErrorCode.CONFIRMATION_ALREADY_DONE) {
                // segundo toque ou resumo anterior: nada é gravado e a conversa segue como está
                context.answer(presenter.error(context.chatId(), error.code(), error.details()));
                return;
            }
            // qualquer outra recusa encerra o cadastro: descarta o rascunho e nada fica gravado
            discardQuietly(context);
            conversation.endFlow();
            throw error;
        }
        conversation.endFlow();
        String key =
                switch (kind.get()) {
                    case CREATE -> "REGISTRY_SAVED";
                    case UPDATE -> "REGISTRY_UPDATED";
                    case INACTIVATE -> "STUDENT_INACTIVATED";
                    case REACTIVATE -> "STUDENT_REACTIVATED";
                };
        context.answer(
                new Message(
                        context.chatId(),
                        presenter.text(key, Map.of("name", student.name())),
                        presenter.homeActions()));
    }

    private void cancelDraft(TelegramContext context) {
        cancel.run(actor(context), new CancelStudentRegistrationUseCase.Command(linkId(context)));
        context.conversation().endFlow();
        context.answer(
                new Message(
                        context.chatId(),
                        presenter.text("REGISTRY_CANCELLED"),
                        presenter.homeActions()));
    }

    /**
     * Descarte sem aviso (saída do fluxo, recusa na confirmação): falha do núcleo não interrompe.
     */
    private void discardQuietly(TelegramContext context) {
        try {
            cancel.run(
                    actor(context), new CancelStudentRegistrationUseCase.Command(linkId(context)));
        } catch (DomainException ignored) {
            // o rascunho vence sozinho em 30 minutos e some com o vínculo
        }
    }

    // ------------------------------------------------------------------ apoio

    private boolean active(TelegramContext context) {
        return context.conversation().flow() == ConversationFlow.STUDENT_REGISTRY;
    }

    private void expired(TelegramContext context) {
        context.answer(presenter.error(context.chatId(), ErrorCode.CALLBACK_EXPIRED, Map.of()));
    }

    private static Actor actor(TelegramContext context) {
        return Actor.teacher(context.teacher().id(), ActionOrigin.TELEGRAM);
    }

    private static UUID linkId(TelegramContext context) {
        return context.link().id();
    }

    private static String correlation(TelegramContext context) {
        return "telegram-" + context.updateId();
    }

    private static Optional<Kind> kindOf(String name) {
        try {
            return Optional.of(Kind.valueOf(name));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    private static Integer number(String argument) {
        try {
            return argument == null ? null : Integer.valueOf(argument);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static StudentStatusFilter filter(String argument) {
        try {
            StudentStatusFilter filter = StudentStatusFilter.valueOf(argument);
            return filter == StudentStatusFilter.ALL ? null : filter;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
