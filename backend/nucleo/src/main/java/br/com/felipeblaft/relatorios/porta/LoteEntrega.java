package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lote de ações entregue inteiro ao orquestrador. O {@code payload} é opaco para o núcleo; {@code
 * editarMensagemId} só vem preenchido nas mensagens de etapa cujo trabalho já tem a mensagem
 * conhecida.
 */
public record LoteEntrega(
        String loteId, OrigemAcaoCanal origem, Instant criadoEm, int tentativa, List<Acao> acoes) {

    public record Acao(
            String acaoId,
            TipoAcaoCanal tipo,
            String payload,
            UUID trabalhoId,
            Long editarMensagemId) {}
}
