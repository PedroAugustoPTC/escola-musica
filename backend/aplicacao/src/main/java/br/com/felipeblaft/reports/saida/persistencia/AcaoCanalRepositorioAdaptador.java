package br.com.felipeblaft.reports.saida.persistencia;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox no PostgreSQL (R-04 a R-06): gravação idempotente por id, reserva do lote devido com
 * {@code FOR UPDATE SKIP LOCKED} e lease, e atualização condicional pela coluna {@code versao}. As
 * leituras são destacadas do contexto de persistência para que uma releitura na mesma transação
 * (ex.: nova tentativa de uma confirmação) sempre veja o que está no banco.
 */
@Repository
class AcaoCanalRepositorioAdaptador implements ChannelActionRepository {

    private final AcaoCanalJpa jpa;
    private final EntityManager gerenciador;

    AcaoCanalRepositorioAdaptador(AcaoCanalJpa jpa, EntityManager gerenciador) {
        this.jpa = jpa;
        this.gerenciador = gerenciador;
    }

    @Override
    @Transactional
    public int saveBatch(List<ChannelAction> acoes) {
        int gravadas = 0;
        for (ChannelAction a : acoes) {
            gravadas +=
                    jpa.inserirSeNova(
                            a.id(),
                            a.batchId(),
                            a.channel().name(),
                            a.origin().name(),
                            a.updateId(),
                            a.chatId(),
                            a.teacherId(),
                            a.jobId(),
                            a.type().name(),
                            a.payload(),
                            a.compensation(),
                            a.state().name(),
                            a.attempts(),
                            a.nextAttemptAt(),
                            a.deadlineAt(),
                            a.version(),
                            a.createdAt(),
                            a.deliveredAt(),
                            a.finalizedAt(),
                            a.reasonCode());
        }
        return gravadas;
    }

    @Override
    @Transactional
    public Optional<ChannelAction> searchById(String id) {
        return jpa.findById(id).map(this::ler);
    }

    @Override
    @Transactional
    public List<ChannelAction> searchByUpdate(long updateId) {
        return jpa.findByUpdateIdOrderBySeq(updateId).stream().map(this::ler).toList();
    }

    @Override
    @Transactional
    public List<ChannelAction> searchByBatch(String loteId) {
        return jpa.findByLoteIdOrderBySeq(loteId).stream().map(this::ler).toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ChannelAction> reserveNextDueBatch(
            Instant agora, Duration confirmacaoMax, Instant leaseAte) {
        Optional<String> lote = jpa.proximoLoteDevido(agora, agora.minus(confirmacaoMax));
        if (lote.isEmpty()) {
            return List.of();
        }
        jpa.reservarLote(lote.get(), leaseAte);
        return jpa.findByLoteIdOrderBySeq(lote.get()).stream()
                .filter(e -> ChannelActionState.PENDENTE.name().equals(e.estado))
                .map(this::ler)
                .toList();
    }

    @Override
    @Transactional
    public boolean updateIfVersion(ChannelAction acao, int versaoEsperada) {
        int linhas =
                jpa.atualizarSeVersao(
                        acao.id(),
                        versaoEsperada,
                        acao.state().name(),
                        acao.attempts(),
                        acao.nextAttemptAt(),
                        acao.deliveredAt(),
                        acao.finalizedAt(),
                        acao.reasonCode(),
                        acao.payload(),
                        acao.compensation());
        if (linhas == 0) {
            return false;
        }
        acao.defineVersion(versaoEsperada + 1);
        return true;
    }

    @Override
    @Transactional
    public int expireOverdueDelivered(Instant agora) {
        return jpa.expirarEntreguesVencidas(agora);
    }

    @Override
    @Transactional
    public Purge purge(Instant payloadAte, Instant registroAte) {
        int zerados = jpa.zerarConteudoFinalizadoAte(payloadAte);
        int removidos = jpa.removerFinalizadasAte(registroAte);
        return new Purge(zerados, removidos);
    }

    @Override
    @Transactional
    public Pending pending(Instant entreguesAte) {
        return new Pending(
                jpa.contarPendentes(),
                jpa.criadaMaisAntigaPendente().orElse(null),
                jpa.contarEntreguesDesde(entreguesAte));
    }

    private ChannelAction ler(AcaoCanalEntidade e) {
        ChannelAction acao =
                new ChannelAction(
                        e.id,
                        e.loteId,
                        Channel.valueOf(e.canal),
                        ChannelActionOrigin.valueOf(e.origem),
                        e.updateId,
                        e.chatId,
                        e.professorId,
                        e.trabalhoId,
                        ChannelActionType.valueOf(e.tipo),
                        e.payload,
                        e.compensacao,
                        ChannelActionState.valueOf(e.estado),
                        e.tentativas,
                        e.proximaTentativaEm,
                        e.prazoEm,
                        e.criadoEm,
                        e.entregueEm,
                        e.finalizadoEm,
                        e.codigoMotivo,
                        e.versao);
        gerenciador.detach(e);
        return acao;
    }
}
