package br.com.felipeblaft.relatorios.casodeuso.canal;

import br.com.felipeblaft.relatorios.porta.AcaoCanalRepositorio;
import br.com.felipeblaft.relatorios.porta.DocumentoCanalRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Retenção da outbox (R-11): ação entregue e nunca confirmada expira no prazo; o conteúdo das ações
 * finalizadas some depois de {@code retencaoPayload}; o registro (sem conteúdo) some depois de
 * {@code retencaoRegistro}; documentos vencidos são removidos.
 */
public class LimparAcoesCanalCasoDeUso {

    public record Resultado(
            int acoesExpiradas,
            int payloadsZerados,
            int registrosRemovidos,
            int documentosRemovidos) {}

    private final AcaoCanalRepositorio acoes;
    private final DocumentoCanalRepositorio documentos;
    private final RelogioPorta relogio;
    private final Duration retencaoPayload;
    private final Duration retencaoRegistro;

    public LimparAcoesCanalCasoDeUso(
            AcaoCanalRepositorio acoes,
            DocumentoCanalRepositorio documentos,
            RelogioPorta relogio,
            Duration retencaoPayload,
            Duration retencaoRegistro) {
        this.acoes = Objects.requireNonNull(acoes);
        this.documentos = Objects.requireNonNull(documentos);
        this.relogio = Objects.requireNonNull(relogio);
        this.retencaoPayload = Objects.requireNonNull(retencaoPayload);
        this.retencaoRegistro = Objects.requireNonNull(retencaoRegistro);
    }

    public Resultado executar() {
        Instant agora = relogio.agora();
        int expiradas = acoes.expirarEntreguesVencidas(agora);
        AcaoCanalRepositorio.Expurgo expurgo =
                acoes.expurgar(agora.minus(retencaoPayload), agora.minus(retencaoRegistro));
        int removidos = documentos.expurgarVencidos(agora);
        return new Resultado(
                expiradas, expurgo.payloadsZerados(), expurgo.registrosRemovidos(), removidos);
    }
}
