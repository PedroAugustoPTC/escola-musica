package br.com.felipeblaft.relatorios.saida.fila;

import br.com.felipeblaft.relatorios.casodeuso.canal.EntregarAcoesPendentesCasoDeUso;
import br.com.felipeblaft.relatorios.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.relatorios.porta.AcaoCanalRepositorio;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Despachante da outbox (FR-011, FR-012): a cada {@code intervalo-despacho-ms} entrega ao
 * orquestrador os lotes devidos, com retentativa, ordem por chat e prazo (R-04 a R-06). Só existe
 * quando há {@code orquestrador-url}; vários despachantes (várias instâncias) não disputam o mesmo
 * lote ({@code SKIP LOCKED} e lease). Expõe as métricas de R-15, sem dado de aluno.
 */
@Component
@Profile("!carga")
@ConditionalOnExpression("!'${relatorios.canal-telegram.orquestrador-url:}'.isBlank()")
public class EntregaAcoesCanalTarefa {

    private static final Logger LOG = LoggerFactory.getLogger(EntregaAcoesCanalTarefa.class);

    private final EntregarAcoesPendentesCasoDeUso entregar;
    private final AcaoCanalRepositorio acoes;
    private final RelogioPorta relogio;
    private final int loteMaxPorCiclo;
    private final Duration confirmacaoMax;
    private final Counter entregasAceitas;
    private final Counter entregasRecusadas;
    private final Counter falhasDefinitivas;

    public EntregaAcoesCanalTarefa(
            EntregarAcoesPendentesCasoDeUso entregar,
            AcaoCanalRepositorio acoes,
            RelogioPorta relogio,
            MeterRegistry registro,
            PropriedadesAplicacao propriedades) {
        this.entregar = entregar;
        this.acoes = acoes;
        this.relogio = relogio;
        PropriedadesAplicacao.CanalTelegram canal = propriedades.canalTelegram();
        this.loteMaxPorCiclo = canal.loteMaxPorCiclo();
        this.confirmacaoMax = Duration.ofSeconds(canal.confirmacaoMaxSegundos());
        this.entregasAceitas =
                Counter.builder("canal_entregas_total")
                        .tag("resultado", "aceita")
                        .register(registro);
        this.entregasRecusadas =
                Counter.builder("canal_entregas_total")
                        .tag("resultado", "recusada")
                        .register(registro);
        this.falhasDefinitivas =
                Counter.builder("canal_acoes_falha_definitiva_total").register(registro);
        Gauge.builder("canal_acoes_pendentes", this, t -> t.pendencias().quantidade())
                .description("Ações do canal aguardando entrega ao orquestrador")
                .register(registro);
        Gauge.builder(
                        "canal_acao_pendente_mais_antiga_segundos",
                        this,
                        t -> {
                            Instant criada = t.pendencias().maisAntigaCriadaEm();
                            return criada == null
                                    ? 0
                                    : Math.max(
                                            0,
                                            Duration.between(criada, t.relogio.agora())
                                                    .toSeconds());
                        })
                .description("Idade da ação pendente mais antiga")
                .register(registro);
        Gauge.builder(
                        "canal_acoes_sem_confirmacao_total",
                        this,
                        t -> t.pendencias().semConfirmacao())
                .description("Ações entregues e ainda sem confirmação além da espera máxima")
                .register(registro);
    }

    private AcaoCanalRepositorio.Pendencias pendencias() {
        return acoes.pendencias(relogio.agora().minus(confirmacaoMax));
    }

    @Scheduled(
            fixedDelayString = "${relatorios.canal-telegram.intervalo-despacho-ms:2000}",
            initialDelayString = "${relatorios.canal-telegram.intervalo-despacho-ms:2000}")
    public void despachar() {
        try {
            EntregarAcoesPendentesCasoDeUso.Ciclo ciclo = entregar.executar(loteMaxPorCiclo);
            entregasAceitas.increment(ciclo.lotesAceitos());
            entregasRecusadas.increment(ciclo.lotesRecusados());
            for (String acaoId : ciclo.falhasDefinitivas()) {
                falhasDefinitivas.increment();
                // sem dado de aluno: só o identificador técnico (FR-018, FR-029)
                LOG.warn("canal-acao-falha-definitiva acaoId={}", acaoId);
            }
        } catch (RuntimeException erro) {
            LOG.error("canal-despacho-falhou tipo={}", erro.getClass().getSimpleName());
        }
    }
}
