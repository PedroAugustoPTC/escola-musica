package br.com.felipeblaft.relatorios.casodeuso.canal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.casodeuso.canal.EntregarAcoesPendentesCasoDeUso.Ciclo;
import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso.NovaAcao;
import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.EstadoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.telegram.VinculoTelegram;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.dubles.AcaoCanalRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.FilaTrabalhoEmMemoria;
import br.com.felipeblaft.relatorios.dubles.OrquestradorCanalFalso;
import br.com.felipeblaft.relatorios.dubles.RelogioFixo;
import br.com.felipeblaft.relatorios.dubles.VinculoTelegramRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.porta.LoteEntrega;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntregarAcoesPendentesCasoDeUsoTeste {

    private static final long CHAT_A = 100L;
    private static final long CHAT_B = 200L;

    private final RelogioFixo relogio = new RelogioFixo();
    private final AcaoCanalRepositorioEmMemoria repositorio = new AcaoCanalRepositorioEmMemoria();
    private final OrquestradorCanalFalso orquestrador = new OrquestradorCanalFalso();
    private final FilaTrabalhoEmMemoria fila = new FilaTrabalhoEmMemoria();
    private final VinculoTelegramRepositorioEmMemoria vinculos =
            new VinculoTelegramRepositorioEmMemoria();
    private final PoliticaRetentativa politica =
            new PoliticaRetentativa(
                    Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));
    private final GravarAcoesCanalCasoDeUso gravar =
            new GravarAcoesCanalCasoDeUso(repositorio, relogio, politica);
    private final ConfirmarExecucaoAcoesCanalCasoDeUso confirmar =
            new ConfirmarExecucaoAcoesCanalCasoDeUso(repositorio, fila, relogio, politica);
    private final EntregarAcoesPendentesCasoDeUso entregar =
            new EntregarAcoesPendentesCasoDeUso(
                    repositorio,
                    orquestrador,
                    fila,
                    vinculos,
                    relogio,
                    politica,
                    Duration.ofSeconds(60),
                    Duration.ofSeconds(60));

    private UUID professorVinculado(long chatId) {
        UUID professor = UUID.randomUUID();
        vinculos.salvar(VinculoTelegram.novo(professor, chatId + 1_000, chatId, relogio.agora()));
        return professor;
    }

    private void lote(String loteId, long chatId, UUID professor, String... ids) {
        gravar.executar(
                loteId,
                OrigemAcaoCanal.PROATIVA,
                null,
                java.util.Arrays.stream(ids)
                        .map(
                                id ->
                                        new NovaAcao(
                                                id,
                                                TipoAcaoCanal.MENSAGEM,
                                                chatId,
                                                professor,
                                                null,
                                                "{\"texto\":\"" + id + "\"}",
                                                null))
                        .toList());
    }

    private EstadoAcaoCanal estado(String id) {
        return repositorio.guardada(id).estado();
    }

    // ---- aceite e recusa

    @Test
    void aceiteMarcaTodoOLoteEntregueComIdsEstaveis() {
        UUID professor = professorVinculado(CHAT_A);
        lote("p:r:1", CHAT_A, professor, "p:r:1:0", "p:r:1:1");

        Ciclo ciclo = entregar.executar(10);

        assertThat(ciclo.lotesAceitos()).isEqualTo(1);
        assertThat(ciclo.lotesRecusados()).isZero();
        assertThat(estado("p:r:1:0")).isEqualTo(EstadoAcaoCanal.ENTREGUE);
        assertThat(estado("p:r:1:1")).isEqualTo(EstadoAcaoCanal.ENTREGUE);
        assertThat(orquestrador.recebidos).hasSize(1);
        LoteEntrega recebido = orquestrador.recebidos.get(0);
        assertThat(recebido.loteId()).isEqualTo("p:r:1");
        assertThat(recebido.origem()).isEqualTo(OrigemAcaoCanal.PROATIVA);
        assertThat(recebido.tentativa()).isEqualTo(1);
        assertThat(recebido.acoes()).extracting(LoteEntrega.Acao::acaoId).containsExactly("p:r:1:0", "p:r:1:1");
        assertThat(recebido.acoes().get(0).payload()).contains("p:r:1:0");
    }

    @Test
    void semNadaDevidoNaoChamaOOrquestrador() {
        assertThat(entregar.executar(10)).isEqualTo(new Ciclo(0, 0, 0, List.of()));
        assertThat(orquestrador.recebidos).isEmpty();
    }

    @Test
    void recusaMantemPendenteEReenviaComOsMesmosIdsEmIntervalosCrescentes() {
        UUID professor = professorVinculado(CHAT_A);
        lote("p:r:1", CHAT_A, professor, "p:r:1:0");
        orquestrador.recusarProximas("HTTP_503", 2);

        assertThat(entregar.executar(10).lotesRecusados()).isEqualTo(1);
        assertThat(estado("p:r:1:0")).isEqualTo(EstadoAcaoCanal.PENDENTE);
        assertThat(repositorio.guardada("p:r:1:0").proximaTentativaEm())
                .isEqualTo(relogio.agora().plusSeconds(30));
        // ainda cedo: nada é enviado
        relogio.avancar(Duration.ofSeconds(29));
        entregar.executar(10);
        assertThat(orquestrador.recebidos).hasSize(1);

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(entregar.executar(10).lotesRecusados()).isEqualTo(1);
        assertThat(repositorio.guardada("p:r:1:0").proximaTentativaEm())
                .isEqualTo(relogio.agora().plusSeconds(60));

        relogio.avancar(Duration.ofSeconds(60));
        assertThat(entregar.executar(10).lotesAceitos()).isEqualTo(1);
        assertThat(estado("p:r:1:0")).isEqualTo(EstadoAcaoCanal.ENTREGUE);
        assertThat(orquestrador.recebidos)
                .extracting(LoteEntrega::loteId)
                .containsOnly("p:r:1");
        assertThat(orquestrador.recebidos).extracting(LoteEntrega::tentativa).containsExactly(1, 2, 3);
    }

    // ---- prazo

    @Test
    void prazoVencidoAntesDaEntregaViraFalhaDefinitivaSemChamarOOrquestrador() {
        UUID professor = professorVinculado(CHAT_A);
        lote("p:r:1", CHAT_A, professor, "p:r:1:0");
        relogio.avancar(Duration.ofHours(24));

        Ciclo ciclo = entregar.executar(10);

        assertThat(ciclo.falhasDefinitivas()).containsExactly("p:r:1:0");
        assertThat(estado("p:r:1:0")).isEqualTo(EstadoAcaoCanal.FALHA_DEFINITIVA);
        assertThat(orquestrador.recebidos).isEmpty();
    }

    @Test
    void recusaQueCruzaOPrazoViraFalhaDefinitiva() {
        UUID professor = professorVinculado(CHAT_A);
        lote("p:r:1", CHAT_A, professor, "p:r:1:0");
        relogio.avancar(Duration.ofHours(24).minusSeconds(1));
        orquestrador.duranteEntrega = () -> relogio.avancar(Duration.ofSeconds(2));
        orquestrador.recusarProximas("TIMEOUT", 1);

        Ciclo ciclo = entregar.executar(10);

        assertThat(ciclo.falhasDefinitivas()).containsExactly("p:r:1:0");
        assertThat(estado("p:r:1:0")).isEqualTo(EstadoAcaoCanal.FALHA_DEFINITIVA);
    }

    // ---- vínculo

    @Test
    void professorSemVinculoTemAAcaoDescartadaSemEntrega() {
        UUID semVinculo = UUID.randomUUID();
        lote("p:r:1", CHAT_A, semVinculo, "p:r:1:0");

        Ciclo ciclo = entregar.executar(10);

        assertThat(ciclo.descartadas()).isEqualTo(1);
        assertThat(estado("p:r:1:0")).isEqualTo(EstadoAcaoCanal.DESCARTADA);
        assertThat(orquestrador.recebidos).isEmpty();
    }

    @Test
    void acaoSemProfessorNaoPassaPelaChecagemDeVinculo() {
        lote("p:r:1", CHAT_A, null, "p:r:1:0");
        assertThat(entregar.executar(10).lotesAceitos()).isEqualTo(1);
    }

    // ---- ordem por chat

    @Test
    void loteDoMesmoChatEsperaAConfirmacaoDoAnteriorMasOutroChatSegue() {
        UUID a = professorVinculado(CHAT_A);
        UUID b = professorVinculado(CHAT_B);
        lote("p:1", CHAT_A, a, "p:1:0");
        lote("p:2", CHAT_A, a, "p:2:0");
        lote("p:3", CHAT_B, b, "p:3:0");

        Ciclo primeiro = entregar.executar(10);

        assertThat(primeiro.lotesAceitos()).isEqualTo(2);
        assertThat(orquestrador.recebidos).extracting(LoteEntrega::loteId).containsExactly("p:1", "p:3");
        assertThat(estado("p:2:0")).isEqualTo(EstadoAcaoCanal.PENDENTE);

        confirmar.executar(
                List.of(new ConfirmarExecucaoAcoesCanalCasoDeUso.Resultado("p:1:0", ConfirmarExecucaoAcoesCanalCasoDeUso.Situacao.EXECUTADA, null, null)));
        assertThat(entregar.executar(10).lotesAceitos()).isEqualTo(1);
        assertThat(orquestrador.recebidos).extracting(LoteEntrega::loteId).containsExactly("p:1", "p:3", "p:2");
    }

    @Test
    void semConfirmacaoOPortaoLiberaDepoisDoTimeout() {
        UUID a = professorVinculado(CHAT_A);
        lote("p:1", CHAT_A, a, "p:1:0");
        lote("p:2", CHAT_A, a, "p:2:0");
        entregar.executar(10);
        assertThat(orquestrador.recebidos).hasSize(1);

        relogio.avancar(Duration.ofSeconds(59));
        entregar.executar(10);
        assertThat(orquestrador.recebidos).hasSize(1);

        relogio.avancar(Duration.ofSeconds(1));
        entregar.executar(10);
        assertThat(orquestrador.recebidos).extracting(LoteEntrega::loteId).containsExactly("p:1", "p:2");
    }

    @Test
    void respostaAUpdateNaoConfirmadaTambemSeguraOLoteProativoDoMesmoChat() {
        UUID a = professorVinculado(CHAT_A);
        gravar.executar(
                "u5",
                OrigemAcaoCanal.RESPOSTA_UPDATE,
                5L,
                List.of(new NovaAcao("u5-0", TipoAcaoCanal.MENSAGEM, CHAT_A, null, null, "{}", null)));
        lote("p:1", CHAT_A, a, "p:1:0");

        entregar.executar(10);
        assertThat(orquestrador.recebidos).isEmpty();

        relogio.avancar(Duration.ofSeconds(61));
        entregar.executar(10);
        assertThat(orquestrador.recebidos).hasSize(1);
    }

    @Test
    void loteAnteriorEmBackoffContinuaSegurandoOSeguinteDoMesmoChat() {
        UUID a = professorVinculado(CHAT_A);
        lote("p:1", CHAT_A, a, "p:1:0");
        orquestrador.recusarProximas("HTTP_500", 1);
        lote("p:2", CHAT_A, a, "p:2:0");

        entregar.executar(10);

        assertThat(orquestrador.recebidos).extracting(LoteEntrega::loteId).containsExactly("p:1");
        assertThat(estado("p:2:0")).isEqualTo(EstadoAcaoCanal.PENDENTE);
    }

    @Test
    void respeitaOMaximoDeLotesPorCiclo() {
        UUID a = professorVinculado(CHAT_A);
        UUID b = professorVinculado(CHAT_B);
        lote("p:1", CHAT_A, a, "p:1:0");
        lote("p:2", CHAT_B, b, "p:2:0");
        assertThat(entregar.executar(1).lotesAceitos()).isEqualTo(1);
        assertThat(orquestrador.recebidos).hasSize(1);
    }

    // ---- corrida e lease

    @Test
    void confirmacaoQueChegaDuranteOHttpPrevaleceSobreOAceite() {
        UUID a = professorVinculado(CHAT_A);
        lote("p:1", CHAT_A, a, "p:1:0");
        orquestrador.duranteEntrega =
                () ->
                        confirmar.executar(
                                List.of(
                                        new ConfirmarExecucaoAcoesCanalCasoDeUso.Resultado(
                                                "p:1:0",
                                                ConfirmarExecucaoAcoesCanalCasoDeUso.Situacao.EXECUTADA,
                                                null,
                                                null)));

        Ciclo ciclo = entregar.executar(10);

        assertThat(ciclo.lotesAceitos()).isEqualTo(1);
        assertThat(estado("p:1:0")).isEqualTo(EstadoAcaoCanal.EXECUTADA);
    }

    @Test
    void confirmacaoDuranteHttpRecusadoNaoViraFalhaDefinitivaNemVoltaAPendente() {
        UUID a = professorVinculado(CHAT_A);
        lote("p:1", CHAT_A, a, "p:1:0");
        orquestrador.recusarProximas("TIMEOUT", 1);
        orquestrador.duranteEntrega =
                () ->
                        confirmar.executar(
                                List.of(
                                        new ConfirmarExecucaoAcoesCanalCasoDeUso.Resultado(
                                                "p:1:0",
                                                ConfirmarExecucaoAcoesCanalCasoDeUso.Situacao.FALHOU,
                                                "ERRO_TELEGRAM",
                                                null)));
        Ciclo ciclo = entregar.executar(10);
        assertThat(ciclo.falhasDefinitivas()).isEmpty();
        assertThat(estado("p:1:0")).isEqualTo(EstadoAcaoCanal.FALHOU);
    }

    @Test
    void despachanteQueMorreDeixaOLoteReentregavelQuandoOLeaseVence() {
        UUID a = professorVinculado(CHAT_A);
        lote("p:1", CHAT_A, a, "p:1:0");
        // outro despachante reservou o lote e morreu antes de entregar
        assertThat(
                        repositorio.reservarProximoLoteDevido(
                                relogio.agora(), Duration.ofSeconds(60), relogio.agora().plusSeconds(60)))
                .hasSize(1);

        entregar.executar(10);
        assertThat(orquestrador.recebidos).isEmpty();

        relogio.avancar(Duration.ofSeconds(60));
        entregar.executar(10);
        assertThat(orquestrador.recebidos).extracting(LoteEntrega::loteId).containsExactly("p:1");
    }

    // ---- etapa

    @Test
    void etapaLevaOIdDaMensagemJaConhecidaDoTrabalho() {
        UUID a = professorVinculado(CHAT_A);
        TrabalhoProcessamento trabalho =
                TrabalhoProcessamento.novo(
                        TipoTrabalho.REGISTRO_AULA,
                        a,
                        UUID.randomUUID(),
                        null,
                        null,
                        Canal.TELEGRAM,
                        Meio.AUDIO,
                        EtapaTrabalho.ENVIANDO,
                        relogio.agora());
        trabalho.definirParametro(TrabalhoProcessamento.MENSAGEM_ETAPA, "555");
        fila.enfileirar(trabalho);
        TrabalhoProcessamento semMensagem =
                TrabalhoProcessamento.novo(
                        TipoTrabalho.REGISTRO_AULA,
                        a,
                        UUID.randomUUID(),
                        null,
                        null,
                        Canal.TELEGRAM,
                        Meio.AUDIO,
                        EtapaTrabalho.ENVIANDO,
                        relogio.agora());
        fila.enfileirar(semMensagem);
        TrabalhoProcessamento invalido =
                TrabalhoProcessamento.novo(
                        TipoTrabalho.REGISTRO_AULA,
                        a,
                        UUID.randomUUID(),
                        null,
                        null,
                        Canal.TELEGRAM,
                        Meio.AUDIO,
                        EtapaTrabalho.ENVIANDO,
                        relogio.agora());
        invalido.definirParametro(TrabalhoProcessamento.MENSAGEM_ETAPA, "nao-e-numero");
        fila.enfileirar(invalido);
        gravar.executar(
                "p:e",
                OrigemAcaoCanal.PROATIVA,
                null,
                List.of(
                        new NovaAcao("p:e:0", TipoAcaoCanal.MENSAGEM_ETAPA, CHAT_A, a, trabalho.id(), "{}", null),
                        new NovaAcao("p:e:1", TipoAcaoCanal.MENSAGEM_ETAPA, CHAT_A, a, semMensagem.id(), "{}", null),
                        new NovaAcao("p:e:2", TipoAcaoCanal.MENSAGEM_ETAPA, CHAT_A, a, invalido.id(), "{}", null),
                        new NovaAcao("p:e:3", TipoAcaoCanal.MENSAGEM_ETAPA, CHAT_A, a, UUID.randomUUID(), "{}", null),
                        new NovaAcao("p:e:4", TipoAcaoCanal.MENSAGEM, CHAT_A, a, trabalho.id(), "{}", null)));

        entregar.executar(10);

        List<LoteEntrega.Acao> acoes = orquestrador.recebidos.get(0).acoes();
        assertThat(acoes).extracting(LoteEntrega.Acao::editarMensagemId).containsExactly(555L, null, null, null, null);
        assertThat(acoes.get(0).trabalhoId()).isEqualTo(trabalho.id());
    }

    @Test
    void falhaDefinitivaReportadaSoUmaVezPorAcao() {
        UUID a = professorVinculado(CHAT_A);
        lote("p:1", CHAT_A, a, "p:1:0");
        relogio.avancar(Duration.ofHours(24));
        assertThat(entregar.executar(10).falhasDefinitivas()).hasSize(1);
        assertThat(entregar.executar(10).falhasDefinitivas()).isEmpty();
        AcaoCanal acao = repositorio.guardada("p:1:0");
        assertThat(acao.codigoMotivo()).isEqualTo(AcaoCanal.CODIGO_PRAZO_VENCIDO);
    }
}
