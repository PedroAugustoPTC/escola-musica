package br.com.felipeblaft.relatorios.saida.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.relatorios.configuracao.CatalogoMensagens;
import br.com.felipeblaft.relatorios.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.relatorios.configuracao.SecoesRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.ConteudoRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.semestral.InformacoesGerais;
import br.com.felipeblaft.relatorios.dominio.semestral.PeriodoSemestral;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.Aprovacao;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.DocumentoGerado;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.ModeloPdfRelatorioSemestral;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta.SituacaoDocumento;
import br.com.felipeblaft.relatorios.suporte.RelogioAjustavel;
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

    private static final PeriodoSemestral PERIODO = PeriodoSemestral.de("2026-1");
    private static final InformacoesGerais INFO =
            InformacoesGerais.calcular(
                    "Matteo Ferreira",
                    LocalDate.of(2012, 5, 10),
                    "Piano",
                    "Clara Souza",
                    PERIODO,
                    8,
                    LocalDate.of(2026, 9, 21));
    private static final ConteudoRelatorioSemestral CONTEUDO =
            new ConteudoRelatorioSemestral(
                    new ConteudoRelatorioSemestral.FrequenciaEEstudo(
                            "Frequentou com regularidade.", "Estudou em casa.", "Alto", null),
                    new ConteudoRelatorioSemestral.Tecnica(
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

    private static String texto(DocumentoGerado documento) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(documento.bytes())) {
            return new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
        }
    }

    @Test
    void conferenciaComDezSecoesInformacoesGeraisEFaixa() throws Exception {
        DocumentoGerado documento =
                adaptador.gerarRelatorioSemestral(
                        new ModeloPdfRelatorioSemestral(
                                INFO, CONTEUDO, 1, SituacaoDocumento.CONFERENCIA, null));

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
        DocumentoGerado documento =
                adaptador.gerarRelatorioSemestral(
                        new ModeloPdfRelatorioSemestral(
                                INFO,
                                CONTEUDO,
                                2,
                                SituacaoDocumento.OFICIAL,
                                new Aprovacao(
                                        "Clara Souza", Instant.parse("2026-07-02T21:42:00Z"), 2)));

        assertThat(texto(documento))
                .contains("Aprovado por Clara Souza em 02/07/2026 às 18:42 · versão 2")
                .contains("Aprovado em 02/07/2026 às 18:42")
                .doesNotContain("Documento em conferência");
    }
}
