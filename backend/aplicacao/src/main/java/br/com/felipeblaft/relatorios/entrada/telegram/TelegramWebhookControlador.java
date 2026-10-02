package br.com.felipeblaft.relatorios.entrada.telegram;

import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.relatorios.entrada.interno.seguranca.ComparadorSegredo;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrada do canal Telegram (contracts/telegram-conversa.md §1): chamado pelo servidor local da API
 * de bots na rede interna. Valida o segredo em tempo constante, deixa o processamento (dedup e
 * interpretação) ao {@link ProcessadorUpdateTelegramServico} e responde sempre 200 depois de gravar
 * o estado; as mensagens do bot são enviadas depois de o estado estar gravado.
 */
@RestController
public class TelegramWebhookControlador {

    public static final String CABECALHO_SEGREDO = "X-Telegram-Bot-Api-Secret-Token";

    private final String segredo;
    private final ComparadorSegredo comparador = new ComparadorSegredo();
    private final ProcessadorUpdateTelegramServico processador;
    private final EnvioTelegram envio;

    public TelegramWebhookControlador(
            PropriedadesAplicacao propriedades,
            ProcessadorUpdateTelegramServico processador,
            EnvioTelegram envio) {
        this.segredo = propriedades.telegram().segredoWebhook();
        this.processador = processador;
        this.envio = envio;
    }

    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> receber(
            @RequestHeader(name = CABECALHO_SEGREDO, required = false) String segredoRecebido,
            @RequestBody(required = false) String corpo) {
        if (!comparador.igual(segredoRecebido, segredo)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<UpdateTelegram> update = processador.ler(corpo);
        if (update.isEmpty()) {
            return ResponseEntity.ok().build();
        }
        ResultadoUpdateTelegram resultado = processador.processarWebhook(update.get());
        for (SaidaTelegram saida : resultado.saidas()) {
            envio.enviar(saida);
        }
        return ResponseEntity.ok().build();
    }
}
