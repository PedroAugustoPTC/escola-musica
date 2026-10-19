package br.com.felipeblaft.reports.inbound.telegram;

import java.util.List;
import java.util.UUID;

/**
 * Saída das notificações proativas ao professor (etapas, resultado, demora e avisos entre canais),
 * isto é, o que não responde a um update. A única implementação grava a outbox, que é entregue ao
 * orquestrador (n8n; FR-019). A entrada define a porta; a saída a implementa.
 */
public interface TelegramActionPublicationPort {

    /**
     * @param eventoId identifica o evento de forma estável ({@code resultado:<trabalhoId>}, {@code
     *     etapa:<trabalhoId>:<ETAPA>}, {@code demora:<trabalhoId>}); o mesmo evento publicado duas
     *     vezes não gera ação nova (FR-014)
     * @param professorId dono das ações: se o vínculo for desfeito antes da entrega, elas são
     *     descartadas
     * @param saidas na ordem em que devem aparecer no chat
     */
    void publish(String eventId, UUID teacherId, List<TelegramOutput> outputs);
}
