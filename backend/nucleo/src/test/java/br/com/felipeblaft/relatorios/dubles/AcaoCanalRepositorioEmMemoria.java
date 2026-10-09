package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.EstadoAcaoCanal;
import br.com.felipeblaft.relatorios.porta.AcaoCanalRepositorio;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Outbox em memória: reproduz a ordem por {@code seq}, a idempotência por id, o portão de ordem por
 * chat e a atualização condicional por {@code versao}. Guarda e devolve cópias, como um banco.
 */
public class AcaoCanalRepositorioEmMemoria implements AcaoCanalRepositorio {

    private final Map<String, AcaoCanal> dados = new LinkedHashMap<>();
    private final Map<String, Long> sequencias = new LinkedHashMap<>();
    private long proximaSequencia = 1;

    /** Chamado logo antes de cada {@code atualizarSeVersao}: simula um ator concorrente. */
    public Runnable antesDeAtualizar = () -> {};

    @Override
    public int gravarLote(List<AcaoCanal> acoes) {
        int gravadas = 0;
        for (AcaoCanal acao : acoes) {
            if (!dados.containsKey(acao.id())) {
                dados.put(acao.id(), copiar(acao));
                sequencias.put(acao.id(), proximaSequencia++);
                gravadas++;
            }
        }
        return gravadas;
    }

    @Override
    public Optional<AcaoCanal> buscarPorId(String id) {
        return Optional.ofNullable(dados.get(id)).map(AcaoCanalRepositorioEmMemoria::copiar);
    }

    @Override
    public List<AcaoCanal> buscarPorUpdate(long updateId) {
        return ordenadas(a -> a.updateId() != null && a.updateId() == updateId);
    }

    @Override
    public List<AcaoCanal> buscarPorLote(String loteId) {
        return ordenadas(a -> a.loteId().equals(loteId));
    }

    @Override
    public List<AcaoCanal> reservarProximoLoteDevido(
            Instant agora, Duration confirmacaoMax, Instant leaseAte) {
        Instant limite = agora.minus(confirmacaoMax);
        Optional<AcaoCanal> candidata =
                dados.values().stream()
                        .filter(a -> a.estado() == EstadoAcaoCanal.PENDENTE)
                        .filter(a -> !a.proximaTentativaEm().isAfter(agora))
                        .filter(this::primeiraPendenteDoLote)
                        .filter(a -> !bloqueadaPorOrdem(a, limite))
                        .min(Comparator.comparingLong(a -> sequencias.get(a.id())));
        if (candidata.isEmpty()) {
            return List.of();
        }
        String lote = candidata.get().loteId();
        List<AcaoCanal> reservadas = new ArrayList<>();
        for (AcaoCanal acao : ordenadas(a -> a.loteId().equals(lote))) {
            AcaoCanal guardada = dados.get(acao.id());
            if (guardada.estado() == EstadoAcaoCanal.PENDENTE) {
                guardada.reservarAte(leaseAte);
                guardada.definirVersao(guardada.versao() + 1);
                reservadas.add(copiar(guardada));
            }
        }
        return reservadas;
    }

    @Override
    public boolean atualizarSeVersao(AcaoCanal acao, int versaoEsperada) {
        antesDeAtualizar.run();
        AcaoCanal atual = dados.get(acao.id());
        if (atual == null || atual.versao() != versaoEsperada) {
            return false;
        }
        AcaoCanal gravada = copiar(acao);
        gravada.definirVersao(versaoEsperada + 1);
        dados.put(acao.id(), gravada);
        acao.definirVersao(versaoEsperada + 1);
        return true;
    }

    @Override
    public int expirarEntreguesVencidas(Instant agora) {
        int expiradas = 0;
        for (AcaoCanal acao : dados.values()) {
            if (acao.expirarSemConfirmacao(agora)) {
                acao.definirVersao(acao.versao() + 1);
                expiradas++;
            }
        }
        return expiradas;
    }

    @Override
    public Expurgo expurgar(Instant payloadAte, Instant registroAte) {
        int zerados = 0;
        for (AcaoCanal acao : dados.values()) {
            if (acao.estado().finalizado()
                    && acao.finalizadoEm().isBefore(payloadAte)
                    && (acao.payload() != null || acao.compensacao() != null)) {
                acao.zerarPayload();
                zerados++;
            }
        }
        int antes = dados.size();
        dados.values()
                .removeIf(a -> a.estado().finalizado() && a.finalizadoEm().isBefore(registroAte));
        return new Expurgo(zerados, antes - dados.size());
    }

    @Override
    public Pendencias pendencias(Instant entreguesAte) {
        long semConfirmacao =
                dados.values().stream()
                        .filter(a -> a.estado() == EstadoAcaoCanal.ENTREGUE)
                        .filter(a -> a.entregueEm().isBefore(entreguesAte))
                        .count();
        List<AcaoCanal> pendentes =
                dados.values().stream()
                        .filter(a -> a.estado() == EstadoAcaoCanal.PENDENTE)
                        .toList();
        Instant maisAntiga =
                pendentes.stream()
                        .map(AcaoCanal::criadoEm)
                        .min(Comparator.naturalOrder())
                        .orElse(null);
        return new Pendencias(pendentes.size(), maisAntiga, semConfirmacao);
    }

    /** Estado guardado (não uma cópia) para asserções. */
    public AcaoCanal guardada(String id) {
        return dados.get(id);
    }

    public List<AcaoCanal> todas() {
        return ordenadas(a -> true);
    }

    private List<AcaoCanal> ordenadas(Predicate<AcaoCanal> filtro) {
        return dados.values().stream()
                .filter(filtro)
                .sorted(Comparator.comparingLong(a -> sequencias.get(a.id())))
                .map(AcaoCanalRepositorioEmMemoria::copiar)
                .toList();
    }

    private boolean primeiraPendenteDoLote(AcaoCanal acao) {
        long minima =
                dados.values().stream()
                        .filter(a -> a.loteId().equals(acao.loteId()))
                        .filter(a -> a.estado() == EstadoAcaoCanal.PENDENTE)
                        .mapToLong(a -> sequencias.get(a.id()))
                        .min()
                        .orElseThrow();
        return sequencias.get(acao.id()) == minima;
    }

    private boolean bloqueadaPorOrdem(AcaoCanal acao, Instant limiteConfirmacao) {
        long seq = sequencias.get(acao.id());
        return dados.values().stream()
                .filter(b -> b.chatId() == acao.chatId())
                .filter(b -> sequencias.get(b.id()) < seq)
                .filter(b -> !b.loteId().equals(acao.loteId()))
                .anyMatch(
                        b ->
                                b.estado() == EstadoAcaoCanal.PENDENTE
                                        || (b.estado() == EstadoAcaoCanal.ENTREGUE
                                                && b.entregueEm().isAfter(limiteConfirmacao)));
    }

    private static AcaoCanal copiar(AcaoCanal a) {
        return new AcaoCanal(
                a.id(),
                a.loteId(),
                a.canal(),
                a.origem(),
                a.updateId(),
                a.chatId(),
                a.professorId(),
                a.trabalhoId(),
                a.tipo(),
                a.payload(),
                a.compensacao(),
                a.estado(),
                a.tentativas(),
                a.proximaTentativaEm(),
                a.prazoEm(),
                a.criadoEm(),
                a.entregueEm(),
                a.finalizadoEm(),
                a.codigoMotivo(),
                a.versao());
    }
}
