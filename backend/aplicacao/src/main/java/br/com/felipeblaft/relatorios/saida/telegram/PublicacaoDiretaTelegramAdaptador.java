package br.com.felipeblaft.relatorios.saida.telegram;

import br.com.felipeblaft.relatorios.entrada.telegram.PublicacaoAcaoTelegramPorta;
import br.com.felipeblaft.relatorios.entrada.telegram.SaidaTelegram;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Caminho atual (padrão): as notificações proativas vão direto à API de bots, na ordem. É o
 * comportamento de antes da feature 002; nada entra na outbox.
 */
@Component
@ConditionalOnProperty(
        prefix = "relatorios.canal-telegram",
        name = "entrega-proativa",
        havingValue = "DIRETA",
        matchIfMissing = true)
public class PublicacaoDiretaTelegramAdaptador implements PublicacaoAcaoTelegramPorta {

    private final MensageriaTelegramAdaptador mensageria;

    public PublicacaoDiretaTelegramAdaptador(MensageriaTelegramAdaptador mensageria) {
        this.mensageria = mensageria;
    }

    @Override
    public void publicar(String eventoId, UUID professorId, List<SaidaTelegram> saidas) {
        for (SaidaTelegram saida : saidas) {
            // uma etapa proativa sem mensagem conhecida não abre mensagem nova: só edita a
            // existente
            if (saida instanceof SaidaTelegram.MensagemEtapa etapa
                    && etapa.editarMensagemId() == null) {
                continue;
            }
            mensageria.enviar(saida);
        }
    }
}
