package br.com.felipeblaft.relatorios.saida.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.configuracao.CatalogoMensagens;
import br.com.felipeblaft.relatorios.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.Aprovacao;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.DocumentoGerado;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.ModeloPdfRelatorioAula;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.SituacaoDocumento;
import br.com.felipeblaft.relatorios.suporte.RelogioAjustavel;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PDF do relatório de aula com a identidade visual (FR-049), conferido pelo texto extraído. */
class PdfRelatorioAulaIT {

    private static OpenHtmlToPdfGeracaoPdfAdaptador adaptador;

    private static final ConteudoRelatorioAula CONTEUDO =
            new ConteudoRelatorioAula(
                    List.of("Estudo nº 5 de Czerny — compassos 1 a 16", "Escala de Sol maior"),
                    "Evolução notável na articulação; ação & reação.",
                    List.of(),
                    List.of("Estudar os compassos 9 a 12 com mãos separadas"),
                    "");

    @BeforeAll
    static void criar() {
        CatalogoMensagens catalogo = new CatalogoMensagens();
        adaptador =
                new OpenHtmlToPdfGeracaoPdfAdaptador(
                        catalogo, new FormatacaoMensagens(catalogo, new RelogioAjustavel()));
    }

    private static String texto(DocumentoGerado documento) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(documento.bytes())) {
            return new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
        }
    }

    @Test
    void conferenciaTemCabecalhoSecoesTracoNasVaziasEFaixa() throws Exception {
        DocumentoGerado documento =
                adaptador.gerarRelatorioAula(
                        new ModeloPdfRelatorioAula(
                                "Matteo Ferreira",
                                "Clara Souza",
                                LocalDate.of(2026, 9, 21),
                                CONTEUDO,
                                1,
                                SituacaoDocumento.CONFERENCIA,
                                null));

        String texto = texto(documento);
        // rótulos com espaçamento entre letras saem letra a letra na extração
        assertThat(texto.replace(" ", ""))
                .contains("RELATÓRIOPEDAGÓGICO")
                .contains("ALUNO")
                .contains("PROFESSOR")
                .contains("DATADAAULA");
        assertThat(texto)
                .contains("Relatório de Aula")
                .contains("Instituto Musical Felipe Blaft")
                .contains("Matteo Ferreira")
                .contains("Clara Souza")
                .contains("21/09/2026")
                .contains("Conteúdos trabalhados")
                .contains("Estudo nº 5 de Czerny — compassos 1 a 16")
                .contains("Evolução")
                .contains("ação & reação")
                .contains("Dificuldades")
                .contains("Atividades propostas")
                .contains("Observações")
                .contains("—")
                .contains("Documento em conferência — não oficial")
                .contains("Página 1 de 1")
                .doesNotContain("Aprovado por");
        assertThat(documento.tamanho()).isEqualTo(documento.bytes().length);
        assertThat(documento.sha256()).isEqualTo(sha256(documento.bytes()));
    }

    @Test
    void oficialTemAprovacaoSemFaixa() throws Exception {
        DocumentoGerado documento =
                adaptador.gerarRelatorioAula(
                        new ModeloPdfRelatorioAula(
                                "Matteo Ferreira",
                                "Clara Souza",
                                LocalDate.of(2026, 9, 21),
                                CONTEUDO,
                                2,
                                SituacaoDocumento.OFICIAL,
                                new Aprovacao(
                                        "Clara Souza", Instant.parse("2026-09-21T21:42:00Z"), 2)));

        assertThat(texto(documento))
                .contains("Aprovado por Clara Souza em 21/09/2026 às 18:42 · versão 2")
                .doesNotContain("Documento em conferência");
    }

    @Test
    void conteudoLongoOcupaVariasPaginasNumeradas() throws Exception {
        List<String> itens =
                java.util.stream.IntStream.rangeClosed(1, 30)
                        .mapToObj(
                                i ->
                                        "Item de conteúdo número "
                                                + i
                                                + " com um texto razoavelmente longo para ocupar espaço")
                        .toList();
        DocumentoGerado documento =
                adaptador.gerarRelatorioAula(
                        new ModeloPdfRelatorioAula(
                                "Matteo Ferreira",
                                "Clara Souza",
                                LocalDate.of(2026, 9, 21),
                                new ConteudoRelatorioAula(
                                        itens, "x".repeat(10), itens, itens, "fim"),
                                1,
                                SituacaoDocumento.CONFERENCIA,
                                null));

        try (PDDocument pdf = Loader.loadPDF(documento.bytes())) {
            int paginas = pdf.getNumberOfPages();
            assertThat(paginas).isGreaterThan(1);
            assertThat(texto(documento))
                    .contains("Página 1 de " + paginas)
                    .contains("Página " + paginas + " de " + paginas);
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
