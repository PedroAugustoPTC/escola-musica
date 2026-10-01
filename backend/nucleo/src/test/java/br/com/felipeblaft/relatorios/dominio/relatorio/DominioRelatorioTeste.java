package br.com.felipeblaft.relatorios.dominio.relatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.relatorios.dominio.comum.Canal;
import br.com.felipeblaft.relatorios.dominio.comum.Meio;
import br.com.felipeblaft.relatorios.dominio.comum.TipoRelatorio;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ConteudoRelatorioAula, VersaoRelatorio, InteracaoRelatorio e DocumentoPdf. */
class DominioRelatorioTeste {

    private static final Instant T0 = Instant.parse("2026-09-21T13:00:00Z");

    @Test
    void conteudoNormalizaSemInventar() {
        ConteudoRelatorioAula conteudo =
                new ConteudoRelatorioAula(
                        Arrays.asList(" Czerny ", "", null, "  "),
                        null,
                        null,
                        List.of("Escala"),
                        "  obs ");
        assertThat(conteudo.conteudosTrabalhados()).containsExactly("Czerny");
        assertThat(conteudo.evolucao()).isEmpty();
        assertThat(conteudo.dificuldades()).isEmpty();
        assertThat(conteudo.observacoes()).isEqualTo("obs");
        assertThat(conteudo.estaVazio()).isFalse();
        assertThat(ConteudoRelatorioAula.vazio().estaVazio()).isTrue();
        assertThat(new ConteudoRelatorioAula(List.of(), "x", List.of(), List.of(), "").estaVazio())
                .isFalse();
        assertThat(
                        new ConteudoRelatorioAula(List.of(), "", List.of("d"), List.of(), "")
                                .estaVazio())
                .isFalse();
        assertThat(
                        new ConteudoRelatorioAula(List.of(), "", List.of(), List.of("a"), "")
                                .estaVazio())
                .isFalse();
        assertThat(new ConteudoRelatorioAula(List.of(), "", List.of(), List.of(), "o").estaVazio())
                .isFalse();
    }

    @Test
    void versaoImutavelComecaEmUm() {
        UUID relatorio = UUID.randomUUID();
        VersaoRelatorio versao =
                VersaoRelatorio.nova(
                        TipoRelatorio.AULA,
                        relatorio,
                        1,
                        ConteudoRelatorioAula.vazio(),
                        OrigemVersao.ESTRUTURACAO_INICIAL,
                        null,
                        Canal.WEB,
                        "estruturacao-aula.v1",
                        T0);
        assertThat(versao.numero()).isEqualTo(1);
        assertThat(versao.relatorioId()).isEqualTo(relatorio);
        assertThatThrownBy(
                        () ->
                                VersaoRelatorio.nova(
                                        TipoRelatorio.AULA,
                                        relatorio,
                                        0,
                                        ConteudoRelatorioAula.vazio(),
                                        OrigemVersao.ALTERACAO,
                                        null,
                                        Canal.WEB,
                                        null,
                                        T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interacaoNaoExpoeOTexto() {
        InteracaoRelatorio interacao =
                InteracaoRelatorio.nova(
                        TipoRelatorio.AULA,
                        UUID.randomUUID(),
                        TipoInteracao.ALTERACAO,
                        Canal.TELEGRAM,
                        Meio.AUDIO,
                        "troque a evolução",
                        2,
                        null,
                        ResultadoInteracao.APLICADA,
                        3,
                        T0);
        assertThat(interacao.toString()).doesNotContain("troque").contains("ALTERACAO");
        assertThat(interacao.versaoResultante()).isEqualTo(3);
    }

    @Test
    void documentoPdfEAula() {
        DocumentoPdf documento =
                DocumentoPdf.novo(
                        UUID.randomUUID(),
                        TipoDocumento.OFICIAL,
                        "relatorios/aula/x/oficial.pdf",
                        10,
                        "sha",
                        T0);
        assertThat(documento.tipo()).isEqualTo(TipoDocumento.OFICIAL);
        assertThatThrownBy(
                        () ->
                                new Aula(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        null,
                                        T0))
                .isInstanceOf(NullPointerException.class);
        assertThat(EtapaRelatorio.valueOf("GERANDO_PDF")).isNotNull();
        assertThat(OrigemVersao.values()).hasSize(4);
    }
}
