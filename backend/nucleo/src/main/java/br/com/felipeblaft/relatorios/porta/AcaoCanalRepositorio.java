package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Outbox de ações de canal (data-model.md §1.2). */
public interface AcaoCanalRepositorio {

    /** Pendências para as métricas: quantidade e criação da ação pendente mais antiga. */
    record Pendencias(long quantidade, Instant maisAntigaCriadaEm) {}

    /** Resultado do expurgo: conteúdos zerados e registros removidos. */
    record Expurgo(int payloadsZerados, int registrosRemovidos) {}

    /**
     * Grava as ações que ainda não existem (idempotente por {@code id}, nunca sobrescreve); devolve
     * quantas foram de fato gravadas.
     */
    int gravarLote(List<AcaoCanal> acoes);

    Optional<AcaoCanal> buscarPorId(String id);

    /** Ações do update, na ordem de criação ({@code seq}). */
    List<AcaoCanal> buscarPorUpdate(long updateId);

    /** Ações do lote, na ordem de criação ({@code seq}). */
    List<AcaoCanal> buscarPorLote(String loteId);

    /**
     * Reserva o próximo lote devido, sem disputar com outros despachantes: a reserva empurra a próxima
     * tentativa para {@code leaseAte}, de modo que um despachante que morre deixa o lote reentregável
     * quando o lease vence. Respeita a ordem por chat: ignora o chat que ainda tem ação anterior de
     * outro lote {@code PENDENTE} ou {@code ENTREGUE} há menos de {@code confirmacaoMax}. Devolve o
     * lote inteiro, na ordem de criação, ou lista vazia.
     */
    List<AcaoCanal> reservarProximoLoteDevido(Instant agora, Duration confirmacaoMax, Instant leaseAte);

    /**
     * Grava a ação só se a versão armazenada ainda for {@code versaoEsperada} (controle otimista);
     * devolve {@code false} se outro ator gravou antes. Em caso de sucesso a versão da ação avança.
     */
    boolean atualizarSeVersao(AcaoCanal acao, int versaoEsperada);

    /**
     * Zera o conteúdo das ações finais anteriores a {@code payloadAte} e remove os registros finais
     * anteriores a {@code registroAte}.
     */
    Expurgo expurgar(Instant payloadAte, Instant registroAte);

    Pendencias pendencias();
}
