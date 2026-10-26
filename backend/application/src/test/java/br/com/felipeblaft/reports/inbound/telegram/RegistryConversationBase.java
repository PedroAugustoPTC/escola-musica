package br.com.felipeblaft.reports.inbound.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Keyboard;
import br.com.felipeblaft.reports.inbound.telegram.TelegramOutput.Message;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.CallbackTelegram;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.ChatTelegram;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.TelegramMessage;
import br.com.felipeblaft.reports.inbound.telegram.UpdateTelegram.TelegramUser;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.registry.CancelStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.registry.CollectStudentRegistrationFieldUseCase;
import br.com.felipeblaft.reports.usecase.registry.ConfirmStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.registry.GetStudentUseCase;
import br.com.felipeblaft.reports.usecase.registry.ListStudentsUseCase;
import br.com.felipeblaft.reports.usecase.registry.StartStudentRegistrationUseCase;
import br.com.felipeblaft.reports.usecase.registry.StudentView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base dos testes de conversa de "Meus alunos" (feature 005, contracts/telegram-student-registry.md).
 * Usa Postgres real para o vínculo e o estado persistido da conversa e <strong>dublês</strong> dos
 * casos de uso do núcleo da fatia S2 (cadastro em andamento, listagem e abertura), que entram no
 * Fecho. O interpretador é chamado direto: as saídas ({@link TelegramOutput}) são verificadas sem
 * passar pelo mapeador de ações do canal, que é do fluxo W-L. Nenhum dado real: nomes e CPF são
 * fictícios.
 */
public abstract class RegistryConversationBase extends IntegrationBase {

    protected static final long USER = 7101L;
    protected static final long OTHER_USER = 7102L;
    protected static final String CPF = "529.982.247-25";
    protected static final String CPF_DIGITS = "52998224725";
    protected static final String TOKEN = "AB12CD34";

    private static final AtomicLong UPDATES = new AtomicLong(5000);
    private static final AtomicLong MESSAGES = new AtomicLong(100);

    @MockitoBean protected StartStudentRegistrationUseCase start;
    @MockitoBean protected CollectStudentRegistrationFieldUseCase collect;
    @MockitoBean protected ConfirmStudentRegistrationUseCase confirm;
    @MockitoBean protected CancelStudentRegistrationUseCase cancel;
    @MockitoBean protected ListStudentsUseCase list;
    @MockitoBean protected GetStudentUseCase get;

    @Autowired protected TelegramConversationInterpreter interpreter;
    @Autowired protected ScenarioIT scenario;

    protected Teacher clara;

    /** Saídas do último update enviado. */
    protected List<TelegramOutput> out = List.of();

    /** Id da mensagem do professor no último texto enviado. */
    protected long lastIncomingMessageId;

    @BeforeEach
    void linkTeacher() {
        clara = scenario.clara();
        send(USER, ScenarioIT.CODE_CLARA);
    }

    // ---------------------------------------------------------------- envio

    protected List<TelegramOutput> send(String text) {
        return send(USER, text);
    }

    protected List<TelegramOutput> send(long user, String text) {
        return sendIn(user, "private", text);
    }

    protected List<TelegramOutput> sendIn(long user, String chatType, String text) {
        lastIncomingMessageId = MESSAGES.incrementAndGet();
        UpdateTelegram update =
                new UpdateTelegram(
                        UPDATES.incrementAndGet(),
                        new TelegramMessage(
                                lastIncomingMessageId,
                                new TelegramUser(user, false),
                                new ChatTelegram(user, chatType),
                                text,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null),
                        null);
        out = interpreter.interpret(update);
        return out;
    }

    protected List<TelegramOutput> tap(String data) {
        return tap(USER, "private", data);
    }

    protected List<TelegramOutput> tap(long user, String chatType, String data) {
        UpdateTelegram update =
                new UpdateTelegram(
                        UPDATES.incrementAndGet(),
                        null,
                        new CallbackTelegram(
                                "cb" + UPDATES.get(),
                                new TelegramUser(user, false),
                                new TelegramMessage(
                                        55,
                                        null,
                                        new ChatTelegram(user, chatType),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                data));
        out = interpreter.interpret(update);
        return out;
    }

    // ---------------------------------------------------------------- leitura das saídas

    protected List<Message> messages() {
        return out.stream()
                .filter(Message.class::isInstance)
                .map(Message.class::cast)
                .toList();
    }

    protected Message lastMessage() {
        List<Message> messages = messages();
        assertThat(messages).as("mensagens enviadas").isNotEmpty();
        return messages.getLast();
    }

    protected String lastText() {
        return lastMessage().text();
    }

    protected static List<String> buttonData(Message message) {
        Keyboard keyboard = message.keyboard();
        List<String> data = new ArrayList<>();
        keyboard.rows().forEach(row -> row.forEach(button -> data.add(button.data())));
        return data;
    }

    protected static List<String> buttonLabels(Message message) {
        List<String> labels = new ArrayList<>();
        message.keyboard().rows().forEach(row -> row.forEach(button -> labels.add(button.label())));
        return labels;
    }

    /** Todo o texto e todo botão das saídas atuais, para provar que um valor não aparece. */
    protected String everythingSent() {
        StringBuilder all = new StringBuilder();
        for (TelegramOutput output : out) {
            all.append(output).append('\n');
        }
        return all.toString();
    }

    // ---------------------------------------------------------------- estado persistido

    protected String persistedFlow() {
        return jdbc.queryForObject("select flow from telegram_link", String.class);
    }

    protected String persistedStep() {
        return jdbc.queryForObject("select flow_step from telegram_link", String.class);
    }

    protected String persistedData() {
        return jdbc.queryForObject("select flow_data::text from telegram_link", String.class);
    }

    protected int links() {
        return jdbc.queryForObject("select count(*) from telegram_link", Integer.class);
    }

    protected UUID linkId() {
        return jdbc.queryForObject("select id from telegram_link", UUID.class);
    }

    // ---------------------------------------------------------------- fabricação de dublês

    protected static StudentView view(
            String name, int age, String instrument, boolean active, String guardianName) {
        return new StudentView(
                UUID.randomUUID(),
                name,
                LocalDate.of(2012, 3, 5),
                age,
                instrument,
                active,
                guardianName == null
                        ? null
                        : new StudentView.GuardianView(guardianName, "11999990000", "g@example.test"),
                List.of(),
                active ? Boolean.TRUE : null);
    }

    protected static PageResult<StudentView> page(
            int page, long total, StudentView... students) {
        return new PageResult<>(List.of(students), page, 6, total);
    }

    protected static CollectStudentRegistrationFieldUseCase.Result next(
            br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field field) {
        return new CollectStudentRegistrationFieldUseCase.Result(field, false, null, null);
    }

    protected static CollectStudentRegistrationFieldUseCase.Result ready(
            String name, String instrument, String guardianName) {
        return new CollectStudentRegistrationFieldUseCase.Result(
                null,
                guardianName != null,
                new CollectStudentRegistrationFieldUseCase.Summary(
                        name, LocalDate.of(2012, 3, 5), instrument, guardianName),
                TOKEN);
    }

    protected static StartStudentRegistrationUseCase.Result started(
            br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind kind,
            String token) {
        return new StartStudentRegistrationUseCase.Result(
                kind, Instant.parse("2026-09-21T13:00:00Z"), token);
    }

    protected static br.com.felipeblaft.reports.domain.common.DomainException error(
            ErrorCode code) {
        return new br.com.felipeblaft.reports.domain.common.DomainException(code);
    }

    protected static br.com.felipeblaft.reports.domain.common.DomainException invalid(
            String field, String code) {
        return new br.com.felipeblaft.reports.domain.common.DomainException(
                ErrorCode.VALIDATION_FAILED,
                Map.of("fields", List.of(Map.of("field", field, "code", code))));
    }
}
