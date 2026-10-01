package br.com.felipeblaft.relatorios.dominio.semestral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.CodigoErro;
import br.com.felipeblaft.relatorios.dominio.comum.ExcecaoDominio;
import br.com.felipeblaft.relatorios.dominio.relatorio.EtapaRelatorio;
import br.com.felipeblaft.relatorios.dominio.relatorio.StatusRelatorio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PeriodoSemestral, InformacoesGerais e RelatorioSemestral (data-model.md §9). */
class SemestralDominioTeste {

    private static final Instant AGORA = Instant.parse("2026-09-21T13:00:00Z");

    private static void codigo(Runnable acao, CodigoErro esperado) {
        assertThatThrownBy(acao::run)
                .isInstanceOfSatisfying(
                        ExcecaoDominio.class, e -> assertThat(e.codigo()).isEqualTo(esperado));
    }

    @Test
    void periodoTemChaveDatasEOrdem() {
        PeriodoSemestral primeiro = PeriodoSemestral.de("2026-1");
        PeriodoSemestral segundo = PeriodoSemestral.contendo(LocalDate.of(2026, 9, 21));

        assertThat(primeiro.inicio()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(primeiro.fim()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(segundo.chave()).isEqualTo("2026-2");
        assertThat(segundo.inicio()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(segundo.fim()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(segundo.anterior()).isEqualTo(primeiro);
        assertThat(primeiro.anterior().chave()).isEqualTo("2025-2");
        assertThat(segundo.emAndamento(LocalDate.of(2026, 9, 21))).isTrue();
        assertThat(primeiro.emAndamento(LocalDate.of(2026, 9, 21))).isFalse();
        assertThat(primeiro.contem(LocalDate.of(2026, 6, 30))).isTrue();
        assertThat(primeiro.contem(LocalDate.of(2026, 7, 1))).isFalse();
    }

    @Test
    void chaveInvalidaEDadosInvalidos() {
        for (String chave : new String[] {"2026-3", "26-1", "2026/1", "", null}) {
            codigo(() -> PeriodoSemestral.de(chave), CodigoErro.DADOS_INVALIDOS);
        }
    }

    @Test
    void disponiveisDoMaisRecenteAoMaisAntigo() {
        List<PeriodoSemestral> periodos =
                PeriodoSemestral.disponiveis(LocalDate.of(2025, 3, 10), LocalDate.of(2026, 9, 21));

        assertThat(periodos)
                .extracting(PeriodoSemestral::chave)
                .containsExactly("2026-2", "2026-1", "2025-2", "2025-1");
        assertThat(
                        PeriodoSemestral.disponiveis(
                                LocalDate.of(2027, 1, 1), LocalDate.of(2026, 9, 21)))
                .extracting(PeriodoSemestral::chave)
                .containsExactly("2026-2");
    }

    @Test
    void idadeNoFimDoPeriodoOuHoje() {
        PeriodoSemestral passado = PeriodoSemestral.de("2025-1");
        PeriodoSemestral corrente = PeriodoSemestral.de("2026-2");
        LocalDate nascimento = LocalDate.of(2012, 5, 10);
        LocalDate hoje = LocalDate.of(2026, 3, 1);

        InformacoesGerais noPassado =
                InformacoesGerais.calcular(
                        "Matteo", nascimento, "Piano", "Clara", passado, 4, hoje);
        InformacoesGerais noCorrente =
                InformacoesGerais.calcular(
                        "Matteo", nascimento, "Piano", "Clara", corrente, 2, hoje);

        assertThat(noPassado.idade()).isEqualTo(13);
        assertThat(noCorrente.idade()).isEqualTo(13);
        assertThat(
                        InformacoesGerais.calcular(
                                        "Matteo",
                                        nascimento,
                                        null,
                                        "Clara",
                                        corrente,
                                        2,
                                        LocalDate.of(2026, 9, 21))
                                .idade())
                .isEqualTo(14);
        assertThat(noPassado.periodoChave()).isEqualTo("2025-1");
        assertThat(noPassado.quantidadeAulasConsideradas()).isEqualTo(4);
    }

    @Test
    void conteudoNormalizaSecoesAusentesEItensEmBranco() {
        ConteudoRelatorioSemestral conteudo =
                new ConteudoRelatorioSemestral(
                        null,
                        new ConteudoRelatorioSemestral.Tecnica(
                                List.of(" Escalas ", " "), null, null, null),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "  Parecer  ");

        assertThat(conteudo.tecnica().conteudosTrabalhados()).containsExactly("Escalas");
        assertThat(conteudo.frequenciaEEstudo().comentarioGeral()).isEmpty();
        assertThat(conteudo.pontosDeAtencao().itens()).isEmpty();
        assertThat(conteudo.parecerFinal()).isEqualTo("Parecer");
        assertThat(ConteudoRelatorioSemestral.vazio())
                .isEqualTo(ConteudoRelatorioSemestral.vazio());
    }

    private static RelatorioSemestral novo() {
        PeriodoSemestral periodo = PeriodoSemestral.de("2026-1");
        return RelatorioSemestral.criar(
                UUID.randomUUID(),
                UUID.randomUUID(),
                periodo,
                3,
                1,
                InformacoesGerais.calcular(
                        "Matteo",
                        LocalDate.of(2012, 5, 10),
                        "Piano",
                        "Clara",
                        periodo,
                        3,
                        LocalDate.of(2026, 9, 21)),
                ConteudoRelatorioSemestral.vazio(),
                Canal.WEB,
                AGORA);
    }

    @Test
    void nasceEmConferenciaNaVersao1EAlteraGerandoNovaVersao() {
        RelatorioSemestral relatorio = novo();
        assertThat(relatorio.status()).isEqualTo(StatusRelatorio.AGUARDANDO_CONFERENCIA);
        assertThat(relatorio.versaoVigente()).isEqualTo(1);
        assertThat(relatorio.quantidadeRelatoriosEmAberto()).isEqualTo(1);

        relatorio.exigirPodeAlterar(1);
        relatorio.iniciarEtapa(EtapaRelatorio.APLICANDO_ALTERACAO, AGORA);
        codigo(() -> relatorio.exigirPodeAlterar(1), CodigoErro.PROCESSAMENTO_EM_ANDAMENTO);
        assertThat(relatorio.aplicarAlteracao(ConteudoRelatorioSemestral.vazio(), AGORA))
                .isEqualTo(2);
        codigo(() -> relatorio.exigirPodeAlterar(1), CodigoErro.VERSAO_DESATUALIZADA);
        codigo(() -> relatorio.aprovar(1, Canal.WEB, AGORA), CodigoErro.VERSAO_DESATUALIZADA);
    }

    @Test
    void aprovadoOuCanceladoNaoAceitaMaisNada() {
        RelatorioSemestral aprovado = novo();
        aprovado.aprovar(1, Canal.TELEGRAM, AGORA);
        assertThat(aprovado.versaoAprovada()).isEqualTo(1);
        assertThat(aprovado.canalAprovacao()).isEqualTo(Canal.TELEGRAM);
        codigo(() -> aprovado.aprovar(1, Canal.WEB, AGORA), CodigoErro.RELATORIO_JA_APROVADO);
        codigo(() -> aprovado.cancelar(AGORA), CodigoErro.RELATORIO_JA_APROVADO);

        RelatorioSemestral cancelado = novo();
        cancelado.cancelar(AGORA);
        codigo(() -> cancelado.exigirPodeAlterar(1), CodigoErro.RELATORIO_CANCELADO);
        assertThatThrownBy(
                        () -> cancelado.aplicarAlteracao(ConteudoRelatorioSemestral.vazio(), AGORA))
                .isInstanceOf(IllegalStateException.class);
    }
}
