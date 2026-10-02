package br.com.felipeblaft.relatorios.casodeuso.canal;

import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.porta.AcaoCanalRepositorio;
import br.com.felipeblaft.relatorios.porta.FilaTrabalhoPorta;
import br.com.felipeblaft.relatorios.porta.LoteEntrega;
import br.com.felipeblaft.relatorios.porta.OrquestradorCanalPorta;
import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import br.com.felipeblaft.relatorios.porta.ResultadoEntrega;
import br.com.felipeblaft.relatorios.porta.VinculoTelegramRepositorio;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Despacha ao orquestrador os lotes de ações proativas devidos (FR-011, FR-012, FR-016, FR-018). Um
 * lote é entregue inteiro e fora de transação: o HTTP nunca segura conexão do banco. Toda gravação
 * é condicional à versão lida, de modo que uma confirmação que chegue durante o HTTP prevalece sobre
 * o aceite.
 *
 * <p>Este caso de uso <strong>não</strong> roda dentro de uma transação única (ver
 * {@code TransacaoCasoDeUsoDecorador}).
 */
public class EntregarAcoesPendentesCasoDeUso {

    /**
     * @param lotesAceitos lotes que o orquestrador aceitou
     * @param lotesRecusados lotes recusados (continuam pendentes até o prazo)
     * @param descartadas ações descartadas por vínculo desfeito
     * @param falhasDefinitivas ids das ações que venceram o prazo sem aceite
     */
    public record Ciclo(
            int lotesAceitos, int lotesRecusados, int descartadas, List<String> falhasDefinitivas) {}

    private final AcaoCanalRepositorio acoes;
    private final OrquestradorCanalPorta orquestrador;
    private final FilaTrabalhoPorta fila;
    private final VinculoTelegramRepositorio vinculos;
    private final RelogioPorta relogio;
    private final PoliticaRetentativa politica;
    private final Duration confirmacaoMax;
    private final Duration lease;

    public EntregarAcoesPendentesCasoDeUso(
            AcaoCanalRepositorio acoes,
            OrquestradorCanalPorta orquestrador,
            FilaTrabalhoPorta fila,
            VinculoTelegramRepositorio vinculos,
            RelogioPorta relogio,
            PoliticaRetentativa politica,
            Duration confirmacaoMax,
            Duration lease) {
        this.acoes = Objects.requireNonNull(acoes);
        this.orquestrador = Objects.requireNonNull(orquestrador);
        this.fila = Objects.requireNonNull(fila);
        this.vinculos = Objects.requireNonNull(vinculos);
        this.relogio = Objects.requireNonNull(relogio);
        this.politica = Objects.requireNonNull(politica);
        this.confirmacaoMax = Objects.requireNonNull(confirmacaoMax);
        this.lease = Objects.requireNonNull(lease);
    }

    /** Processa até {@code maximoLotes} lotes devidos. */
    public Ciclo executar(int maximoLotes) {
        int aceitos = 0;
        int recusados = 0;
        int descartadas = 0;
        List<String> falhas = new ArrayList<>();
        for (int i = 0; i < maximoLotes; i++) {
            Instant agora = relogio.agora();
            List<AcaoCanal> lote =
                    acoes.reservarProximoLoteDevido(agora, confirmacaoMax, agora.plus(lease));
            if (lote.isEmpty()) {
                break;
            }
            List<AcaoCanal> aEntregar = new ArrayList<>();
            for (AcaoCanal acao : lote) {
                int versaoLida = acao.versao();
                if (acao.vencida(agora)) {
                    acao.expirar(agora);
                    if (acoes.atualizarSeVersao(acao, versaoLida)) {
                        falhas.add(acao.id());
                    }
                } else if (acao.professorId() != null
                        && vinculos.buscarPorProfessorId(acao.professorId()).isEmpty()) {
                    acao.descartar(agora);
                    if (acoes.atualizarSeVersao(acao, versaoLida)) {
                        descartadas++;
                    }
                } else {
                    aEntregar.add(acao);
                }
            }
            if (aEntregar.isEmpty()) {
                continue;
            }
            ResultadoEntrega resultado = orquestrador.entregar(paraLote(aEntregar));
            Instant depois = relogio.agora();
            for (AcaoCanal acao : aEntregar) {
                int versaoLida = acao.versao();
                if (resultado.aceito()) {
                    acao.marcarEntregue(depois);
                } else {
                    acao.registrarFalhaEntrega(depois, politica);
                }
                // false = a confirmação chegou durante o HTTP: o estado final prevalece
                if (acoes.atualizarSeVersao(acao, versaoLida)
                        && acao.estado().finalizado()
                        && !resultado.aceito()) {
                    falhas.add(acao.id());
                }
            }
            if (resultado.aceito()) {
                aceitos++;
            } else {
                recusados++;
            }
        }
        return new Ciclo(aceitos, recusados, descartadas, List.copyOf(falhas));
    }

    private LoteEntrega paraLote(List<AcaoCanal> lote) {
        AcaoCanal primeira = lote.get(0);
        int tentativa = lote.stream().mapToInt(AcaoCanal::tentativas).max().orElse(0) + 1;
        List<LoteEntrega.Acao> itens =
                lote.stream()
                        .map(
                                acao ->
                                        new LoteEntrega.Acao(
                                                acao.id(),
                                                acao.tipo(),
                                                acao.payload(),
                                                acao.trabalhoId(),
                                                mensagemDaEtapa(acao).orElse(null)))
                        .toList();
        return new LoteEntrega(
                primeira.loteId(), primeira.origem(), primeira.criadoEm(), tentativa, itens);
    }

    /** Id da mensagem de etapa já conhecida do trabalho (a etapa edita no lugar). */
    private Optional<Long> mensagemDaEtapa(AcaoCanal acao) {
        if (acao.tipo() != TipoAcaoCanal.MENSAGEM_ETAPA || acao.trabalhoId() == null) {
            return Optional.empty();
        }
        return fila.buscarPorId(acao.trabalhoId())
                .flatMap(trabalho -> trabalho.parametro(TrabalhoProcessamento.MENSAGEM_ETAPA))
                .flatMap(EntregarAcoesPendentesCasoDeUso::comoNumero);
    }

    private static Optional<Long> comoNumero(String valor) {
        try {
            return Optional.of(Long.parseLong(valor));
        } catch (NumberFormatException erro) {
            return Optional.empty();
        }
    }
}
