package br.com.felipeblaft.relatorios.casodeuso.canal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.casodeuso.canal.ConfirmarExecucaoAcoesCanalCasoDeUso.Resposta;
import br.com.felipeblaft.relatorios.casodeuso.canal.ConfirmarExecucaoAcoesCanalCasoDeUso.Resultado;
import br.com.felipeblaft.relatorios.casodeuso.canal.ConfirmarExecucaoAcoesCanalCasoDeUso.Situacao;
import br.com.felipeblaft.relatorios.casodeuso.canal.GravarAcoesCanalCasoDeUso.NovaAcao;
import br.com.felipeblaft.relatorios.dominio.canal.AcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.EstadoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.OrigemAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.canal.PoliticaRetentativa;
import br.com.felipeblaft.relatorios.dominio.canal.TipoAcaoCanal;
import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.trabalho.EtapaTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TipoTrabalho;
import br.com.felipeblaft.relatorios.dominio.trabalho.TrabalhoProcessamento;
import br.com.felipeblaft.relatorios.dubles.AcaoCanalRepositorioEmMemoria;
import br.com.felipeblaft.relatorios.dubles.DublesProcessamento.FilaTrabalhoEmMemoria;
import br.com.felipeblaft.relatorios.dubles.RelogioFixo;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmarExecucaoAcoesCanalCasoDeUsoTeste {

    private final RelogioFixo relogio = new RelogioFixo();
    private final AcaoCanalRepositorioEmMemoria repositorio = new AcaoCanalRepositorioEmMemoria();
    private final FilaTrabalhoEmMemoria fila = new FilaTrabalhoEmMemoria();
    private final PoliticaRetentativa politica =
            new PoliticaRetentativa(
                    Duration.ofSeconds(30), Duration.ofMinutes(15), Duration.ofHours(24));
    private final GravarAcoesCanalCasoDeUso gravar =
            new GravarAcoesCanalCasoDeUso(repositorio, relogio, politica);
    private final ConfirmarExecucaoAcoesCanalCasoDeUso confirmar =
            new ConfirmarExecucaoAcoesCanalCasoDeUso(repositorio, fila, relogio, politica);

    private static Resultado ok(String id) {
        return new Resultado(id, Situacao.EXECUTADA, null, null);
    }

    private static Resultado falha(String id, String codigo) {
        return new Resultado(id, Situacao.FALHOU, codigo, null);
    }

    private void resposta(String... ids) {
        gravar.executar(
                "u1",
                OrigemAcaoCanal.RESPOSTA_UPDATE,
                1L,
                java.util.Arrays.stream(ids)
                        .map(
                                id ->
                                        new NovaAcao(
                                                id,
                                                TipoAcaoCanal.MENSAGEM,
                                                1L,
                                                null,
                                                null,
                                                "{\"texto\":\"a\"}",
                                                null))
                        .toList());
    }

    private TrabalhoProcessamento etapa(UUID professor) {
        TrabalhoProcessamento trabalho =
                TrabalhoProcessamento.novo(
                        TipoTrabalho.REGISTRO_AULA,
                        professor,
                        UUID.randomUUID(),
                        null,
                        null,
                        Canal.TELEGRAM,
                        Meio.AUDIO,
                        EtapaTrabalho.ENVIANDO,
                        relogio.agora());
        fila.enfileirar(trabalho);
        return trabalho;
    }

    // ---- caminho básico e idempotência

    @Test
    void executadaEFalhouViramEstadosFinaisComMotivo() {
        resposta("u1-0", "u1-1");
        Resposta resposta =
                confirmar.executar(List.of(ok("u1-0"), falha("u1-1", "CHAT_INDISPONIVEL")));
        assertThat(resposta.aceitas()).isEqualTo(2);
        assertThat(resposta.ignoradas()).isEmpty();
        assertThat(resposta.violacaoDeContrato()).isFalse();
        assertThat(repositorio.guardada("u1-0").estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
        assertThat(repositorio.guardada("u1-1").estado()).isEqualTo(EstadoAcaoCanal.FALHOU);
        assertThat(repositorio.guardada("u1-1").codigoMotivo()).isEqualTo("CHAT_INDISPONIVEL");
    }

    @Test
    void confirmacaoIdenticaRepetidaNaoTemEfeito() {
        resposta("u1-0");
        confirmar.executar(List.of(ok("u1-0")));
        int versao = repositorio.guardada("u1-0").versao();
        Resposta segunda = confirmar.executar(List.of(ok("u1-0")));
        assertThat(segunda.aceitas()).isEqualTo(1);
        assertThat(repositorio.guardada("u1-0").versao()).isEqualTo(versao);
    }

    @Test
    void confirmacaoDivergenteDesconhecidaOuInvalidaVaiParaIgnoradas() {
        resposta("u1-0", "u1-1");
        confirmar.executar(List.of(ok("u1-0")));
        Resposta resposta =
                confirmar.executar(
                        java.util.Arrays.asList(
                                falha("u1-0", "ERRO_TELEGRAM"), // divergente do já registrado
                                ok("u9-9"), // desconhecida
                                new Resultado("u1-1", Situacao.FALHOU, null, null), // sem motivo
                                new Resultado("u1-1", null, null, null), // sem situação
                                new Resultado(null, Situacao.EXECUTADA, null, null),
                                new Resultado("", Situacao.EXECUTADA, null, null),
                                new Resultado("texto livre com espaços de aluno", Situacao.EXECUTADA, null, null),
                                null,
                                ok("u1-1")));
        assertThat(resposta.aceitas()).isEqualTo(1);
        assertThat(resposta.ignoradas()).containsExactly("u1-0", "u9-9", "u1-1", "u1-1");
        assertThat(repositorio.guardada("u1-1").estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
    }

    @Test
    void itemInvalidoNaoDerrubaOsDemais() {
        resposta("u1-0", "u1-1");
        Resposta resposta =
                confirmar.executar(List.of(new Resultado("u1-0", Situacao.FALHOU, " ", null), ok("u1-1")));
        assertThat(resposta.aceitas()).isEqualTo(1);
        assertThat(repositorio.guardada("u1-0").estado()).isEqualTo(EstadoAcaoCanal.ENTREGUE);
        assertThat(repositorio.guardada("u1-1").estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
    }

    @Test
    void acaoDescartadaOuComFalhaDefinitivaIgnoraConfirmacao() {
        gravar.executar(
                "p:a",
                OrigemAcaoCanal.PROATIVA,
                null,
                List.of(
                        new NovaAcao("p:a:0", TipoAcaoCanal.MENSAGEM, 1L, UUID.randomUUID(), null, "{}", null),
                        new NovaAcao("p:a:1", TipoAcaoCanal.MENSAGEM, 1L, UUID.randomUUID(), null, "{}", null)));
        repositorio.guardada("p:a:0").descartar(relogio.agora());
        repositorio.guardada("p:a:1").expirar(relogio.agora().plus(Duration.ofHours(30)));
        Resposta resposta = confirmar.executar(List.of(ok("p:a:0"), ok("p:a:1")));
        assertThat(resposta.aceitas()).isZero();
        assertThat(resposta.ignoradas()).containsExactly("p:a:0", "p:a:1");
    }

    // ---- código desconhecido

    @Test
    void codigoDesconhecidoOuTextoLivreViraErroTelegram() {
        resposta("u1-0", "u1-1", "u1-2");
        Resposta resposta =
                confirmar.executar(
                        List.of(
                                falha("u1-0", "CODIGO_NOVO_DO_N8N"),
                                falha("u1-1", "texto livre da Maria"),
                                ok("u1-2")));
        assertThat(resposta.aceitas()).isEqualTo(3);
        assertThat(repositorio.guardada("u1-0").codigoMotivo()).isEqualTo("ERRO_TELEGRAM");
        assertThat(repositorio.guardada("u1-1").codigoMotivo()).isEqualTo("ERRO_TELEGRAM");
        assertThat(repositorio.guardada("u1-2").estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
    }

    // ---- compensação

    private void edicaoComCompensacao(String id) {
        gravar.executar(
                "p:e",
                OrigemAcaoCanal.PROATIVA,
                null,
                List.of(
                        new NovaAcao(
                                id,
                                TipoAcaoCanal.EDICAO,
                                1L,
                                UUID.randomUUID(),
                                null,
                                "{\"mensagemId\":5}",
                                "{\"texto\":\"mesmo texto\"}")));
    }

    @Test
    void edicaoNaoEditavelGeraUmaCompensacaoComOMesmoConteudo() {
        edicaoComCompensacao("p:e:0");
        confirmar.executar(List.of(falha("p:e:0", "MENSAGEM_NAO_EDITAVEL")));
        AcaoCanal compensacao = repositorio.buscarPorId("p:e:0-c").orElseThrow();
        assertThat(compensacao.origem()).isEqualTo(OrigemAcaoCanal.COMPENSACAO);
        assertThat(compensacao.estado()).isEqualTo(EstadoAcaoCanal.PENDENTE);
        assertThat(compensacao.payload()).isEqualTo("{\"texto\":\"mesmo texto\"}");
        assertThat(compensacao.loteId()).isEqualTo("c:p:e:0");
        // repetir a confirmação não cria outra
        confirmar.executar(List.of(falha("p:e:0", "MENSAGEM_NAO_EDITAVEL")));
        assertThat(repositorio.todas()).hasSize(2);
    }

    @Test
    void compensacaoQueFalhaNaoGeraOutra() {
        edicaoComCompensacao("p:e:0");
        confirmar.executar(List.of(falha("p:e:0", "MENSAGEM_NAO_EDITAVEL")));
        confirmar.executar(List.of(falha("p:e:0-c", "MENSAGEM_NAO_EDITAVEL")));
        assertThat(repositorio.todas()).hasSize(2);
        assertThat(repositorio.guardada("p:e:0-c").estado()).isEqualTo(EstadoAcaoCanal.FALHOU);
    }

    @Test
    void demaisCodigosSoRegistramSemCompensar() {
        int i = 0;
        for (String codigo :
                List.of(
                        "CHAT_INDISPONIVEL",
                        "LIMITE_DE_TAXA",
                        "ACAO_NAO_SUPORTADA",
                        "DOCUMENTO_INDISPONIVEL",
                        "ERRO_TELEGRAM")) {
            String id = "p:e:" + i++;
            edicaoComCompensacao(id);
            confirmar.executar(List.of(falha(id, codigo)));
            assertThat(repositorio.buscarPorId(id + "-c")).isEmpty();
            assertThat(repositorio.guardada(id).codigoMotivo()).isEqualTo(codigo);
        }
        assertThat(repositorio.todas()).hasSize(5);
    }

    @Test
    void naoEditavelSemCompensacaoGravadaSoRegistra() {
        resposta("u1-0");
        confirmar.executar(List.of(falha("u1-0", "MENSAGEM_NAO_EDITAVEL")));
        assertThat(repositorio.todas()).hasSize(1);
        assertThat(repositorio.guardada("u1-0").estado()).isEqualTo(EstadoAcaoCanal.FALHOU);
    }

    // ---- mensagem de etapa

    private void etapaProativa(UUID trabalho, String id, String compensacao) {
        gravar.executar(
                "p:etapa:" + trabalho,
                OrigemAcaoCanal.PROATIVA,
                null,
                List.of(
                        new NovaAcao(
                                id,
                                TipoAcaoCanal.MENSAGEM_ETAPA,
                                1L,
                                UUID.randomUUID(),
                                trabalho,
                                "{\"texto\":\"etapa\"}",
                                compensacao)));
    }

    @Test
    void etapaExecutadaGravaOMensagemIdNoTrabalhoAntesDeFinalizar() {
        TrabalhoProcessamento trabalho = etapa(UUID.randomUUID());
        etapaProativa(trabalho.id(), "p:etapa:x:0", null);
        Resposta resposta =
                confirmar.executar(
                        List.of(new Resultado("p:etapa:x:0", Situacao.EXECUTADA, null, 4321L)));
        assertThat(resposta.aceitas()).isEqualTo(1);
        assertThat(trabalho.parametro(TrabalhoProcessamento.MENSAGEM_ETAPA)).contains("4321");
        assertThat(repositorio.guardada("p:etapa:x:0").estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
    }

    @Test
    void etapaExecutadaSemMensagemIdESinalizadaENaoFinaliza() {
        TrabalhoProcessamento trabalho = etapa(UUID.randomUUID());
        etapaProativa(trabalho.id(), "p:etapa:x:0", null);
        gravar.executar(
                "u1", OrigemAcaoCanal.RESPOSTA_UPDATE, 1L,
                List.of(new NovaAcao("u1-0", TipoAcaoCanal.MENSAGEM, 1L, null, null, "{}", null)));
        for (Long invalido : new Long[] {null, 0L}) {
            Resposta resposta =
                    confirmar.executar(
                            List.of(
                                    new Resultado("p:etapa:x:0", Situacao.EXECUTADA, null, invalido),
                                    ok("u1-0")));
            assertThat(resposta.violacaoDeContrato()).isTrue();
            assertThat(resposta.aceitas()).isLessThanOrEqualTo(1);
            assertThat(resposta.ignoradas()).contains("p:etapa:x:0");
        }
        assertThat(repositorio.guardada("p:etapa:x:0").estado()).isEqualTo(EstadoAcaoCanal.PENDENTE);
        assertThat(trabalho.parametro(TrabalhoProcessamento.MENSAGEM_ETAPA)).isEmpty();
        assertThat(repositorio.guardada("u1-0").estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
    }

    @Test
    void falhaDeEdicaoDeEtapaGeraNovaMensagemDeEtapaQueRegravaOId() {
        TrabalhoProcessamento trabalho = etapa(UUID.randomUUID());
        etapaProativa(trabalho.id(), "p:etapa:x:0", "{\"texto\":\"etapa\"}");
        confirmar.executar(List.of(falha("p:etapa:x:0", "MENSAGEM_NAO_EDITAVEL")));
        AcaoCanal compensacao = repositorio.buscarPorId("p:etapa:x:0-c").orElseThrow();
        assertThat(compensacao.tipo()).isEqualTo(TipoAcaoCanal.MENSAGEM_ETAPA);
        assertThat(compensacao.trabalhoId()).isEqualTo(trabalho.id());
        confirmar.executar(List.of(new Resultado("p:etapa:x:0-c", Situacao.EXECUTADA, null, 777L)));
        assertThat(trabalho.parametro(TrabalhoProcessamento.MENSAGEM_ETAPA)).contains("777");
    }

    @Test
    void etapaSemTrabalhoNaoExigeMensagemId() {
        gravar.executar(
                "p:s",
                OrigemAcaoCanal.PROATIVA,
                null,
                List.of(new NovaAcao("p:s:0", TipoAcaoCanal.MENSAGEM_ETAPA, 1L, UUID.randomUUID(), null, "{}", null)));
        assertThat(confirmar.executar(List.of(ok("p:s:0"))).aceitas()).isEqualTo(1);
    }

    // ---- concorrência

    @Test
    void gravacaoPerdidaParaOutroAtorERefeitaSobreOEstadoNovo() {
        resposta("u1-0");
        int[] disparos = {0};
        repositorio.antesDeAtualizar =
                () -> {
                    if (disparos[0]++ == 0) {
                        // outro ator (despachante) grava no meio: a versão avança
                        AcaoCanal outra = repositorio.guardada("u1-0");
                        outra.definirVersao(outra.versao() + 1);
                    }
                };
        Resposta resposta = confirmar.executar(List.of(ok("u1-0")));
        assertThat(resposta.aceitas()).isEqualTo(1);
        assertThat(disparos[0]).isEqualTo(2);
        assertThat(repositorio.guardada("u1-0").estado()).isEqualTo(EstadoAcaoCanal.EXECUTADA);
    }

    @Test
    void desistePoisDeTresDisputasPerdidas() {
        resposta("u1-0");
        repositorio.antesDeAtualizar =
                () -> {
                    AcaoCanal outra = repositorio.guardada("u1-0");
                    outra.definirVersao(outra.versao() + 1);
                };
        Resposta resposta = confirmar.executar(List.of(ok("u1-0")));
        assertThat(resposta.aceitas()).isZero();
        assertThat(resposta.ignoradas()).containsExactly("u1-0");
        assertThat(repositorio.guardada("u1-0").estado()).isEqualTo(EstadoAcaoCanal.ENTREGUE);
    }

    @Test
    void falhaTambemRefazAposDisputaPerdida() {
        resposta("u1-0");
        int[] disparos = {0};
        repositorio.antesDeAtualizar =
                () -> {
                    if (disparos[0]++ == 0) {
                        AcaoCanal outra = repositorio.guardada("u1-0");
                        outra.definirVersao(outra.versao() + 1);
                    }
                };
        assertThat(confirmar.executar(List.of(falha("u1-0", "ERRO_TELEGRAM"))).aceitas()).isEqualTo(1);
    }
}
