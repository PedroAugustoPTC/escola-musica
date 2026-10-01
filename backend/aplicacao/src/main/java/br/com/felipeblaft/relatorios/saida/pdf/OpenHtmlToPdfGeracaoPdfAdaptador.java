package br.com.felipeblaft.relatorios.saida.pdf;

import br.com.felipeblaft.relatorios.configuracao.CatalogoMensagens;
import br.com.felipeblaft.relatorios.configuracao.FormatacaoMensagens;
import br.com.felipeblaft.relatorios.configuracao.SecoesRelatorioSemestral;
import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import br.com.felipeblaft.relatorios.dominio.semestral.InformacoesGerais;
import br.com.felipeblaft.relatorios.porta.GeracaoPdfPorta;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * PDF com a identidade visual (R-10, FR-049): template Thymeleaf em XHTML convertido pelo
 * OpenHTMLtoPDF, com fontes TTF e imagens embutidas a partir do classpath. Versões não aprovadas
 * levam a faixa de conferência; o oficial leva quem aprovou, quando e qual versão (FR-030).
 */
@Component
public class OpenHtmlToPdfGeracaoPdfAdaptador implements GeracaoPdfPorta {

    private static final String RAIZ = "pdf/";

    private record Fonte(String arquivo, String familia, int peso, FontStyle estilo) {}

    private static final List<Fonte> FONTES =
            List.of(
                    new Fonte("Cinzel-Regular.ttf", "Cinzel", 400, FontStyle.NORMAL),
                    new Fonte("Cinzel-Bold.ttf", "Cinzel", 700, FontStyle.NORMAL),
                    new Fonte(
                            "CormorantGaramond-Medium.ttf",
                            "Cormorant Garamond",
                            500,
                            FontStyle.NORMAL),
                    new Fonte(
                            "CormorantGaramond-SemiBold.ttf",
                            "Cormorant Garamond",
                            600,
                            FontStyle.NORMAL),
                    new Fonte(
                            "CormorantGaramond-Bold.ttf",
                            "Cormorant Garamond",
                            700,
                            FontStyle.NORMAL),
                    new Fonte(
                            "CormorantGaramond-Italic.ttf",
                            "Cormorant Garamond",
                            500,
                            FontStyle.ITALIC),
                    new Fonte("SourceSans3-Regular.ttf", "Source Sans 3", 400, FontStyle.NORMAL),
                    new Fonte("SourceSans3-Semibold.ttf", "Source Sans 3", 600, FontStyle.NORMAL),
                    new Fonte("SourceSans3-Bold.ttf", "Source Sans 3", 700, FontStyle.NORMAL),
                    new Fonte("SourceSans3-It.ttf", "Source Sans 3", 400, FontStyle.ITALIC));

    private final CatalogoMensagens catalogo;
    private final SecoesRelatorioSemestral secoesSemestral;
    private final FormatacaoMensagens formatacao;
    private final TemplateEngine motor;
    private final Map<Fonte, byte[]> fontes = new HashMap<>();
    private final String estilos;
    private final Map<String, String> imagens;

    public OpenHtmlToPdfGeracaoPdfAdaptador(
            CatalogoMensagens catalogo, FormatacaoMensagens formatacao) {
        this(catalogo, formatacao, new SecoesRelatorioSemestral(catalogo, formatacao));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OpenHtmlToPdfGeracaoPdfAdaptador(
            CatalogoMensagens catalogo,
            FormatacaoMensagens formatacao,
            SecoesRelatorioSemestral secoesSemestral) {
        this.secoesSemestral = secoesSemestral;
        this.catalogo = catalogo;
        this.formatacao = formatacao;
        ClassLoaderTemplateResolver resolvedor = new ClassLoaderTemplateResolver();
        resolvedor.setPrefix(RAIZ);
        resolvedor.setSuffix(".html");
        resolvedor.setTemplateMode(TemplateMode.XML);
        resolvedor.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolvedor.setCacheable(true);
        SpringTemplateEngine motorSpring = new SpringTemplateEngine();
        motorSpring.setTemplateResolver(resolvedor);
        this.motor = motorSpring;
        FONTES.forEach(
                fonte ->
                        fontes.put(
                                fonte,
                                semSubstituicoes(recurso(RAIZ + "fontes/" + fonte.arquivo()))));
        this.estilos = new String(recurso(RAIZ + "estilos-pdf.css"), StandardCharsets.UTF_8);
        this.imagens =
                Map.of(
                        "logo", imagem("fb-logo-preto.png"),
                        "filete", imagem("fb-filete-preto.png"),
                        "wordmark", imagem("fb-wordmark-preto.png"));
    }

    @Override
    public DocumentoGerado gerarRelatorioAula(ModeloPdfRelatorioAula modelo) {
        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("titulo", catalogo.texto("PDF_TITULO_AULA"));
        variaveis.put("aluno", modelo.alunoNome());
        variaveis.put("professor", modelo.professorNome());
        variaveis.put("data", formatacao.data(modelo.dataAula()));
        variaveis.put("secoes", secoes(modelo.conteudo()));
        variaveis.put(
                "rodapeTexto", catalogo.texto("PDF_RODAPE") + " — relatório pedagógico de aula.");
        preencherSituacao(variaveis, modelo.situacao(), modelo.aprovacao(), modelo.versao());
        return gerar("relatorio-aula", variaveis);
    }

    @Override
    public DocumentoGerado gerarRelatorioSemestral(ModeloPdfRelatorioSemestral modelo) {
        InformacoesGerais info = modelo.informacoesGerais();
        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("titulo", catalogo.texto("PDF_TITULO_SEMESTRAL"));
        variaveis.put("aluno", info.nome());
        variaveis.put("professor", info.professor());
        variaveis.put(
                "periodo",
                formatacao.data(info.periodoInicio()) + " a " + formatacao.data(info.periodoFim()));
        variaveis.put("periodoRotulo", secoesSemestral.rotuloPeriodo(info.periodoChave()));
        variaveis.put(
                "consolidacao",
                catalogo.texto(
                        "CONSOLIDACAO_DE_AULAS",
                        Map.of("quantidade", info.quantidadeAulasConsideradas())));
        variaveis.put("secoes", secoesSemestral.secoes(info, modelo.conteudo()));
        variaveis.put(
                "rodapeTexto",
                catalogo.texto("PDF_RODAPE")
                        + " — documento gerado a partir dos relatórios de aula do período.");
        preencherSituacao(variaveis, modelo.situacao(), modelo.aprovacao(), modelo.versao());
        variaveis.put(
                "aprovadoEm",
                modelo.aprovacao() == null
                        ? ""
                        : catalogo.texto(
                                "APROVADO_EM",
                                Map.of(
                                        "data", formatacao.data(modelo.aprovacao().aprovadoEm()),
                                        "hora", formatacao.hora(modelo.aprovacao().aprovadoEm()))));
        return gerar("relatorio-semestral", variaveis);
    }

    /** Variáveis comuns de situação (faixa de conferência ou linha de aprovação). */
    void preencherSituacao(
            Map<String, Object> variaveis,
            SituacaoDocumento situacao,
            Aprovacao aprovacao,
            int versao) {
        boolean conferencia = situacao == SituacaoDocumento.CONFERENCIA;
        variaveis.put("conferencia", conferencia);
        variaveis.put("faixa", catalogo.texto("PDF_FAIXA_CONFERENCIA"));
        variaveis.put(
                "aprovacao",
                conferencia
                        ? null
                        : catalogo.texto(
                                "PDF_APROVACAO",
                                Map.of(
                                        "professor", aprovacao.professorNome(),
                                        "data", formatacao.data(aprovacao.aprovadoEm()),
                                        "hora", formatacao.hora(aprovacao.aprovadoEm()),
                                        "versao", aprovacao.versao())));
        variaveis.put("versaoTexto", "Versão " + versao);
    }

    /** Renderiza o template e converte em PDF, com hash e tamanho para o registro do documento. */
    DocumentoGerado gerar(String template, Map<String, Object> variaveis) {
        Context contexto = new Context();
        contexto.setVariables(variaveis);
        contexto.setVariable("estilos", estilos);
        contexto.setVariable("imagens", imagens);
        contexto.setVariable("estiloPaginacao", estiloPaginacao());
        String html = motor.process(template, contexto);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder construtor = new PdfRendererBuilder();
            construtor.useFastMode();
            construtor.withHtmlContent(html, null);
            fontes.forEach(
                    (fonte, bytes) ->
                            construtor.useFont(
                                    () -> new ByteArrayInputStream(bytes),
                                    fonte.familia(),
                                    fonte.peso(),
                                    fonte.estilo(),
                                    true));
            construtor.toStream(saida);
            construtor.run();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
        byte[] bytes = saida.toByteArray();
        return new DocumentoGerado(bytes, sha256(bytes), bytes.length);
    }

    private List<Secao> secoes(ConteudoRelatorioAula conteudo) {
        List<Secao> secoes = new ArrayList<>();
        secoes.add(lista("SECAO_CONTEUDOS_TRABALHADOS", conteudo.conteudosTrabalhados()));
        secoes.add(texto("SECAO_EVOLUCAO", conteudo.evolucao()));
        secoes.add(lista("SECAO_DIFICULDADES", conteudo.dificuldades()));
        secoes.add(lista("SECAO_ATIVIDADES_PROPOSTAS", conteudo.atividadesPropostas()));
        secoes.add(texto("SECAO_OBSERVACOES", conteudo.observacoes()));
        return secoes;
    }

    Secao lista(String chave, List<String> itens) {
        return new Secao(catalogo.texto(chave), itens, catalogo.texto("SECAO_VAZIA"));
    }

    Secao texto(String chave, String texto) {
        return new Secao(
                catalogo.texto(chave),
                List.of(),
                texto == null || texto.isBlank() ? catalogo.texto("SECAO_VAZIA") : texto);
    }

    /** Seção do template: itens em lista ou um parágrafo; vazia mostra "—" (FR-020). */
    public static final class Secao {
        private final String titulo;
        private final List<String> itens;
        private final String texto;

        Secao(String titulo, List<String> itens, String texto) {
            this.titulo = titulo;
            this.itens = itens == null ? List.of() : itens;
            this.texto = texto;
        }

        public String getTitulo() {
            return titulo;
        }

        public List<String> getItens() {
            return itens;
        }

        public String getTexto() {
            return texto;
        }
    }

    /** Numeração "Página X de Y" toda gerada pelo CSS paginado, com o rótulo do catálogo. */
    private String estiloPaginacao() {
        String rotulo = catalogo.texto("PDF_PAGINA").replace("\\", "").replace("\"", "");
        return ".paginacao::after { content: \""
                + rotulo
                + " \" counter(page) \" de \" counter(pages); }";
    }

    /**
     * Desliga a tabela GSUB da fonte (renomeia a entrada no diretório de tabelas): sem ela o PDFBox
     * não troca "fi"/"ft" por ligaduras, cujo glifo não tem mapeamento Unicode e quebraria a cópia
     * do texto, a busca e os leitores de tela. O desenho das letras não muda.
     */
    static byte[] semSubstituicoes(byte[] fonte) {
        byte[] copia = fonte.clone();
        int tabelas = ((copia[4] & 0xFF) << 8) | (copia[5] & 0xFF);
        for (int i = 0; i < tabelas; i++) {
            int registro = 12 + i * 16;
            if (copia[registro] == 'G'
                    && copia[registro + 1] == 'S'
                    && copia[registro + 2] == 'U'
                    && copia[registro + 3] == 'B') {
                copia[registro] = 'X';
            }
        }
        return copia;
    }

    private String imagem(String arquivo) {
        return "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(recurso(RAIZ + "imagens/" + arquivo));
    }

    private static byte[] recurso(String caminho) {
        try (InputStream entrada =
                Objects.requireNonNull(
                        OpenHtmlToPdfGeracaoPdfAdaptador.class
                                .getClassLoader()
                                .getResourceAsStream(caminho),
                        "recurso de PDF ausente: " + caminho)) {
            return entrada.readAllBytes();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException(erro);
        }
    }
}
