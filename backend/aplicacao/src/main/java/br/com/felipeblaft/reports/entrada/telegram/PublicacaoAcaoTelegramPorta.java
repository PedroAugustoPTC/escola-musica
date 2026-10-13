package br.com.felipeblaft.reports.entrada.telegram;

import java.util.List;
import java.util.UUID;

/**
 * Saída das notificações proativas ao professor (etapas, resultado, demora e avisos entre canais),
 * isto é, o que não responde a um update. Exatamente uma implementação fica ativa por configuração
 * (R-10, FR-019): o envio direto à API de bots (padrão) ou a outbox entregue ao orquestrador. A
 * entrada define a porta; a saída a implementa.
 */
public interface PublicacaoAcaoTelegramPorta {

    /**
     * @param eventoId identifica o evento de forma estável ({@code resultado:<trabalhoId>}, {@code
     *     etapa:<trabalhoId>:<ETAPA>}, {@code demora:<trabalhoId>}); o mesmo evento publicado duas
     *     vezes não gera ação nova (FR-014)
     * @param professorId dono das ações: se o vínculo for desfeito antes da entrega, elas são
     *     descartadas
     * @param saidas na ordem em que devem aparecer no chat
     */
    void publicar(String eventoId, UUID professorId, List<SaidaTelegram> saidas);
}
