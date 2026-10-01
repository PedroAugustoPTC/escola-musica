package br.com.felipeblaft.relatorios.dominio.relatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** Transições e proibições da máquina de estados do relatório de aula (data-model.md §8.1). */
class RelatorioAulaTeste {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");
    private static final ConteudoRelatorioAula CONTEUDO =
            new ConteudoRelatorioAula(
                    List.of("Czerny"), "Evoluiu.", List.of(), List.of("Escala"), "");
    private static final ConteudoRelatorioAula OUTRO =
            new ConteudoRelatorioAula(
                    List.of("Czerny"), "Evoluiu muito.", List.of(), List.of(), "");

    private static RelatorioAula novo() {
        Aula aula = Aula.nova(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 21), T0);
        return RelatorioAula.criar(aula, "  transcrição da aula  ", Canal.TELEGRAM, T0);
    }

    private static RelatorioAula emConferencia() {
        RelatorioAula relatorio = novo();
        relatorio.registrarEstruturacao(CONTEUDO, List.of(), T0);
        return relatorio;
    }

    private static void falhaCom(ThrowingCallable acao, CodigoErro codigo) {
        assertThatThrownBy(acao)
                .isInstanceOf(ExcecaoDominio.class)
                .extracting(e -> ((ExcecaoDominio) e).codigo())
                .isEqualTo(codigo);
    }

    @Test
    void criacaoExigeTranscricaoENasceEstruturando() {
        RelatorioAula relatorio = novo();
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.EM_PROCESSAMENTO);
        assertThat(relatorio.etapa()).isEqualTo(EtapaRelatorio.ESTRUTURANDO);
        assertThat(relatorio.transcricao()).isEqualTo("transcrição da aula");
        assertThat(relatorio.versaoVigente()).isZero();
        assertThat(relatorio.canalOrigem()).isEqualTo(Canal.TELEGRAM);
        assertThat(relatorio.conteudo().estaVazio()).isTrue();
        assertThat(relatorio.toString()).doesNotContain("transcrição");
        Aula aula = Aula.nova(UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 21), T0);
        assertThatThrownBy(() -> RelatorioAula.criar(aula, "  ", Canal.WEB, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RelatorioAula.criar(aula, null, Canal.WEB, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void estruturacaoSemPerguntasVaiParaConferenciaNaVersaoUm() {
        RelatorioAula relatorio = novo();
        assertThat(relatorio.registrarEstruturacao(CONTEUDO, List.of(" "), T0.plusSeconds(5)))
                .isTrue();
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.AGUARDANDO_CONFERENCIA);
        assertThat(relatorio.versaoVigente()).isEqualTo(1);
        assertThat(relatorio.etapa()).isNull();
        assertThat(relatorio.atualizadoEm()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    void noMaximoDuasRodadasDePerguntas() {
        RelatorioAula relatorio = novo();
        assertThat(relatorio.registrarEstruturacao(CONTEUDO, List.of("P1", "P2"), T0)).isFalse();
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.AGUARDANDO_RESPOSTAS);
        assertThat(relatorio.perguntasPendentes()).containsExactly("P1", "P2");
        assertThat(relatorio.rodadasPerguntas()).isEqualTo(1);

        assertThat(relatorio.incorporarResposta(CONTEUDO, List.of("P3"), T0)).isFalse();
        assertThat(relatorio.rodadasPerguntas()).isEqualTo(2);
        assertThat(relatorio.perguntasPendentes()).containsExactly("P3");

        // na 2ª rodada, as perguntas que sobrarem são ignoradas
        assertThat(relatorio.incorporarResposta(OUTRO, List.of("P4"), T0)).isTrue();
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.AGUARDANDO_CONFERENCIA);
        assertThat(relatorio.perguntasPendentes()).isEmpty();
        assertThat(relatorio.conteudo()).isEqualTo(OUTRO);
        assertThat(relatorio.versaoVigente()).isEqualTo(1);
    }

    @Test
    void falhaDeEstruturacaoETentarDeNovo() {
        RelatorioAula relatorio = novo();
        relatorio.falharEstruturacao(T0);
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.FALHA_PROCESSAMENTO);
        assertThat(relatorio.motivoFalha()).isEqualTo(RelatorioAula.FALHA_ESTRUTURACAO);
        assertThat(relatorio.transcricao()).isNotBlank();
        relatorio.prepararNovaTentativa(T0);
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.EM_PROCESSAMENTO);
        assertThat(relatorio.etapa()).isEqualTo(EtapaRelatorio.ESTRUTURANDO);
        assertThat(relatorio.motivoFalha()).isNull();
        falhaCom(relatorio::exigirPodeTentarDeNovo, CodigoErro.PROCESSAMENTO_EM_ANDAMENTO);
        falhaCom(
                () -> emConferencia().exigirPodeTentarDeNovo(),
                CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA);
    }

    @Test
    void alteracaoCriaNovaVersaoEExigeVersaoVigente() {
        RelatorioAula relatorio = emConferencia();
        relatorio.exigirPodeAlterar(1);
        falhaCom(() -> relatorio.exigirPodeAlterar(0), CodigoErro.VERSAO_DESATUALIZADA);
        relatorio.iniciarEtapa(EtapaRelatorio.APLICANDO_ALTERACAO, T0);
        falhaCom(() -> relatorio.exigirPodeAlterar(1), CodigoErro.PROCESSAMENTO_EM_ANDAMENTO);
        assertThat(relatorio.aplicarAlteracao(OUTRO, T0)).isEqualTo(2);
        assertThat(relatorio.etapa()).isNull();
        assertThatThrownBy(() -> relatorio.exigirVersaoVigente(1))
                .isInstanceOf(ExcecaoDominio.class)
                .satisfies(
                        e ->
                                assertThat(((ExcecaoDominio) e).detalhes())
                                        .containsEntry("versaoVigente", 2));
    }

    @Test
    void responderSoAguardandoRespostasSemEtapa() {
        RelatorioAula relatorio = novo();
        relatorio.registrarEstruturacao(CONTEUDO, List.of("P1"), T0);
        relatorio.exigirPodeResponder();
        relatorio.iniciarEtapa(EtapaRelatorio.INCORPORANDO_RESPOSTAS, T0);
        falhaCom(relatorio::exigirPodeResponder, CodigoErro.PROCESSAMENTO_EM_ANDAMENTO);
        relatorio.concluirEtapa(T0);
        falhaCom(
                () -> emConferencia().exigirPodeResponder(),
                CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA);
    }

    @Test
    void aprovarSoAVersaoVigenteSemEtapaEmCurso() {
        RelatorioAula relatorio = emConferencia();
        falhaCom(() -> relatorio.aprovar(2, Canal.WEB, T0), CodigoErro.VERSAO_DESATUALIZADA);
        relatorio.iniciarEtapa(EtapaRelatorio.GERANDO_PDF, T0);
        falhaCom(() -> relatorio.aprovar(1, Canal.WEB, T0), CodigoErro.PROCESSAMENTO_EM_ANDAMENTO);
        relatorio.concluirEtapa(T0);

        relatorio.aprovar(1, Canal.TELEGRAM, T0.plusSeconds(60));

        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.APROVADO);
        assertThat(relatorio.aprovadoEm()).isEqualTo(T0.plusSeconds(60));
        assertThat(relatorio.versaoAprovada()).isEqualTo(1);
        assertThat(relatorio.canalAprovacao()).isEqualTo(Canal.TELEGRAM);
        falhaCom(() -> relatorio.aprovar(1, Canal.WEB, T0), CodigoErro.RELATORIO_JA_APROVADO);
        falhaCom(() -> relatorio.cancelar(T0, false), CodigoErro.RELATORIO_JA_APROVADO);
        falhaCom(() -> relatorio.exigirPodeAlterar(1), CodigoErro.RELATORIO_JA_APROVADO);
    }

    @Test
    void aprovarForaDaConferencia() {
        RelatorioAula relatorio = novo();
        relatorio.registrarEstruturacao(CONTEUDO, List.of("P1"), T0);
        falhaCom(() -> relatorio.aprovar(0, Canal.WEB, T0), CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA);
    }

    @Test
    void cancelarEmProcessamentoSoPeloDescarte() {
        RelatorioAula relatorio = novo();
        falhaCom(() -> relatorio.cancelar(T0, false), CodigoErro.ACAO_NAO_PERMITIDA_NA_ETAPA);
        relatorio.cancelar(T0, true);
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.CANCELADO);
        assertThat(relatorio.canceladoEm()).isEqualTo(T0);
        falhaCom(() -> relatorio.cancelar(T0, true), CodigoErro.RELATORIO_CANCELADO);
        falhaCom(relatorio::exigirAberto, CodigoErro.RELATORIO_CANCELADO);
    }

    @Test
    void cancelarEmConferenciaOuFalha() {
        RelatorioAula conferencia = emConferencia();
        conferencia.cancelar(T0, false);
        assertThat(conferencia.status()).isEqualTo(StatusRelatorio.CANCELADO);
        RelatorioAula falha = novo();
        falha.falharEstruturacao(T0);
        falha.cancelar(T0, false);
        assertThat(falha.status()).isEqualTo(StatusRelatorio.CANCELADO);
    }

    @Test
    void transicoesInternasInvalidasSaoErroDeProgramacao() {
        RelatorioAula relatorio = emConferencia();
        assertThatThrownBy(() -> relatorio.registrarEstruturacao(CONTEUDO, List.of(), T0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> relatorio.incorporarResposta(CONTEUDO, List.of(), T0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> relatorio.falharEstruturacao(T0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> novo().aplicarAlteracao(CONTEUDO, T0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tempoDeProcessamentoEVersaoPersistida() {
        RelatorioAula relatorio = novo();
        relatorio.somarTempoProcessamento(1500);
        relatorio.somarTempoProcessamento(-10);
        relatorio.registrarVersaoPersistida(3);
        assertThat(relatorio.tempoProcessamentoMs()).isEqualTo(1500);
        assertThat(relatorio.versao()).isEqualTo(3);
        assertThat(relatorio.aulaId()).isNotNull();
        assertThat(relatorio.criadoEm()).isEqualTo(T0);
    }
}
