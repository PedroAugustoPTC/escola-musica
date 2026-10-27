package br.com.felipeblaft.reports.inbound.telegram;

import br.com.felipeblaft.reports.config.log.ErrorReference;
import br.com.felipeblaft.reports.port.ClockPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Porta de entrada única do Telegram (R-02): lê o update, deduplica por {@code update_id}, chama o
 * <strong>mesmo</strong> interpretador de conversa e produz {@code ERRO_INTERNO} diante de falha
 * inesperada. É usada pela rota interna do canal, que devolve as saídas como ações v1 ao n8n. Não
 * tem regra de conversa nem de transporte.
 */
@Component
public class TelegramUpdateProcessorService {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramUpdateProcessorService.class);
    private static final JsonMapper JSON =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final TelegramUpdateRecords updateRecords;
    private final TelegramConversationInterpreter interpreter;
    private final TelegramPresenter presenter;
    private final ClockPort clock;

    public TelegramUpdateProcessorService(
            TelegramUpdateRecords updateRecords,
            TelegramConversationInterpreter interpreter,
            TelegramPresenter presenter,
            ClockPort clock) {
        this.updateRecords = updateRecords;
        this.interpreter = interpreter;
        this.presenter = presenter;
        this.clock = clock;
    }

    /**
     * Lê o corpo como update. Corpo ausente, ilegível ou de tipo não suportado vira vazio; o
     * registro em log nunca inclui o conteúdo nem a mensagem da exceção de leitura.
     */
    public Optional<UpdateTelegram> read(String body) {
        try {
            return Optional.of(JSON.readValue(body == null ? "{}" : body, UpdateTelegram.class));
        } catch (RuntimeException error) {
            LOG.warn("telegram-update-invalid");
            return Optional.empty();
        }
    }

    /** Registra o update com a origem, para reentregar as ações depois. */
    public TelegramUpdateResult processInternal(UpdateTelegram update) {
        return process(
                update,
                updateRecords.registerIfNew(
                        update.updateId(), clock.now(), TelegramUpdateRecords.INTERNAL_ORIGIN));
    }

    private TelegramUpdateResult process(UpdateTelegram update, boolean created) {
        if (!created) {
            LOG.info("telegram-update-repeated updateId={}", update.updateId());
            return TelegramUpdateResult.repeated(update.updateId());
        }
        List<TelegramOutput> outputs;
        try {
            outputs = interpreter.interpret(update);
        } catch (RuntimeException error) {
            // só o tipo: a mensagem e a causa podem trazer dado de aluno (FR-029)
            String reference = ErrorReference.newReference();
            LOG.error(
                    "telegram-interpretation-error updateId={} reference={} type={}",
                    update.updateId(),
                    reference,
                    error.getClass().getSimpleName());
            outputs = errorReply(update, reference);
        }
        return TelegramUpdateResult.create(update.updateId(), outputs);
    }

    /** Toda mensagem recebe resposta (FR-039), mesmo diante de erro inesperado. */
    public List<TelegramOutput> errorReply(UpdateTelegram update) {
        return errorReply(update, ErrorReference.newReference());
    }

    private List<TelegramOutput> errorReply(UpdateTelegram update, String reference) {
        Long chatId = null;
        String callbackId = null;
        if (update.message() != null && update.message().chat() != null) {
            chatId = update.message().chat().id();
        } else if (update.callbackQuery() != null) {
            callbackId = update.callbackQuery().id();
            chatId =
                    update.callbackQuery().message() != null
                            ? update.callbackQuery().message().chat().id()
                            : update.callbackQuery().from().id();
        }
        if (chatId == null) {
            return List.of();
        }
        TelegramOutput message =
                presenter.simple(chatId, "INTERNAL_ERROR", Map.of("reference", reference));
        if (callbackId != null) {
            return List.of(new TelegramOutput.CallbackReply(callbackId), message);
        }
        List<TelegramOutput> replies = new ArrayList<>();
        if (carriesCpf(update)) {
            // a falha não pode deixar o CPF no chat: a exclusão vem antes do aviso (FR-036)
            replies.add(
                    new TelegramOutput.DeleteMessage(
                            chatId,
                            update.message().messageId(),
                            presenter.simple(chatId, "CPF_MESSAGE_NOT_DELETED")));
        }
        replies.add(message);
        return List.copyOf(replies);
    }

    /** Texto com forma de CPF, ou texto enviado enquanto o bot esperava o CPF do cadastro. */
    private boolean carriesCpf(UpdateTelegram update) {
        UpdateTelegram.TelegramMessage message = update.message();
        if (message == null || message.text() == null) {
            return false;
        }
        return CpfShape.matches(message.text())
                || (message.from() != null && interpreter.awaitingCpf(message.from().id()));
    }
}
