package br.com.felipeblaft.relatorios.casodeuso.canal;

import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.porta.AcaoCanalRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Grava as ações de um evento na outbox, de forma idempotente por {@code acaoId} (FR-014): o mesmo
 * evento processado duas vezes não cria ação nova. Respostas a update nascem {@code ENTREGUE}
 * (seguem na resposta HTTP); proativas e compensações nascem {@code PENDENTE}.
 */
public class GravarAcoesCanalCasoDeUso {

    /**
     * Ação já mapeada pelo adaptador de entrada; {@code payload} e {@code compensacao} são opacos.
     */
    public record NovaAcao(
            String id,
            TipoAcaoCanal tipo,
            long chatId,
            UUID professorId,
            UUID trabalhoId,
            String payload,
            String compensacao) {}

    private final AcaoCanalRepositorio acoes;
    private final RelogioPorta relogio;
    private final PoliticaRetentativa politica;

    public GravarAcoesCanalCasoDeUso(
            AcaoCanalRepositorio acoes, RelogioPorta relogio, PoliticaRetentativa politica) {
        this.acoes = Objects.requireNonNull(acoes);
        this.relogio = Objects.requireNonNull(relogio);
        this.politica = Objects.requireNonNull(politica);
    }

    /**
     * @param loteId evento de origem ({@code u<update_id>}, {@code p:<evento>} ou {@code
     *     c:<acaoId>})
     * @param updateId obrigatório em {@link OrigemAcaoCanal#RESPOSTA_UPDATE}
     * @return quantas ações foram de fato gravadas (as já existentes não contam)
     */
    public int executar(
            String loteId, OrigemAcaoCanal origem, Long updateId, List<NovaAcao> novas) {
        Instant agora = relogio.agora();
        List<AcaoCanal> lote =
                novas.stream()
                        .map(
                                nova ->
                                        AcaoCanal.nova(
                                                nova.id(),
                                                loteId,
                                                origem,
                                                updateId,
                                                nova.chatId(),
                                                nova.professorId(),
                                                nova.trabalhoId(),
                                                nova.tipo(),
                                                nova.payload(),
                                                nova.compensacao(),
                                                agora,
                                                politica))
                        .toList();
        return acoes.gravarLote(lote);
    }
}
