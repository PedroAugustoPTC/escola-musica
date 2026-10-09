package br.com.felipeblaft.relatorios.dominio.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcaoCanalTeste {

    private static final Instant AGORA = Instant.parse("2026-10-02T12:00:00Z");
    private static final UUID PROFESSOR = UUID.randomUUID();
    private static final UUID TRABALHO = UUID.randomUUID();

    private final PoliticaRetentativa politica =
            new PoliticaRetentativa(
                    Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));

    private AcaoCanal proativa(TipoAcaoCanal tipo, String compensacao) {
        return AcaoCanal.nova(
                "p:resultado:x:0",
                "p:resultado:x",
                OrigemAcaoCanal.PROATIVA,
                null,
                10L,
                PROFESSOR,
                TRABALHO,
                tipo,
                "{\"texto\":\"a\"}",
                compensacao,
                AGORA,
                politica);
    }

    private AcaoCanal resposta() {
        return AcaoCanal.nova(
                "u1-0",
                "u1",
                OrigemAcaoCanal.RESPOSTA_UPDATE,
                1L,
                10L,
                null,
                null,
                TipoAcaoCanal.MENSAGEM,
                "{}",
                null,
                AGORA,
                politica);
    }

    @Test
    void respostaAUpdateNasceEntregue() {
        AcaoCanal acao = resposta();
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.ENTREGUE);
        assertThat(acao.entregueEm()).isEqualTo(AGORA);
        assertThat(acao.proximaTentativaEm()).isNull();
        assertThat(acao.tentativas()).isZero();
        assertThat(acao.prazoEm()).isEqualTo(AGORA.plus(Duration.ofHours(24)));
        assertThat(acao.canal())
                .isEqualTo(br.com.felipeblaft.relatorios.dominio.comum.Canal.TELEGRAM);
    }

    @Test
    void respostaAUpdateExigeUpdateId() {
        assertThatThrownBy(
                        () ->
                                AcaoCanal.nova(
                                        "u1-0",
                                        "u1",
                                        OrigemAcaoCanal.RESPOSTA_UPDATE,
                                        null,
                                        10L,
                                        null,
                                        null,
                                        TipoAcaoCanal.MENSAGEM,
                                        "{}",
                                        null,
                                        AGORA,
                                        politica))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void proativaNasceDevidaEPendente() {
        AcaoCanal acao = proativa(TipoAcaoCanal.MENSAGEM, null);
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.PENDENTE);
        assertThat(acao.proximaTentativaEm()).isEqualTo(AGORA);
        assertThat(acao.entregueEm()).isNull();
        assertThat(acao.professorId()).isEqualTo(PROFESSOR);
        assertThat(acao.trabalhoId()).isEqualTo(TRABALHO);
        assertThat(acao.versao()).isZero();
    }

    @Test
    void falhaDeEntregaReagendaSegundoAPolitica() {
        AcaoCanal acao = proativa(TipoAcaoCanal.MENSAGEM, null);
        acao.registrarFalhaEntrega(AGORA, politica);
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.PENDENTE);
        assertThat(acao.tentativas()).isEqualTo(1);
        assertThat(acao.proximaTentativaEm()).isEqualTo(AGORA.plusSeconds(30));
        acao.registrarFalhaEntrega(AGORA.plusSeconds(30), politica);
        assertThat(acao.tentativas()).isEqualTo(2);
        assertThat(acao.proximaTentativaEm()).isEqualTo(AGORA.plusSeconds(30 + 60));
    }

    @Test
    void falhaDeEntregaAposOPrazoEFalhaDefinitiva() {
        AcaoCanal acao = proativa(TipoAcaoCanal.MENSAGEM, null);
        Instant depoisDoPrazo = AGORA.plus(Duration.ofHours(24));
        acao.registrarFalhaEntrega(depoisDoPrazo, politica);
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.FALHA_DEFINITIVA);
        assertThat(acao.codigoMotivo()).isEqualTo(AcaoCanal.CODIGO_PRAZO_VENCIDO);
        assertThat(acao.finalizadoEm()).isEqualTo(depoisDoPrazo);
        assertThat(acao.proximaTentativaEm()).isNull();
    }

    @Test
    void falhaDeEntregaNaoMexeEmAcaoQueNaoEstaPendente() {
        AcaoCanal acao = resposta();
        acao.registrarFalhaEntrega(AGORA, politica);
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.ENTREGUE);
        assertThat(acao.tentativas()).isZero();
    }

    @Test
    void reservaEmpurraAProximaTentativaSoEmPendente() {
        AcaoCanal acao = proativa(TipoAcaoCanal.MENSAGEM, null);
        acao.reservarAte(AGORA.plusSeconds(60));
        assertThat(acao.proximaTentativaEm()).isEqualTo(AGORA.plusSeconds(60));
        acao.marcarEntregue(AGORA);
        acao.reservarAte(AGORA.plusSeconds(999));
        assertThat(acao.proximaTentativaEm()).isNull();
    }

    @Test
    void aceiteMarcaEntregueEConta() {
        AcaoCanal acao = proativa(TipoAcaoCanal.MENSAGEM, null);
        acao.marcarEntregue(AGORA.plusSeconds(5));
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.ENTREGUE);
        assertThat(acao.entregueEm()).isEqualTo(AGORA.plusSeconds(5));
        assertThat(acao.tentativas()).isEqualTo(1);
        assertThat(acao.proximaTentativaEm()).isNull();
    }

    @Test
    void aceiteNaoMexeEmAcaoJaConfirmada() {
        AcaoCanal acao = proativa(TipoAcaoCanal.MENSAGEM, null);
        acao.confirmarExecutada(AGORA);
        acao.marcarEntregue(AGORA.plusSeconds(1));
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
        assertThat(acao.entregueEm()).isNull();
    }

    @Test
    void confirmacaoExecutadaEIdempotente() {
        AcaoCanal acao = resposta();
        assertThat(acao.confirmarExecutada(AGORA)).isEqualTo(ResultadoConfirmacao.APLICADA);
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
        assertThat(acao.finalizadoEm()).isEqualTo(AGORA);
        assertThat(acao.confirmarExecutada(AGORA.plusSeconds(9)))
                .isEqualTo(ResultadoConfirmacao.REPETIDA);
        assertThat(acao.finalizadoEm()).isEqualTo(AGORA);
    }

    @Test
    void confirmacaoDivergenteDeEstadoFinalEIgnorada() {
        AcaoCanal acao = resposta();
        acao.confirmarExecutada(AGORA);
        assertThat(acao.confirmarFalha("ERRO_TELEGRAM", AGORA))
                .isEqualTo(ResultadoConfirmacao.DIVERGENTE);
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
        assertThat(acao.codigoMotivo()).isNull();
    }

    @Test
    void confirmacaoDeFalhaEIdempotenteEDivergenteQuandoOCodigoMuda() {
        AcaoCanal acao = resposta();
        assertThat(acao.confirmarFalha("CHAT_INDISPONIVEL", AGORA))
                .isEqualTo(ResultadoConfirmacao.APLICADA);
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.FALHOU);
        assertThat(acao.codigoMotivo()).isEqualTo("CHAT_INDISPONIVEL");
        assertThat(acao.confirmarFalha("CHAT_INDISPONIVEL", AGORA.plusSeconds(1)))
                .isEqualTo(ResultadoConfirmacao.REPETIDA);
        assertThat(acao.confirmarFalha("ERRO_TELEGRAM", AGORA.plusSeconds(1)))
                .isEqualTo(ResultadoConfirmacao.DIVERGENTE);
        assertThat(acao.confirmarExecutada(AGORA.plusSeconds(1)))
                .isEqualTo(ResultadoConfirmacao.DIVERGENTE);
        assertThat(acao.codigoMotivo()).isEqualTo("CHAT_INDISPONIVEL");
    }

    @Test
    void confirmacaoChegandoAntesDoAceiteVaiDePendenteAoFinal() {
        AcaoCanal acao = proativa(TipoAcaoCanal.MENSAGEM, null);
        assertThat(acao.confirmarFalha("LIMITE_DE_TAXA", AGORA))
                .isEqualTo(ResultadoConfirmacao.APLICADA);
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.FALHOU);
        assertThat(acao.proximaTentativaEm()).isNull();
    }

    @Test
    void confirmacaoDeAcaoFinalizadaPorOutroMotivoEDivergente() {
        AcaoCanal descartada = proativa(TipoAcaoCanal.MENSAGEM, null);
        descartada.descartar(AGORA);
        assertThat(descartada.confirmarExecutada(AGORA)).isEqualTo(ResultadoConfirmacao.DIVERGENTE);
        assertThat(descartada.confirmarFalha("X", AGORA))
                .isEqualTo(ResultadoConfirmacao.DIVERGENTE);
        assertThat(descartada.estado()).isEqualTo(EstadoAcaoCanal.DESCARTADA);
    }

    @Test
    void confirmarFalhaExigeCodigo() {
        assertThatThrownBy(() -> resposta().confirmarFalha(null, AGORA))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void descarteSoVale_emPendente() {
        AcaoCanal pendente = proativa(TipoAcaoCanal.MENSAGEM, null);
        pendente.descartar(AGORA);
        assertThat(pendente.estado()).isEqualTo(EstadoAcaoCanal.DESCARTADA);
        assertThat(pendente.codigoMotivo()).isEqualTo(AcaoCanal.CODIGO_VINCULO_DESFEITO);

        AcaoCanal entregue = resposta();
        entregue.descartar(AGORA);
        assertThat(entregue.estado()).isEqualTo(EstadoAcaoCanal.ENTREGUE);
    }

    @Test
    void expiraSoQuandoNaoFinalizadaEOPrazoVenceu() {
        AcaoCanal acao = proativa(TipoAcaoCanal.MENSAGEM, null);
        acao.expirar(AGORA.plusSeconds(10));
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.PENDENTE);
        assertThat(acao.vencida(AGORA.plus(Duration.ofHours(24)))).isTrue();
        acao.expirar(AGORA.plus(Duration.ofHours(24)));
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.FALHA_DEFINITIVA);
        assertThat(acao.vencida(AGORA.plus(Duration.ofHours(48)))).isFalse();
        acao.expirar(AGORA.plus(Duration.ofHours(48)));
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.FALHA_DEFINITIVA);
    }

    @Test
    void falhaDeEntregaNuncaAgendaDepoisDoPrazo() {
        AcaoCanal acao = proativa(TipoAcaoCanal.MENSAGEM, null);
        Instant prazo = AGORA.plus(Duration.ofHours(24));
        acao.registrarFalhaEntrega(prazo.minusSeconds(10), politica);
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.PENDENTE);
        assertThat(acao.proximaTentativaEm()).isEqualTo(prazo);

        acao.expirar(prazo);
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.FALHA_DEFINITIVA);
        assertThat(acao.codigoMotivo()).isEqualTo(AcaoCanal.CODIGO_PRAZO_VENCIDO);
        assertThat(acao.finalizadoEm()).isEqualTo(prazo);
    }

    @Test
    void falhaDeEntregaAntesDoPrazoMantemAEsperaDaPolitica() {
        AcaoCanal acao = proativa(TipoAcaoCanal.MENSAGEM, null);
        acao.registrarFalhaEntrega(AGORA, politica);
        assertThat(acao.proximaTentativaEm()).isEqualTo(AGORA.plusSeconds(30));
    }

    @Test
    void entregueSemConfirmacaoExpiraNoPrazoComCodigoProprio() {
        AcaoCanal acao = resposta();
        Instant prazo = AGORA.plus(Duration.ofHours(24));
        assertThat(acao.expirarSemConfirmacao(prazo.minusSeconds(1))).isFalse();
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.ENTREGUE);

        assertThat(acao.expirarSemConfirmacao(prazo)).isTrue();
        assertThat(acao.estado()).isEqualTo(EstadoAcaoCanal.FALHA_DEFINITIVA);
        assertThat(acao.codigoMotivo()).isEqualTo(AcaoCanal.CODIGO_SEM_CONFIRMACAO);
        assertThat(acao.finalizadoEm()).isEqualTo(prazo);
        assertThat(acao.proximaTentativaEm()).isNull();

        acao.zerarPayload();
        assertThat(acao.payload()).isNull();
        assertThat(acao.compensacao()).isNull();
    }

    @Test
    void expirarSemConfirmacaoSoAgeEmEntregue() {
        Instant muitoDepois = AGORA.plus(Duration.ofDays(3));
        AcaoCanal pendente = proativa(TipoAcaoCanal.MENSAGEM, null);
        assertThat(pendente.expirarSemConfirmacao(muitoDepois)).isFalse();
        assertThat(pendente.estado()).isEqualTo(EstadoAcaoCanal.PENDENTE);

        AcaoCanal executada = resposta();
        executada.confirmarExecutada(AGORA);
        assertThat(executada.expirarSemConfirmacao(muitoDepois)).isFalse();
        assertThat(executada.estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
        assertThat(executada.codigoMotivo()).isNull();
    }

    @Test
    void confirmacaoTardiaDeAcaoExpiradaEDivergente() {
        AcaoCanal acao = resposta();
        acao.expirarSemConfirmacao(AGORA.plus(Duration.ofHours(24)));
        assertThat(acao.confirmarExecutada(AGORA.plus(Duration.ofHours(25))))
                .isEqualTo(ResultadoConfirmacao.DIVERGENTE);
        assertThat(acao.codigoMotivo()).isEqualTo(AcaoCanal.CODIGO_SEM_CONFIRMACAO);
    }

    @Test
    void compensacaoDeEdicaoViraMensagemNoMesmoChat() {
        AcaoCanal edicao = proativa(TipoAcaoCanal.EDICAO, "{\"texto\":\"alternativa\"}");
        assertThat(edicao.temCompensacao()).isTrue();
        assertThat(edicao.idDaCompensacao()).isEqualTo("p:resultado:x:0-c");
        AcaoCanal compensacao = edicao.criarCompensacao(AGORA, politica);
        assertThat(compensacao.id()).isEqualTo("p:resultado:x:0-c");
        assertThat(compensacao.loteId()).isEqualTo("c:p:resultado:x:0");
        assertThat(compensacao.origem()).isEqualTo(OrigemAcaoCanal.COMPENSACAO);
        assertThat(compensacao.tipo()).isEqualTo(TipoAcaoCanal.MENSAGEM);
        assertThat(compensacao.payload()).isEqualTo("{\"texto\":\"alternativa\"}");
        assertThat(compensacao.chatId()).isEqualTo(10L);
        assertThat(compensacao.professorId()).isEqualTo(PROFESSOR);
        assertThat(compensacao.estado()).isEqualTo(EstadoAcaoCanal.PENDENTE);
        assertThat(compensacao.temCompensacao()).isFalse();
    }

    @Test
    void documentoCompensaSoPorDocumentoIndisponivelEEdicaoSoPorMensagemNaoEditavel() {
        AcaoCanal documento = proativa(TipoAcaoCanal.DOCUMENTO, "{\"texto\":\"aviso\"}");
        assertThat(documento.compensaPor("DOCUMENTO_INDISPONIVEL")).isTrue();
        assertThat(documento.compensaPor("MENSAGEM_NAO_EDITAVEL")).isFalse();
        assertThat(documento.compensaPor("ERRO_TELEGRAM")).isFalse();

        AcaoCanal edicao = proativa(TipoAcaoCanal.EDICAO, "{\"texto\":\"x\"}");
        assertThat(edicao.compensaPor("MENSAGEM_NAO_EDITAVEL")).isTrue();
        assertThat(edicao.compensaPor("DOCUMENTO_INDISPONIVEL")).isFalse();

        assertThat(proativa(TipoAcaoCanal.DOCUMENTO, null).compensaPor("DOCUMENTO_INDISPONIVEL"))
                .isFalse();
        assertThat(
                        documento
                                .criarCompensacao(AGORA, politica)
                                .compensaPor("DOCUMENTO_INDISPONIVEL"))
                .isFalse();
    }

    @Test
    void compensacaoDeDocumentoViraMensagemNoMesmoChatSemTrazerODocumento() {
        AcaoCanal documento =
                AcaoCanal.nova(
                        "p:resultado:x:3",
                        "p:resultado:x",
                        OrigemAcaoCanal.PROATIVA,
                        null,
                        10L,
                        PROFESSOR,
                        TRABALHO,
                        TipoAcaoCanal.DOCUMENTO,
                        "{\"documentoId\":\"abc\",\"nomeArquivo\":\"a.pdf\"}",
                        "{\"tipo\":\"MENSAGEM\",\"chatId\":10,\"texto\":\"Aviso\"}",
                        AGORA,
                        politica);
        AcaoCanal compensacao = documento.criarCompensacao(AGORA, politica);
        assertThat(compensacao.id()).isEqualTo("p:resultado:x:3-c");
        assertThat(compensacao.loteId()).isEqualTo("c:p:resultado:x:3");
        assertThat(compensacao.origem()).isEqualTo(OrigemAcaoCanal.COMPENSACAO);
        assertThat(compensacao.tipo()).isEqualTo(TipoAcaoCanal.MENSAGEM);
        assertThat(compensacao.payload()).doesNotContain("documentoId", "a.pdf");
        assertThat(compensacao.chatId()).isEqualTo(10L);
        assertThat(compensacao.professorId()).isEqualTo(PROFESSOR);
        assertThat(compensacao.estado()).isEqualTo(EstadoAcaoCanal.PENDENTE);
    }

    @Test
    void compensacaoDeEtapaContinuaSendoEtapa() {
        AcaoCanal etapa = proativa(TipoAcaoCanal.MENSAGEM_ETAPA, "{\"texto\":\"etapa\"}");
        assertThat(etapa.criarCompensacao(AGORA, politica).tipo())
                .isEqualTo(TipoAcaoCanal.MENSAGEM_ETAPA);
    }

    @Test
    void acaoSemCompensacaoNaoCria() {
        AcaoCanal acao = proativa(TipoAcaoCanal.EDICAO, null);
        assertThat(acao.temCompensacao()).isFalse();
        assertThatThrownBy(() -> acao.criarCompensacao(AGORA, politica))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void zerarPayloadApagaOConteudo() {
        AcaoCanal acao = proativa(TipoAcaoCanal.EDICAO, "{}");
        acao.zerarPayload();
        assertThat(acao.payload()).isNull();
        assertThat(acao.compensacao()).isNull();
    }

    @Test
    void versaoEControladaPeloRepositorio() {
        AcaoCanal acao = resposta();
        acao.definirVersao(4);
        assertThat(acao.versao()).isEqualTo(4);
    }

    @Test
    void estadosFinais() {
        assertThat(EstadoAcaoCanal.PENDENTE.finalizado()).isFalse();
        assertThat(EstadoAcaoCanal.ENTREGUE.finalizado()).isFalse();
        for (EstadoAcaoCanal final_ :
                new EstadoAcaoCanal[] {
                    EstadoAcaoCanal.EXECUTADA,
                    EstadoAcaoCanal.FALHOU,
                    EstadoAcaoCanal.FALHA_DEFINITIVA,
                    EstadoAcaoCanal.DESCARTADA
                }) {
            assertThat(final_.finalizado()).isTrue();
        }
    }
}
