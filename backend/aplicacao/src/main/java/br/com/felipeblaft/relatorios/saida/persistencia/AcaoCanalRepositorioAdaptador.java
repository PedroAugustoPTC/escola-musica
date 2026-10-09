package br.com.felipeblaft.relatorios.saida.persistencia;

import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.EstadoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.porta.AcaoCanalRepositorio;
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
class AcaoCanalRepositorioAdaptador implements AcaoCanalRepositorio {

    private final AcaoCanalJpa jpa;
    private final EntityManager gerenciador;

    AcaoCanalRepositorioAdaptador(AcaoCanalJpa jpa, EntityManager gerenciador) {
        this.jpa = jpa;
        this.gerenciador = gerenciador;
    }

    @Override
    @Transactional
    public int gravarLote(List<AcaoCanal> acoes) {
        int gravadas = 0;
        for (AcaoCanal a : acoes) {
            gravadas +=
                    jpa.inserirSeNova(
                            a.id(),
                            a.loteId(),
                            a.canal().name(),
                            a.origem().name(),
                            a.updateId(),
                            a.chatId(),
                            a.professorId(),
                            a.trabalhoId(),
                            a.tipo().name(),
                            a.payload(),
                            a.compensacao(),
                            a.estado().name(),
                            a.tentativas(),
                            a.proximaTentativaEm(),
                            a.prazoEm(),
                            a.versao(),
                            a.criadoEm(),
                            a.entregueEm(),
                            a.finalizadoEm(),
                            a.codigoMotivo());
        }
        return gravadas;
    }

    @Override
    @Transactional
    public Optional<AcaoCanal> buscarPorId(String id) {
        return jpa.findById(id).map(this::ler);
    }

    @Override
    @Transactional
    public List<AcaoCanal> buscarPorUpdate(long updateId) {
        return jpa.findByUpdateIdOrderBySeq(updateId).stream().map(this::ler).toList();
    }

    @Override
    @Transactional
    public List<AcaoCanal> buscarPorLote(String loteId) {
        return jpa.findByLoteIdOrderBySeq(loteId).stream().map(this::ler).toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<AcaoCanal> reservarProximoLoteDevido(
            Instant agora, Duration confirmacaoMax, Instant leaseAte) {
        Optional<String> lote = jpa.proximoLoteDevido(agora, agora.minus(confirmacaoMax));
        if (lote.isEmpty()) {
            return List.of();
        }
        jpa.reservarLote(lote.get(), leaseAte);
        return jpa.findByLoteIdOrderBySeq(lote.get()).stream()
                .filter(e -> EstadoAcaoCanal.PENDENTE.name().equals(e.estado))
                .map(this::ler)
                .toList();
    }

    @Override
    @Transactional
    public boolean atualizarSeVersao(AcaoCanal acao, int versaoEsperada) {
        int linhas =
                jpa.atualizarSeVersao(
                        acao.id(),
                        versaoEsperada,
                        acao.estado().name(),
                        acao.tentativas(),
                        acao.proximaTentativaEm(),
                        acao.entregueEm(),
                        acao.finalizadoEm(),
                        acao.codigoMotivo(),
                        acao.payload(),
                        acao.compensacao());
        if (linhas == 0) {
            return false;
        }
        acao.definirVersao(versaoEsperada + 1);
        return true;
    }

    @Override
    @Transactional
    public int expirarEntreguesVencidas(Instant agora) {
        return jpa.expirarEntreguesVencidas(agora);
    }

    @Override
    @Transactional
    public Expurgo expurgar(Instant payloadAte, Instant registroAte) {
        int zerados = jpa.zerarConteudoFinalizadoAte(payloadAte);
        int removidos = jpa.removerFinalizadasAte(registroAte);
        return new Expurgo(zerados, removidos);
    }

    @Override
    @Transactional
    public Pendencias pendencias(Instant entreguesAte) {
        return new Pendencias(
                jpa.contarPendentes(),
                jpa.criadaMaisAntigaPendente().orElse(null),
                jpa.contarEntreguesDesde(entreguesAte));
    }

    private AcaoCanal ler(AcaoCanalEntidade e) {
        AcaoCanal acao =
                new AcaoCanal(
                        e.id,
                        e.loteId,
                        Canal.valueOf(e.canal),
                        OrigemAcaoCanal.valueOf(e.origem),
                        e.updateId,
                        e.chatId,
                        e.professorId,
                        e.trabalhoId,
                        TipoAcaoCanal.valueOf(e.tipo),
                        e.payload,
                        e.compensacao,
                        EstadoAcaoCanal.valueOf(e.estado),
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
