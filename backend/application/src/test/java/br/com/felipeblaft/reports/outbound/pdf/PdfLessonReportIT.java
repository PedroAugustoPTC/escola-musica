package br.com.felipeblaft.reports.outbound.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.port.PdfGenerationPort.Approval;
import br.com.felipeblaft.reports.port.PdfGenerationPort.DocumentStatus;
import br.com.felipeblaft.reports.port.PdfGenerationPort.GeneratedDocument;
import br.com.felipeblaft.reports.port.PdfGenerationPort.LessonReportPdfModel;
import br.com.felipeblaft.reports.support.AdjustableClock;
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
class PdfLessonReportIT {

    private static OpenHtmlToPdfGenerationAdapter adapter;

    private static final LessonReportContent CONTENT =
            new LessonReportContent(
                    List.of("Estudo nº 5 de Czerny — compassos 1 a 16", "Escala de Sol maior"),
                    "Evolução notável na articulação; ação & reação.",
                    List.of(),
                    List.of("Estudar os compassos 9 a 12 com mãos separadas"),
                    "");

    @BeforeAll
    static void create() {
        MessageCatalog catalog = new MessageCatalog();
        adapter =
                new OpenHtmlToPdfGenerationAdapter(
                        catalog, new MessageFormatting(catalog, new AdjustableClock()));
    }

    private static String text(GeneratedDocument document) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(document.bytes())) {
            return new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
        }
    }

    @Test
    void reviewHasHeaderSectionsDashInEmptyOnesAndBand() throws Exception {
        GeneratedDocument document =
                adapter.generateLessonReport(
                        new LessonReportPdfModel(
                                "Matteo Ferreira",
                                "Clara Souza",
                                LocalDate.of(2026, 9, 21),
                                CONTENT,
                                1,
                                DocumentStatus.CONFERENCIA,
                                null));

        String text = text(document);
        // rótulos com espaçamento entre letras saem letra a letra na extração
        assertThat(text.replace(" ", ""))
                .contains("RELATÓRIOPEDAGÓGICO")
                .contains("ALUNO")
                .contains("PROFESSOR")
                .contains("DATADAAULA");
        assertThat(text)
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
        assertThat(document.size()).isEqualTo(document.bytes().length);
        assertThat(document.sha256()).isEqualTo(sha256(document.bytes()));
    }

    @Test
    void officialHasApprovalWithoutBand() throws Exception {
        GeneratedDocument document =
                adapter.generateLessonReport(
                        new LessonReportPdfModel(
                                "Matteo Ferreira",
                                "Clara Souza",
                                LocalDate.of(2026, 9, 21),
                                CONTENT,
                                2,
                                DocumentStatus.OFICIAL,
                                new Approval(
                                        "Clara Souza", Instant.parse("2026-09-21T21:42:00Z"), 2)));

        assertThat(text(document))
                .contains("Aprovado por Clara Souza em 21/09/2026 às 18:42 · versão 2")
                .doesNotContain("Documento em conferência");
    }

    @Test
    void longContentOccupiesSeveralNumberedPages() throws Exception {
        List<String> items =
                java.util.stream.IntStream.rangeClosed(1, 30)
                        .mapToObj(
                                i ->
                                        "Item de conteúdo número "
                                                + i
                                                + " com um texto razoavelmente longo para ocupar espaço")
                        .toList();
        GeneratedDocument document =
                adapter.generateLessonReport(
                        new LessonReportPdfModel(
                                "Matteo Ferreira",
                                "Clara Souza",
                                LocalDate.of(2026, 9, 21),
                                new LessonReportContent(items, "x".repeat(10), items, items, "fim"),
                                1,
                                DocumentStatus.CONFERENCIA,
                                null));

        try (PDDocument pdf = Loader.loadPDF(document.bytes())) {
            int pages = pdf.getNumberOfPages();
            assertThat(pages).isGreaterThan(1);
            assertThat(text(document))
                    .contains("Página 1 de " + pages)
                    .contains("Página " + pages + " de " + pages);
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
