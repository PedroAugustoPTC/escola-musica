package br.com.felipeblaft.reports.saida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.channel.ChannelAction;
import br.com.felipeblaft.reports.domain.channel.ChannelActionOrigin;
import br.com.felipeblaft.reports.domain.channel.ChannelActionState;
import br.com.felipeblaft.reports.domain.channel.ChannelActionType;
import br.com.felipeblaft.reports.domain.channel.ChannelDocument;
import br.com.felipeblaft.reports.domain.channel.RetryPolicy;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.port.ChannelActionRepository;
import br.com.felipeblaft.reports.port.ChannelDocumentRepository;
import br.com.felipeblaft.reports.suporte.IntegracaoBase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AcaoCanalRepositorioAdaptadorIT extends IntegracaoBase {

    private static final Instant T0 = Instant.parse("2026-10-02T12:00:00Z");
    private static final Duration CONFIRMACAO = Duration.ofSeconds(60);

    private final RetryPolicy politica =
            new RetryPolicy(Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));

    @Autowired ChannelActionRepository acoes;
    @Autowired ChannelDocumentRepository documentos;

    private ChannelAction proativa(String loteId, int pos, long chatId) {
        return ChannelAction.create(
                loteId + ":" + pos,
                loteId,
                ChannelActionOrigin.PROATIVA,
                null,
                chatId,
                UUID.randomUUID(),
                null,
                ChannelActionType.MENSAGEM,
                "{\"texto\":\"" + loteId + pos + "\"}",
                "{\"texto\":\"alt\"}",
                T0,
                politica);
    }

    private List<ChannelAction> lote(String loteId, long chatId, int quantidade) {
        List<ChannelAction> lote = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            lote.add(proativa(loteId, i, chatId));
        }
        return lote;
    }

    @Test
    void gravacaoEIdempotentePorIdENuncaSobrescreve() {
        ChannelAction original = proativa("p:a", 0, 1);
        assertThat(acoes.saveBatch(List.of(original))).isEqualTo(1);
        ChannelAction outra =
                ChannelAction.create(
                        "p:a:0",
                        "p:a",
                        ChannelActionOrigin.PROATIVA,
                        null,
                        1,
                        UUID.randomUUID(),
                        null,
                        ChannelActionType.MENSAGEM,
                        "{\"texto\":\"diferente\"}",
                        null,
                        T0.plusSeconds(5),
                        politica);
        assertThat(acoes.saveBatch(List.of(outra, proativa("p:a", 1, 1)))).isEqualTo(1);

        ChannelAction lida = acoes.searchById("p:a:0").orElseThrow();
        assertThat(lida.payload()).contains("p:a0");
        assertThat(lida.compensation()).contains("alt");
        assertThat(lida.state()).isEqualTo(ChannelActionState.PENDENTE);
        assertThat(lida.createdAt()).isEqualTo(T0);
        assertThat(lida.version()).isZero();
        assertThat(acoes.searchById("inexistente")).isEmpty();
    }

    @Test
    void respostasAUpdateSaoLidasPorUpdateNaOrdemDeCriacao() {
        List<ChannelAction> respostas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            respostas.add(
                    ChannelAction.create(
                            "u7-" + i,
                            "u7",
                            ChannelActionOrigin.RESPOSTA_UPDATE,
                            7L,
                            55,
                            null,
                            null,
                            ChannelActionType.MENSAGEM,
                            "{}",
                            null,
                            T0,
                            politica));
        }
        acoes.saveBatch(respostas);
        assertThat(acoes.searchByUpdate(7L))
                .extracting(ChannelAction::id)
                .containsExactly("u7-0", "u7-1", "u7-2");
        assertThat(acoes.searchByBatch("u7"))
                .extracting(ChannelAction::id)
                .containsExactly("u7-0", "u7-1", "u7-2");
        assertThat(acoes.searchByUpdate(8L)).isEmpty();
        assertThat(acoes.searchByUpdate(7L).get(0).state()).isEqualTo(ChannelActionState.ENTREGUE);
        assertThat(acoes.searchByUpdate(7L).get(0).deliveredAt()).isEqualTo(T0);
    }

    @Test
    void reservaDevolveOLoteInteiroEOLeaseImpedeASegundaReserva() {
        acoes.saveBatch(lote("p:a", 1, 3));
        Instant lease = T0.plusSeconds(60);

        List<ChannelAction> reservado = acoes.reserveNextDueBatch(T0, CONFIRMACAO, lease);

        assertThat(reservado)
                .extracting(ChannelAction::id)
                .containsExactly("p:a:0", "p:a:1", "p:a:2");
        assertThat(reservado).allSatisfy(a -> assertThat(a.version()).isEqualTo(1));
        assertThat(reservado).allSatisfy(a -> assertThat(a.nextAttemptAt()).isEqualTo(lease));
        assertThat(
                        acoes.reserveNextDueBatch(
                                T0.plusSeconds(59), CONFIRMACAO, lease.plusSeconds(60)))
                .isEmpty();
        // o lease venceu: o mesmo lote volta, com os mesmos ids
        assertThat(
                        acoes.reserveNextDueBatch(
                                T0.plusSeconds(60), CONFIRMACAO, lease.plusSeconds(60)))
                .extracting(ChannelAction::id)
                .containsExactly("p:a:0", "p:a:1", "p:a:2");
    }

    @Test
    void naoReservaAcaoAindaNaoDevida() {
        acoes.saveBatch(lote("p:a", 1, 1));
        assertThat(acoes.reserveNextDueBatch(T0.minusSeconds(1), CONFIRMACAO, T0.plusSeconds(60)))
                .isEmpty();
    }

    @Test
    void doisConsumidoresConcorrentesNuncaReservamOMesmoLote() throws Exception {
        int lotes = 6;
        for (int i = 0; i < lotes; i++) {
            acoes.saveBatch(lote("p:l" + i, 100 + i, 3));
        }
        int consumidores = 8;
        ExecutorService pool = Executors.newFixedThreadPool(consumidores);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<List<List<ChannelAction>>>> futuros = new ArrayList<>();
        for (int c = 0; c < consumidores; c++) {
            futuros.add(
                    pool.submit(
                            () -> {
                                largada.await();
                                List<List<ChannelAction>> meus = new ArrayList<>();
                                for (int i = 0; i < lotes; i++) {
                                    List<ChannelAction> reservado =
                                            acoes.reserveNextDueBatch(
                                                    T0, CONFIRMACAO, T0.plusSeconds(60));
                                    if (!reservado.isEmpty()) {
                                        meus.add(reservado);
                                    }
                                }
                                return meus;
                            }));
        }
        largada.countDown();
        Set<String> ids = new HashSet<>();
        int lotesReservados = 0;
        for (Future<List<List<ChannelAction>>> futuro : futuros) {
            for (List<ChannelAction> reservado : futuro.get()) {
                lotesReservados++;
                assertThat(reservado).hasSize(3);
                assertThat(reservado.stream().map(ChannelAction::batchId).distinct()).hasSize(1);
                for (ChannelAction acao : reservado) {
                    assertThat(ids.add(acao.id()))
                            .as("ação %s reservada duas vezes", acao.id())
                            .isTrue();
                }
            }
        }
        pool.shutdown();
        assertThat(lotesReservados).isEqualTo(lotes);
        assertThat(ids).hasSize(lotes * 3);
    }

    @Test
    void portaoDeOrdemPorChatEsperaConfirmacaoOuTimeout() {
        acoes.saveBatch(lote("p:a", 1, 1));
        acoes.saveBatch(lote("p:b", 1, 1));
        acoes.saveBatch(lote("p:c", 2, 1));

        List<ChannelAction> primeiro =
                acoes.reserveNextDueBatch(T0, CONFIRMACAO, T0.plusSeconds(60));
        assertThat(primeiro).extracting(ChannelAction::batchId).containsExactly("p:a");
        // o chat 1 está atrás do lote A (ainda pendente); o chat 2 é independente
        List<ChannelAction> segundo =
                acoes.reserveNextDueBatch(T0, CONFIRMACAO, T0.plusSeconds(60));
        assertThat(segundo).extracting(ChannelAction::batchId).containsExactly("p:c");
        assertThat(acoes.reserveNextDueBatch(T0, CONFIRMACAO, T0.plusSeconds(60))).isEmpty();

        // A é aceito (ENTREGUE) mas não confirmado: B espera até o timeout do portão
        ChannelAction a = primeiro.get(0);
        a.markDelivered(T0);
        assertThat(acoes.updateIfVersion(a, a.version())).isTrue();
        Instant depoisDoLease = T0.plusSeconds(61);
        assertThat(acoes.reserveNextDueBatch(T0.plusSeconds(30), CONFIRMACAO, depoisDoLease))
                .isEmpty();
        assertThat(
                        acoes.reserveNextDueBatch(
                                depoisDoLease, CONFIRMACAO, depoisDoLease.plusSeconds(60)))
                .extracting(ChannelAction::batchId)
                .containsExactly("p:b");
    }

    @Test
    void confirmacaoLiberaOProximoLoteDoChatAntesDoTimeout() {
        acoes.saveBatch(lote("p:a", 1, 1));
        acoes.saveBatch(lote("p:b", 1, 1));
        ChannelAction a = acoes.reserveNextDueBatch(T0, CONFIRMACAO, T0.plusSeconds(60)).get(0);
        a.markDelivered(T0);
        acoes.updateIfVersion(a, a.version());
        a = acoes.searchById(a.id()).orElseThrow();
        a.confirmExecuted(T0.plusSeconds(2));
        assertThat(acoes.updateIfVersion(a, a.version())).isTrue();

        assertThat(acoes.reserveNextDueBatch(T0.plusSeconds(3), CONFIRMACAO, T0.plusSeconds(63)))
                .extracting(ChannelAction::batchId)
                .containsExactly("p:b");
    }

    @Test
    void atualizacaoCondicionalAvancaAVersaoESoAceitaAVersaoLida() {
        acoes.saveBatch(lote("p:a", 1, 1));
        ChannelAction lida = acoes.searchById("p:a:0").orElseThrow();
        ChannelAction concorrente = acoes.searchById("p:a:0").orElseThrow();

        lida.confirmExecuted(T0);
        assertThat(acoes.updateIfVersion(lida, 0)).isTrue();
        assertThat(lida.version()).isEqualTo(1);

        concorrente.markDelivered(T0);
        assertThat(acoes.updateIfVersion(concorrente, 0)).isFalse();
        assertThat(concorrente.version()).isZero();

        ChannelAction gravada = acoes.searchById("p:a:0").orElseThrow();
        assertThat(gravada.state()).isEqualTo(ChannelActionState.EXECUTADA);
        assertThat(gravada.version()).isEqualTo(1);
        assertThat(gravada.finalizedAt()).isEqualTo(T0);
    }

    @Test
    void releituraSempreVeOEstadoDoBanco() {
        acoes.saveBatch(lote("p:a", 1, 1));
        jdbc.update(
                "update acao_canal set estado = 'EXECUTADA', versao = versao + 1 where id = 'p:a:0'");
        assertThat(acoes.searchById("p:a:0").orElseThrow().state())
                .isEqualTo(ChannelActionState.EXECUTADA);
        jdbc.update(
                "update acao_canal set estado = 'FALHOU', versao = versao + 1 where id = 'p:a:0'");
        assertThat(acoes.searchById("p:a:0").orElseThrow().state())
                .isEqualTo(ChannelActionState.FALHOU);
    }

    @Test
    void falhaDefinitivaEDescarteSaoGravadosComMotivo() {
        acoes.saveBatch(lote("p:a", 1, 2));
        ChannelAction a = acoes.searchById("p:a:0").orElseThrow();
        a.discard(T0.plusSeconds(1));
        acoes.updateIfVersion(a, 0);
        ChannelAction b = acoes.searchById("p:a:1").orElseThrow();
        b.expire(T0.plus(Duration.ofHours(25)));
        acoes.updateIfVersion(b, 0);

        assertThat(acoes.searchById("p:a:0").orElseThrow().reasonCode())
                .isEqualTo("VINCULO_DESFEITO");
        ChannelAction falha = acoes.searchById("p:a:1").orElseThrow();
        assertThat(falha.state()).isEqualTo(ChannelActionState.FALHA_DEFINITIVA);
        assertThat(falha.reasonCode()).isEqualTo("PRAZO_VENCIDO");
        assertThat(falha.nextAttemptAt()).isNull();
    }

    @Test
    void expurgoZeraOConteudoDepoisDaRetencaoERemoveORegistroDepois() {
        acoes.saveBatch(lote("p:a", 1, 2));
        ChannelAction finalizada = acoes.searchById("p:a:0").orElseThrow();
        finalizada.confirmExecuted(T0);
        acoes.updateIfVersion(finalizada, 0);

        ChannelActionRepository.Purge nada = acoes.purge(T0.minusSeconds(1), T0.minusSeconds(1));
        assertThat(nada).isEqualTo(new ChannelActionRepository.Purge(0, 0));

        ChannelActionRepository.Purge zerou = acoes.purge(T0.plusSeconds(1), T0.minusSeconds(1));
        assertThat(zerou.resetPayloads()).isEqualTo(1);
        ChannelAction semConteudo = acoes.searchById("p:a:0").orElseThrow();
        assertThat(semConteudo.payload()).isNull();
        assertThat(semConteudo.compensation()).isNull();
        // a pendente não é tocada
        assertThat(acoes.searchById("p:a:1").orElseThrow().payload()).isNotNull();

        ChannelActionRepository.Purge removeu = acoes.purge(T0.plusSeconds(1), T0.plusSeconds(1));
        assertThat(removeu.removedRegistrations()).isEqualTo(1);
        assertThat(acoes.searchById("p:a:0")).isEmpty();
        assertThat(acoes.searchById("p:a:1")).isPresent();
    }

    @Test
    void pendenciasContamSoAsPendentesEInformamAMaisAntiga() {
        assertThat(acoes.pending(T0)).isEqualTo(new ChannelActionRepository.Pending(0, null, 0));
        acoes.saveBatch(lote("p:a", 1, 3));
        ChannelAction f = acoes.searchById("p:a:0").orElseThrow();
        f.confirmExecuted(T0);
        acoes.updateIfVersion(f, 0);
        ChannelAction e = acoes.searchById("p:a:1").orElseThrow();
        e.markDelivered(T0);
        acoes.updateIfVersion(e, 0);
        ChannelActionRepository.Pending pendencias = acoes.pending(T0.plusSeconds(60));
        assertThat(pendencias.quantity()).isEqualTo(1);
        assertThat(pendencias.oldestCreatedAt()).isEqualTo(T0);
        assertThat(pendencias.withoutConfirmation()).isEqualTo(1);
        assertThat(acoes.pending(T0).withoutConfirmation()).isZero();
    }

    @Test
    void documentoCanalEmiteBuscaPorHashEExpurgaVencidos() {
        UUID professor = UUID.randomUUID();
        UUID relatorio = UUID.randomUUID();
        String hash = ChannelDocument.hashDoToken("token-de-teste");
        documentos.emit(
                new ChannelDocument(
                        hash,
                        professor,
                        ReportType.SEMESTRAL,
                        relatorio,
                        3,
                        "s-v3.pdf",
                        T0,
                        T0.plusSeconds(100)));
        documentos.emit(
                new ChannelDocument(
                        ChannelDocument.hashDoToken("outro"),
                        professor,
                        ReportType.AULA,
                        relatorio,
                        null,
                        "a.pdf",
                        T0,
                        T0.plusSeconds(10)));

        ChannelDocument lido = documentos.searchByHash(hash).orElseThrow();
        assertThat(lido.teacherId()).isEqualTo(professor);
        assertThat(lido.reportType()).isEqualTo(ReportType.SEMESTRAL);
        assertThat(lido.version()).isEqualTo(3);
        assertThat(lido.expiresAt()).isEqualTo(T0.plusSeconds(100));
        assertThat(documentos.searchByHash("x".repeat(64))).isEmpty();
        assertThat(
                        documentos
                                .searchByHash(ChannelDocument.hashDoToken("outro"))
                                .orElseThrow()
                                .official())
                .isTrue();

        assertThat(documentos.purgeExpired(T0.plusSeconds(50))).isEqualTo(1);
        assertThat(documentos.searchByHash(ChannelDocument.hashDoToken("outro"))).isEmpty();
        assertThat(documentos.searchByHash(hash)).isPresent();
    }
}
