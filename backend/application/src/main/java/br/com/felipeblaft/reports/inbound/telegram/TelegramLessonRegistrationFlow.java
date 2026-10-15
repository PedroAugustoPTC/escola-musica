package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.domain.common.BusinessParameters;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlow;
import br.com.felipeblaft.reports.domain.telegram.ConversationFlowStep;
import br.com.felipeblaft.reports.domain.telegram.PendingAudio;
import br.com.felipeblaft.reports.domain.telegram.TelegramConversation;
import br.com.felipeblaft.reports.inbound.telegram.TelegramKeywords.Command;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Button;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Message;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.StepMessage;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.TelegramFile;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.TelegramMessage;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.usecase.home.SearchStudentsUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import br.com.felipeblaft.reports.usecase.lesson.GetOpenReportUseCase;
import br.com.felipeblaft.reports.usecase.lesson.RegisterLessonByAudioUseCase;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Registro de aula pelo Telegram (estados #7 e #10, sequência 5.2, passos 1 a 4): o áudio fica
 * pendente só como {@code file_id} até o professor confirmar o aluno (FR-040); confirmado, vira
 * {@code RegistrarAulaPorAudioCasoDeUso}, o mesmo da Web.
 */
@Component
@Order(20)
public class TelegramLessonRegistrationFlow implements TelegramFlow {

    static final String STUDENT_WITH_OPEN_REPORT = "studentWithOpenReport";

    private final RegisterLessonByAudioUseCase register;
    private final SearchStudentsUseCase searchStudents;
    private final TelegramConversationInterpreter interpreter;
    private final TelegramPresenter presenter;
    private final BusinessParameters parameters;
    private final ClockPort clock;
    private final GetOpenReportUseCase getOpen;

    public TelegramLessonRegistrationFlow(
            RegisterLessonByAudioUseCase register,
            SearchStudentsUseCase searchStudents,
            TelegramConversationInterpreter interpreter,
            TelegramPresenter presenter,
            BusinessParameters parameters,
            ClockPort clock,
            GetOpenReportUseCase getOpen) {
        this.getOpen = getOpen;
        this.register = register;
        this.searchStudents = searchStudents;
        this.interpreter = interpreter;
        this.presenter = presenter;
        this.parameters = parameters;
        this.clock = clock;
    }

    /** Referência ao arquivo do Telegram; a voz chega sem MIME declarado e é Ogg/Opus. */
    static ChannelFileReference reference(TelegramMessage message, TelegramFile file) {
        String mime = file.mimeType();
        if (mime == null) {
            mime =
                    file == message.voice()
                            ? "audio/ogg"
                            : file == message.videoNote() ? "video/mp4" : "audio/ogg";
        }
        return new ChannelFileReference(
                file.fileId(),
                file.fileUniqueId(),
                mime,
                file.fileSize() == null ? 0 : file.fileSize());
    }

    // ------------------------------------------------------------------ áudio recebido

    @Override
    public boolean aoAudio(TelegramContext context, TelegramMessage message, TelegramFile file) {
        TelegramConversation conversation = context.conversation();
        ChannelFileReference reference = reference(message, file);
        if (reference.size() > parameters.audioLimitBytes()) {
            context.answer(
                    presenter.error(
                            context.chatId(),
                            ErrorCode.AUDIO_TOO_LARGE,
                            Map.of("limitMb", parameters.audioLimitMb())));
            return true;
        }
        conversation.cleanFocus();
        conversation.receiveAudio(
                new PendingAudio(
                        reference.id(),
                        reference.uniqueId(),
                        reference.mime(),
                        reference.size(),
                        clock.now()));
        Optional<StudentSummary> selected = selectedStudent(context);
        if (selected.isPresent()) {
            conversation.startFlow(
                    ConversationFlow.LESSON_REGISTRATION,
                    ConversationFlowStep.CONFIRMING_AUDIO_STUDENT);
            askStudentConfirmation(context, selected.get());
        } else {
            conversation.startFlow(
                    ConversationFlow.LESSON_REGISTRATION, ConversationFlowStep.CHOOSING_STUDENT);
            SearchStudentsUseCase.Result all = searchStudents.run(context.teacher().id(), "");
            if (all.students().isEmpty()) {
                conversation.discardAudio();
                context.answer(presenter.simple(context.chatId(), "NO_STUDENTS"));
                return true;
            }
            interpreter.offerStudents(
                    context, presenter.text("RECEIVED_AUDIO_CHOOSE_STUDENT"), all.students());
        }
        return true;
    }

    private Optional<StudentSummary> selectedStudent(TelegramContext context) {
        UUID studentId = context.conversation().selectedStudentId();
        if (studentId == null) {
            return Optional.empty();
        }
        return searchStudents.run(context.teacher().id(), "").students().stream()
                .filter(a -> a.id().equals(studentId))
                .findFirst();
    }

    private void askStudentConfirmation(TelegramContext context, StudentSummary student) {
        context.answer(
                new Message(
                        context.chatId(),
                        presenter.text(
                                "RECEIVED_AUDIO_CONFIRM_STUDENT",
                                Map.of("student", student.name())),
                        Keyboard.of(
                                new Button(
                                        presenter.label(
                                                "CONFIRM_STUDENT_BUTTON",
                                                Map.of("student", student.name())),
                                        CallbackDataCodec.encode("confirm-student")),
                                presenter.button("SWITCH_STUDENT_BUTTON", "switch-student"))));
    }

    // ------------------------------------------------------------------ confirmação do aluno

    @Override
    public boolean onChooseStudent(TelegramContext context, StudentSummary student) {
        TelegramConversation conversation = context.conversation();
        if (conversation.flow() != ConversationFlow.LESSON_REGISTRATION
                && conversation.flow() != ConversationFlow.NONE) {
            return false;
        }
        if (conversation.pendingAudio().isEmpty()) {
            // aluno com relatório em aberto: avisa antes de pedir o áudio (FR-034)
            Optional<OpenReport> openReport = getOpen.run(context.teacher().id(), student.id());
            if (openReport.isEmpty()) {
                return false;
            }
            notifyOpen(context, student.id(), openReport.get());
            return true;
        }
        // com áudio pendente, escolher o aluno na lista (ou pelo nome) é a confirmação (FR-040)
        registerPendingAudio(context, student, null);
        return true;
    }

    @Override
    public boolean aoCallback(TelegramContext context, String action, String argument) {
        TelegramConversation conversation = context.conversation();
        switch (action) {
            case "confirm-student" -> {
                Optional<StudentSummary> student = selectedStudent(context);
                if (conversation.pendingAudio().isEmpty() || student.isEmpty()) {
                    return false;
                }
                conversation.cleanConfirmation();
                registerPendingAudio(context, student.get(), context.callbackMessageId());
                return true;
            }
            case "switch-student" -> {
                if (conversation.pendingAudio().isEmpty()) {
                    return false;
                }
                conversation.cleanConfirmation();
                interpreter.offerStudents(
                        context,
                        presenter.text("RECEIVED_AUDIO_CHOOSE_STUDENT"),
                        searchStudents.run(context.teacher().id(), "").students());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** "cancelar" sem relatório em foco descarta o áudio pendente. */
    @Override
    public boolean onCommand(TelegramContext context, Command command) {
        TelegramConversation conversation = context.conversation();
        if (command != Command.CANCEL || conversation.pendingAudio().isEmpty()) {
            return false;
        }
        conversation.discardAudio();
        conversation.endFlow();
        context.answer(presenter.simple(context.chatId(), "PENDING_AUDIO_DISCARDED"));
        return true;
    }

    @Override
    public String whatICanReceive(TelegramContext context) {
        return context.conversation().pendingAudio().isPresent()
                ? presenter.label("RECEIVE_STUDENT")
                : null;
    }

    /** Guarda o aluno do aviso na conversa e mostra "Você tem um relatório em aberto". */
    void notifyOpen(TelegramContext context, UUID studentId, OpenReport openReport) {
        TelegramConversation conversation = context.conversation();
        conversation.cleanFocus();
        conversation.startFlow(
                ConversationFlow.LESSON_REGISTRATION, ConversationFlowStep.OPEN_REPORT_NOTICE);
        conversation.store(STUDENT_WITH_OPEN_REPORT, studentId.toString());
        context.answer(presenter.openNotice(context.chatId(), openReport));
    }

    void registerPendingAudio(TelegramContext context, StudentSummary student, Long editMessageId) {
        TelegramConversation conversation = context.conversation();
        PendingAudio audio = conversation.pendingAudio().orElseThrow();
        conversation.selectStudent(student.id());
        ProcessingJob job;
        try {
            job =
                    register.run(
                            context.teacher().id(),
                            student.id(),
                            new ChannelFileReference(
                                    audio.fileId(),
                                    audio.fileUniqueId(),
                                    audio.mime(),
                                    audio.size()),
                            Channel.TELEGRAM);
        } catch (DomainException error) {
            if (error.code() != ErrorCode.REPORT_ALREADY_OPEN) {
                conversation.discardAudio();
                throw error;
            }
            // FR-034: avisa e oferece continuar ou descartar; o áudio só é reaproveitado no
            // descarte
            Optional<OpenReport> openReport = getOpen.run(context.teacher().id(), student.id());
            if (openReport.isEmpty()) {
                throw error;
            }
            notifyOpen(context, student.id(), openReport.get());
            return;
        }
        conversation.discardAudio();
        conversation.startFlow(ConversationFlow.LESSON_REGISTRATION, ConversationFlowStep.NONE);
        context.answer(
                new StepMessage(
                        context.chatId(),
                        editMessageId,
                        TelegramLessonReportFlow.stepText(presenter, job),
                        job.id()));
    }
}
