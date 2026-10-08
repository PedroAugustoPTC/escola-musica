package br.com.felipeblaft.relatorios.saida.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.DocumentoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.EstadoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import br.com.felipeblaft.relatorios.porta.AcaoCanalRepositorio;
import br.com.felipeblaft.relatorios.porta.DocumentoCanalRepositorio;
import br.com.felipeblaft.relatorios.suporte.IntegracaoBase;
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

    private final PoliticaRetentativa politica =
            new PoliticaRetentativa(
                    Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));

    @Autowired AcaoCanalRepositorio acoes;
    @Autowired DocumentoCanalRepositorio documentos;

    private AcaoCanal proativa(String loteId, int pos, long chatId) {
        return AcaoCanal.nova(
                loteId + ":" + pos,
                loteId,
                OrigemAcaoCanal.PROATIVA,
                null,
                chatId,
                UUID.randomUUID(),
                null,
                TipoAcaoCanal.MENSAGEM,
                "{\"texto\":\"" + loteId + pos + "\"}",
                "{\"texto\":\"alt\"}",
                T0,
                politica);
    }

    private List<AcaoCanal> lote(String loteId, long chatId, int quantidade) {
        List<AcaoCanal> lote = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            lote.add(proativa(loteId, i, chatId));
        }
        return lote;
    }

    @Test
    void gravacaoEIdempotentePorIdENuncaSobrescreve() {
        AcaoCanal original = proativa("p:a", 0, 1);
        assertThat(acoes.gravarLote(List.of(original))).isEqualTo(1);
        AcaoCanal outra =
                AcaoCanal.nova(
                        "p:a:0",
                        "p:a",
                        OrigemAcaoCanal.PROATIVA,
                        null,
                        1,
                        UUID.randomUUID(),
                        null,
                        TipoAcaoCanal.MENSAGEM,
                        "{\"texto\":\"diferente\"}",
                        null,
                        T0.plusSeconds(5),
                        politica);
        assertThat(acoes.gravarLote(List.of(outra, proativa("p:a", 1, 1)))).isEqualTo(1);

        AcaoCanal lida = acoes.buscarPorId("p:a:0").orElseThrow();
        assertThat(lida.payload()).contains("p:a0");
        assertThat(lida.compensacao()).contains("alt");
        assertThat(lida.estado()).isEqualTo(EstadoAcaoCanal.PENDENTE);
        assertThat(lida.criadoEm()).isEqualTo(T0);
        assertThat(lida.versao()).isZero();
        assertThat(acoes.buscarPorId("inexistente")).isEmpty();
    }

    @Test
    void respostasAUpdateSaoLidasPorUpdateNaOrdemDeCriacao() {
        List<AcaoCanal> respostas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            respostas.add(
                    AcaoCanal.nova(
                            "u7-" + i,
                            "u7",
                            OrigemAcaoCanal.RESPOSTA_UPDATE,
                            7L,
                            55,
                            null,
                            null,
                            TipoAcaoCanal.MENSAGEM,
                            "{}",
                            null,
                            T0,
                            politica));
        }
        acoes.gravarLote(respostas);
        assertThat(acoes.buscarPorUpdate(7L))
                .extracting(AcaoCanal::id)
                .containsExactly("u7-0", "u7-1", "u7-2");
        assertThat(acoes.buscarPorLote("u7"))
                .extracting(AcaoCanal::id)
                .containsExactly("u7-0", "u7-1", "u7-2");
        assertThat(acoes.buscarPorUpdate(8L)).isEmpty();
        assertThat(acoes.buscarPorUpdate(7L).get(0).estado()).isEqualTo(EstadoAcaoCanal.ENTREGUE);
        assertThat(acoes.buscarPorUpdate(7L).get(0).entregueEm()).isEqualTo(T0);
    }

    @Test
    void reservaDevolveOLoteInteiroEOLeaseImpedeASegundaReserva() {
        acoes.gravarLote(lote("p:a", 1, 3));
        Instant lease = T0.plusSeconds(60);

        List<AcaoCanal> reservado = acoes.reservarProximoLoteDevido(T0, CONFIRMACAO, lease);

        assertThat(reservado).extracting(AcaoCanal::id).containsExactly("p:a:0", "p:a:1", "p:a:2");
        assertThat(reservado).allSatisfy(a -> assertThat(a.versao()).isEqualTo(1));
        assertThat(reservado).allSatisfy(a -> assertThat(a.proximaTentativaEm()).isEqualTo(lease));
        assertThat(
                        acoes.reservarProximoLoteDevido(
                                T0.plusSeconds(59), CONFIRMACAO, lease.plusSeconds(60)))
                .isEmpty();
        // o lease venceu: o mesmo lote volta, com os mesmos ids
        assertThat(
                        acoes.reservarProximoLoteDevido(
                                T0.plusSeconds(60), CONFIRMACAO, lease.plusSeconds(60)))
                .extracting(AcaoCanal::id)
                .containsExactly("p:a:0", "p:a:1", "p:a:2");
    }

    @Test
    void naoReservaAcaoAindaNaoDevida() {
        acoes.gravarLote(lote("p:a", 1, 1));
        assertThat(
                        acoes.reservarProximoLoteDevido(
                                T0.minusSeconds(1), CONFIRMACAO, T0.plusSeconds(60)))
                .isEmpty();
    }

    @Test
    void doisConsumidoresConcorrentesNuncaReservamOMesmoLote() throws Exception {
        int lotes = 6;
        for (int i = 0; i < lotes; i++) {
            acoes.gravarLote(lote("p:l" + i, 100 + i, 3));
        }
        int consumidores = 8;
        ExecutorService pool = Executors.newFixedThreadPool(consumidores);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<List<List<AcaoCanal>>>> futuros = new ArrayList<>();
        for (int c = 0; c < consumidores; c++) {
            futuros.add(
                    pool.submit(
                            () -> {
                                largada.await();
                                List<List<AcaoCanal>> meus = new ArrayList<>();
                                for (int i = 0; i < lotes; i++) {
                                    List<AcaoCanal> reservado =
                                            acoes.reservarProximoLoteDevido(
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
        for (Future<List<List<AcaoCanal>>> futuro : futuros) {
            for (List<AcaoCanal> reservado : futuro.get()) {
                lotesReservados++;
                assertThat(reservado).hasSize(3);
                assertThat(reservado.stream().map(AcaoCanal::loteId).distinct()).hasSize(1);
                for (AcaoCanal acao : reservado) {
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
        acoes.gravarLote(lote("p:a", 1, 1));
        acoes.gravarLote(lote("p:b", 1, 1));
        acoes.gravarLote(lote("p:c", 2, 1));

        List<AcaoCanal> primeiro =
                acoes.reservarProximoLoteDevido(T0, CONFIRMACAO, T0.plusSeconds(60));
        assertThat(primeiro).extracting(AcaoCanal::loteId).containsExactly("p:a");
        // o chat 1 está atrás do lote A (ainda pendente); o chat 2 é independente
        List<AcaoCanal> segundo =
                acoes.reservarProximoLoteDevido(T0, CONFIRMACAO, T0.plusSeconds(60));
        assertThat(segundo).extracting(AcaoCanal::loteId).containsExactly("p:c");
        assertThat(acoes.reservarProximoLoteDevido(T0, CONFIRMACAO, T0.plusSeconds(60))).isEmpty();

        // A é aceito (ENTREGUE) mas não confirmado: B espera até o timeout do portão
        AcaoCanal a = primeiro.get(0);
        a.marcarEntregue(T0);
        assertThat(acoes.atualizarSeVersao(a, a.versao())).isTrue();
        Instant depoisDoLease = T0.plusSeconds(61);
        assertThat(acoes.reservarProximoLoteDevido(T0.plusSeconds(30), CONFIRMACAO, depoisDoLease))
                .isEmpty();
        assertThat(
                        acoes.reservarProximoLoteDevido(
                                depoisDoLease, CONFIRMACAO, depoisDoLease.plusSeconds(60)))
                .extracting(AcaoCanal::loteId)
                .containsExactly("p:b");
    }

    @Test
    void confirmacaoLiberaOProximoLoteDoChatAntesDoTimeout() {
        acoes.gravarLote(lote("p:a", 1, 1));
        acoes.gravarLote(lote("p:b", 1, 1));
        AcaoCanal a = acoes.reservarProximoLoteDevido(T0, CONFIRMACAO, T0.plusSeconds(60)).get(0);
        a.marcarEntregue(T0);
        acoes.atualizarSeVersao(a, a.versao());
        a = acoes.buscarPorId(a.id()).orElseThrow();
        a.confirmarExecutada(T0.plusSeconds(2));
        assertThat(acoes.atualizarSeVersao(a, a.versao())).isTrue();

        assertThat(
                        acoes.reservarProximoLoteDevido(
                                T0.plusSeconds(3), CONFIRMACAO, T0.plusSeconds(63)))
                .extracting(AcaoCanal::loteId)
                .containsExactly("p:b");
    }

    @Test
    void atualizacaoCondicionalAvancaAVersaoESoAceitaAVersaoLida() {
        acoes.gravarLote(lote("p:a", 1, 1));
        AcaoCanal lida = acoes.buscarPorId("p:a:0").orElseThrow();
        AcaoCanal concorrente = acoes.buscarPorId("p:a:0").orElseThrow();

        lida.confirmarExecutada(T0);
        assertThat(acoes.atualizarSeVersao(lida, 0)).isTrue();
        assertThat(lida.versao()).isEqualTo(1);

        concorrente.marcarEntregue(T0);
        assertThat(acoes.atualizarSeVersao(concorrente, 0)).isFalse();
        assertThat(concorrente.versao()).isZero();

        AcaoCanal gravada = acoes.buscarPorId("p:a:0").orElseThrow();
        assertThat(gravada.estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
        assertThat(gravada.versao()).isEqualTo(1);
        assertThat(gravada.finalizadoEm()).isEqualTo(T0);
    }

    @Test
    void releituraSempreVeOEstadoDoBanco() {
        acoes.gravarLote(lote("p:a", 1, 1));
        jdbc.update(
                "update acao_canal set estado = 'EXECUTADA', versao = versao + 1 where id = 'p:a:0'");
        assertThat(acoes.buscarPorId("p:a:0").orElseThrow().estado())
                .isEqualTo(EstadoAcaoCanal.EXECUTADA);
        jdbc.update(
                "update acao_canal set estado = 'FALHOU', versao = versao + 1 where id = 'p:a:0'");
        assertThat(acoes.buscarPorId("p:a:0").orElseThrow().estado())
                .isEqualTo(EstadoAcaoCanal.FALHOU);
    }

    @Test
    void falhaDefinitivaEDescarteSaoGravadosComMotivo() {
        acoes.gravarLote(lote("p:a", 1, 2));
        AcaoCanal a = acoes.buscarPorId("p:a:0").orElseThrow();
        a.descartar(T0.plusSeconds(1));
        acoes.atualizarSeVersao(a, 0);
        AcaoCanal b = acoes.buscarPorId("p:a:1").orElseThrow();
        b.expirar(T0.plus(Duration.ofHours(25)));
        acoes.atualizarSeVersao(b, 0);

        assertThat(acoes.buscarPorId("p:a:0").orElseThrow().codigoMotivo())
                .isEqualTo("VINCULO_DESFEITO");
        AcaoCanal falha = acoes.buscarPorId("p:a:1").orElseThrow();
        assertThat(falha.estado()).isEqualTo(EstadoAcaoCanal.FALHA_DEFINITIVA);
        assertThat(falha.codigoMotivo()).isEqualTo("PRAZO_VENCIDO");
        assertThat(falha.proximaTentativaEm()).isNull();
    }

    @Test
    void expurgoZeraOConteudoDepoisDaRetencaoERemoveORegistroDepois() {
        acoes.gravarLote(lote("p:a", 1, 2));
        AcaoCanal finalizada = acoes.buscarPorId("p:a:0").orElseThrow();
        finalizada.confirmarExecutada(T0);
        acoes.atualizarSeVersao(finalizada, 0);

        AcaoCanalRepositorio.Expurgo nada = acoes.expurgar(T0.minusSeconds(1), T0.minusSeconds(1));
        assertThat(nada).isEqualTo(new AcaoCanalRepositorio.Expurgo(0, 0));

        AcaoCanalRepositorio.Expurgo zerou = acoes.expurgar(T0.plusSeconds(1), T0.minusSeconds(1));
        assertThat(zerou.payloadsZerados()).isEqualTo(1);
        AcaoCanal semConteudo = acoes.buscarPorId("p:a:0").orElseThrow();
        assertThat(semConteudo.payload()).isNull();
        assertThat(semConteudo.compensacao()).isNull();
        // a pendente não é tocada
        assertThat(acoes.buscarPorId("p:a:1").orElseThrow().payload()).isNotNull();

        AcaoCanalRepositorio.Expurgo removeu = acoes.expurgar(T0.plusSeconds(1), T0.plusSeconds(1));
        assertThat(removeu.registrosRemovidos()).isEqualTo(1);
        assertThat(acoes.buscarPorId("p:a:0")).isEmpty();
        assertThat(acoes.buscarPorId("p:a:1")).isPresent();
    }

    @Test
    void pendenciasContamSoAsPendentesEInformamAMaisAntiga() {
        assertThat(acoes.pendencias(T0)).isEqualTo(new AcaoCanalRepositorio.Pendencias(0, null, 0));
        acoes.gravarLote(lote("p:a", 1, 3));
        AcaoCanal f = acoes.buscarPorId("p:a:0").orElseThrow();
        f.confirmarExecutada(T0);
        acoes.atualizarSeVersao(f, 0);
        AcaoCanal e = acoes.buscarPorId("p:a:1").orElseThrow();
        e.marcarEntregue(T0);
        acoes.atualizarSeVersao(e, 0);
        AcaoCanalRepositorio.Pendencias pendencias = acoes.pendencias(T0.plusSeconds(60));
        assertThat(pendencias.quantidade()).isEqualTo(1);
        assertThat(pendencias.maisAntigaCriadaEm()).isEqualTo(T0);
        assertThat(pendencias.semConfirmacao()).isEqualTo(1);
        assertThat(acoes.pendencias(T0).semConfirmacao()).isZero();
    }

    @Test
    void documentoCanalEmiteBuscaPorHashEExpurgaVencidos() {
        UUID professor = UUID.randomUUID();
        UUID relatorio = UUID.randomUUID();
        String hash = DocumentoCanal.hashDoToken("token-de-teste");
        documentos.emitir(
                new DocumentoCanal(
                        hash,
                        professor,
                        TipoRelatorio.SEMESTRAL,
                        relatorio,
                        3,
                        "s-v3.pdf",
                        T0,
                        T0.plusSeconds(100)));
        documentos.emitir(
                new DocumentoCanal(
                        DocumentoCanal.hashDoToken("outro"),
                        professor,
                        TipoRelatorio.AULA,
                        relatorio,
                        null,
                        "a.pdf",
                        T0,
                        T0.plusSeconds(10)));

        DocumentoCanal lido = documentos.buscarPorHash(hash).orElseThrow();
        assertThat(lido.professorId()).isEqualTo(professor);
        assertThat(lido.relatorioTipo()).isEqualTo(TipoRelatorio.SEMESTRAL);
        assertThat(lido.versao()).isEqualTo(3);
        assertThat(lido.expiraEm()).isEqualTo(T0.plusSeconds(100));
        assertThat(documentos.buscarPorHash("x".repeat(64))).isEmpty();
        assertThat(
                        documentos
                                .buscarPorHash(DocumentoCanal.hashDoToken("outro"))
                                .orElseThrow()
                                .oficial())
                .isTrue();

        assertThat(documentos.expurgarVencidos(T0.plusSeconds(50))).isEqualTo(1);
        assertThat(documentos.buscarPorHash(DocumentoCanal.hashDoToken("outro"))).isEmpty();
        assertThat(documentos.buscarPorHash(hash)).isPresent();
    }
}
