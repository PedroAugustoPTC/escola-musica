package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Porta de entrada única do Telegram (R-02): lê o update, deduplica por {@code update_id}, chama o
 * <strong>mesmo</strong> interpretador de conversa e produz {@code ERRO_INTERNO} diante de falha
 * inesperada. É usada pelo webhook antigo (que envia as saídas direto à API de bots) e pela rota
 * interna do canal (que as devolve como ações v1). Não tem regra de conversa nem de transporte.
 */
@Component
public class ProcessadorUpdateTelegramServico {

    private static final Logger LOG =
            LoggerFactory.getLogger(ProcessadorUpdateTelegramServico.class);
    private static final JsonMapper JSON =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final RegistroUpdatesTelegram registroUpdates;
    private final InterpretadorConversaTelegram interpretador;
    private final ApresentadorTelegram apresentador;
    private final RelogioPorta relogio;

    public ProcessadorUpdateTelegramServico(
            RegistroUpdatesTelegram registroUpdates,
            InterpretadorConversaTelegram interpretador,
            ApresentadorTelegram apresentador,
            RelogioPorta relogio) {
        this.registroUpdates = registroUpdates;
        this.interpretador = interpretador;
        this.apresentador = apresentador;
        this.relogio = relogio;
    }

    /**
     * Lê o corpo como update. Corpo ausente, ilegível ou de tipo não suportado vira vazio; o
     * registro em log nunca inclui o conteúdo nem a mensagem da exceção de leitura.
     */
    public Optional<UpdateTelegram> ler(String corpo) {
        try {
            return Optional.of(JSON.readValue(corpo == null ? "{}" : corpo, UpdateTelegram.class));
        } catch (RuntimeException erro) {
            LOG.warn("telegram-update-invalido");
            return Optional.empty();
        }
    }

    /** Update do webhook antigo: registra só o {@code update_id}. */
    public ResultadoUpdateTelegram processarWebhook(UpdateTelegram update) {
        return processar(
                update, registroUpdates.registrarSeNovo(update.updateId(), relogio.agora()));
    }

    /** Update da rota interna: registra também a origem, para reentregar as ações depois. */
    public ResultadoUpdateTelegram processarInterno(UpdateTelegram update) {
        return processar(
                update,
                registroUpdates.registrarSeNovo(
                        update.updateId(),
                        relogio.agora(),
                        RegistroUpdatesTelegram.ORIGEM_INTERNO));
    }

    private ResultadoUpdateTelegram processar(UpdateTelegram update, boolean novo) {
        if (!novo) {
            LOG.info("telegram-update-repetido updateId={}", update.updateId());
            return ResultadoUpdateTelegram.repetido(update.updateId());
        }
        List<SaidaTelegram> saidas;
        try {
            saidas = interpretador.interpretar(update);
        } catch (RuntimeException erro) {
            LOG.error("telegram-erro-interpretacao updateId={}", update.updateId(), erro);
            saidas = respostaDeErro(update);
        }
        return ResultadoUpdateTelegram.novo(update.updateId(), saidas);
    }

    /** Toda mensagem recebe resposta (FR-039), mesmo diante de erro inesperado. */
    public List<SaidaTelegram> respostaDeErro(UpdateTelegram update) {
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
        SaidaTelegram mensagem = apresentador.simples(chatId, "ERRO_INTERNO");
        return callbackId == null
                ? List.of(mensagem)
                : List.of(new SaidaTelegram.RespostaCallback(callbackId), mensagem);
    }
}
