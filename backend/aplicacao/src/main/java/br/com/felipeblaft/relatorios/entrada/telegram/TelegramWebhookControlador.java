package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entrada do canal Telegram (contracts/telegram-conversa.md §1): chamado pelo servidor local da API
 * de bots na rede interna. Valida o segredo em tempo constante, deduplica por {@code update_id},
 * interpreta o update e responde sempre 200 depois de gravar o estado; as mensagens do bot são
 * enviadas depois de o estado estar gravado.
 */
@RestController
public class TelegramWebhookControlador {

    public static final String CABECALHO_SEGREDO = "X-Telegram-Bot-Api-Secret-Token";

    private static final Logger LOG = LoggerFactory.getLogger(TelegramWebhookControlador.class);
    private static final JsonMapper JSON =
            JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final byte[] segredo;
    private final RegistroUpdatesTelegram registroUpdates;
    private final InterpretadorConversaTelegram interpretador;
    private final EnvioTelegram envio;
    private final ApresentadorTelegram apresentador;
    private final RelogioPorta relogio;

    public TelegramWebhookControlador(
            PropriedadesAplicacao propriedades,
            RegistroUpdatesTelegram registroUpdates,
            InterpretadorConversaTelegram interpretador,
            EnvioTelegram envio,
            ApresentadorTelegram apresentador,
            RelogioPorta relogio) {
        String configurado = propriedades.telegram().segredoWebhook();
        this.segredo =
                configurado == null || configurado.isBlank()
                        ? null
                        : configurado.getBytes(StandardCharsets.UTF_8);
        this.registroUpdates = registroUpdates;
        this.interpretador = interpretador;
        this.envio = envio;
        this.apresentador = apresentador;
        this.relogio = relogio;
    }

    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> receber(
            @RequestHeader(name = CABECALHO_SEGREDO, required = false) String segredoRecebido,
            @RequestBody(required = false) String corpo) {
        if (!segredoValido(segredoRecebido)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UpdateTelegram update;
        try {
            update = JSON.readValue(corpo == null ? "{}" : corpo, UpdateTelegram.class);
        } catch (RuntimeException erro) {
            LOG.warn("telegram-update-invalido");
            return ResponseEntity.ok().build();
        }
        if (!registroUpdates.registrarSeNovo(update.updateId(), relogio.agora())) {
            LOG.info("telegram-update-repetido updateId={}", update.updateId());
            return ResponseEntity.ok().build();
        }
        List<SaidaTelegram> saidas;
        try {
            saidas = interpretador.interpretar(update);
        } catch (RuntimeException erro) {
            LOG.error("telegram-erro-interpretacao updateId={}", update.updateId(), erro);
            saidas = respostaDeErro(update);
        }
        for (SaidaTelegram saida : saidas) {
            envio.enviar(saida);
        }
        return ResponseEntity.ok().build();
    }

    private boolean segredoValido(String recebido) {
        if (segredo == null || recebido == null) {
            return false;
        }
        return MessageDigest.isEqual(segredo, recebido.getBytes(StandardCharsets.UTF_8));
    }

    /** Toda mensagem recebe resposta (FR-039), mesmo diante de erro inesperado. */
    private List<SaidaTelegram> respostaDeErro(UpdateTelegram update) {
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
