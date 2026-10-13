package br.com.felipeblaft.reports.saida.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.configuracao.CatalogoMensagens;
import br.com.felipeblaft.reports.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.reports.configuracao.SecoesRelatorioSemestral;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import br.com.felipeblaft.reports.port.PdfGenerationPort.Approval;
import br.com.felipeblaft.reports.port.PdfGenerationPort.DocumentStatus;
import br.com.felipeblaft.reports.port.PdfGenerationPort.GeneratedDocument;
import br.com.felipeblaft.reports.port.PdfGenerationPort.SemesterReportPdfModel;
import br.com.felipeblaft.reports.suporte.RelogioAjustavel;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PDF do semestral: 10 seções, Informações Gerais, quantidade consolidada, conferência e oficial.
 */
class PdfRelatorioSemestralIT {

    private static OpenHtmlToPdfGeracaoPdfAdaptador adaptador;

    private static final SemesterPeriod PERIODO = SemesterPeriod.of("2026-1");
    private static final GeneralInformation INFO =
            GeneralInformation.calculate(
                    "Matteo Ferreira",
                    LocalDate.of(2012, 5, 10),
                    "Piano",
                    "Clara Souza",
                    PERIODO,
                    8,
                    LocalDate.of(2026, 9, 21));
    private static final SemesterReportContent CONTEUDO =
            new SemesterReportContent(
                    new SemesterReportContent.AttendanceAndStudy(
                            "Frequentou com regularidade.", "Estudou em casa.", "Alto", null),
                    new SemesterReportContent.Technique(
                            List.of("Escalas maiores"),
                            "Evolução técnica notável.",
                            List.of(),
                            List.of()),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Semestre de evolução consistente.");

    @BeforeAll
    static void criar() {
        CatalogoMensagens catalogo = new CatalogoMensagens();
        FormatacaoMensagens formatacao = new FormatacaoMensagens(catalogo, new RelogioAjustavel());
        adaptador =
                new OpenHtmlToPdfGeracaoPdfAdaptador(
                        catalogo, formatacao, new SecoesRelatorioSemestral(catalogo, formatacao));
    }

    private static String texto(GeneratedDocument documento) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(documento.bytes())) {
            return new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
        }
    }

    @Test
    void conferenciaComDezSecoesInformacoesGeraisEFaixa() throws Exception {
        GeneratedDocument documento =
                adaptador.generateSemesterReport(
                        new SemesterReportPdfModel(
                                INFO, CONTEUDO, 1, DocumentStatus.CONFERENCIA, null));

        String texto = texto(documento);
        assertThat(texto)
                .contains("Relatório Semestral")
                .contains("Matteo Ferreira")
                .contains("Clara Souza")
                .contains("1º semestre 2026")
                .contains("Consolidação de 8 relatório(s) de aula aprovado(s) no período.")
                .contains("Informações Gerais")
                .contains("14 anos")
                .contains("01/01/2026 a 30/06/2026")
                .contains("Frequência e Estudo")
                .contains("Técnica")
                .contains("Musicalidade")
                .contains("Leitura e Memorização")
                .contains("Pontos de Atenção")
                .contains("Estratégias Pedagógicas")
                .contains("Acompanhamento Familiar")
                .contains("Planejamento do Próximo Semestre")
                .contains("Parecer Final")
                .contains("Semestre de evolução consistente.")
                .contains("—")
                .contains("Documento em conferência — não oficial")
                .doesNotContain("Aprovado por");
    }

    @Test
    void oficialComAprovacaoEAssinatura() throws Exception {
        GeneratedDocument documento =
                adaptador.generateSemesterReport(
                        new SemesterReportPdfModel(
                                INFO,
                                CONTEUDO,
                                2,
                                DocumentStatus.OFICIAL,
                                new Approval(
                                        "Clara Souza", Instant.parse("2026-07-02T21:42:00Z"), 2)));

        String texto = texto(documento);
        // como no modelo: a aprovação aparece só no bloco de assinatura, sem linha duplicada
        assertThat(texto)
                .contains("Clara Souza")
                .contains("Professor responsável")
                .contains("Aprovado em 02/07/2026 às 18:42")
                .contains("Versão 2")
                .contains("documento gerado a partir dos relatórios de aula do período.")
                .doesNotContain("Aprovado por")
                .doesNotContain("Documento em conferência");
        // cabeçalho corrido só a partir da página 2
        try (PDDocument pdf = Loader.loadPDF(documento.bytes())) {
            if (pdf.getNumberOfPages() > 1) {
                PDFTextStripper extrator = new PDFTextStripper();
                extrator.setStartPage(2);
                extrator.setEndPage(2);
                assertThat(extrator.getText(pdf))
                        .contains("Relatório Semestral · Matteo Ferreira · 1º semestre 2026");
            }
        }
    }
}
