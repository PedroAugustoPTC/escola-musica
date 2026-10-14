package br.com.felipeblaft.reports.outbound.pdf;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.config.MessageFormatting;
import br.com.felipeblaft.reports.config.SemesterReportSections;
import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.port.PdfGenerationPort;
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
public class OpenHtmlToPdfGenerationAdapter implements PdfGenerationPort {

    private static final String ROOT = "pdf/";

    private record Font(String file, String family, int weight, FontStyle style) {}

    private static final List<Font> FONTS =
            List.of(
                    new Font("Cinzel-Regular.ttf", "Cinzel", 400, FontStyle.NORMAL),
                    new Font("Cinzel-Bold.ttf", "Cinzel", 700, FontStyle.NORMAL),
                    new Font(
                            "CormorantGaramond-Medium.ttf",
                            "Cormorant Garamond",
                            500,
                            FontStyle.NORMAL),
                    new Font(
                            "CormorantGaramond-SemiBold.ttf",
                            "Cormorant Garamond",
                            600,
                            FontStyle.NORMAL),
                    new Font(
                            "CormorantGaramond-Bold.ttf",
                            "Cormorant Garamond",
                            700,
                            FontStyle.NORMAL),
                    new Font(
                            "CormorantGaramond-Italic.ttf",
                            "Cormorant Garamond",
                            500,
                            FontStyle.ITALIC),
                    new Font("SourceSans3-Regular.ttf", "Source Sans 3", 400, FontStyle.NORMAL),
                    new Font("SourceSans3-Semibold.ttf", "Source Sans 3", 600, FontStyle.NORMAL),
                    new Font("SourceSans3-Bold.ttf", "Source Sans 3", 700, FontStyle.NORMAL),
                    new Font("SourceSans3-It.ttf", "Source Sans 3", 400, FontStyle.ITALIC));

    private final MessageCatalog catalog;
    private final SemesterReportSections semesterSections;
    private final MessageFormatting formatting;
    private final TemplateEngine motor;
    private final Map<Font, byte[]> fonts = new HashMap<>();
    private final String styles;
    private final Map<String, String> images;

    public OpenHtmlToPdfGenerationAdapter(MessageCatalog catalog, MessageFormatting formatting) {
        this(catalog, formatting, new SemesterReportSections(catalog, formatting));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OpenHtmlToPdfGenerationAdapter(
            MessageCatalog catalog,
            MessageFormatting formatting,
            SemesterReportSections semesterSections) {
        this.semesterSections = semesterSections;
        this.catalog = catalog;
        this.formatting = formatting;
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(ROOT);
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        SpringTemplateEngine motorSpring = new SpringTemplateEngine();
        motorSpring.setTemplateResolver(resolver);
        this.motor = motorSpring;
        FONTS.forEach(
                font ->
                        fonts.put(
                                font,
                                withoutReplacements(resource(ROOT + "fonts/" + font.file()))));
        this.styles = new String(resource(ROOT + "pdf-styles.css"), StandardCharsets.UTF_8);
        this.images =
                Map.of(
                        "logo", image("fb-logo-black.png"),
                        "divider", image("fb-divider-black.png"),
                        "wordmark", image("fb-wordmark-black.png"));
    }

    @Override
    public GeneratedDocument generateLessonReport(LessonReportPdfModel model) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("title", catalog.text("PDF_LESSON_TITLE"));
        variables.put("student", model.studentName());
        variables.put("teacher", model.teacherName());
        variables.put("data", formatting.data(model.lessonDate()));
        variables.put("sections", sections(model.content()));
        variables.put(
                "footerText", catalog.text("PDF_FOOTER") + " — relatório pedagógico de aula.");
        fillStatus(variables, model.status(), model.approval(), model.version());
        return generate("lesson-report", variables);
    }

    @Override
    public GeneratedDocument generateSemesterReport(SemesterReportPdfModel model) {
        GeneralInformation info = model.generalInformation();
        Map<String, Object> variables = new HashMap<>();
        variables.put("title", catalog.text("PDF_SEMESTER_TITLE"));
        variables.put("student", info.name());
        variables.put("teacher", info.teacher());
        variables.put(
                "period",
                formatting.data(info.periodStart()) + " a " + formatting.data(info.periodEnd()));
        variables.put("periodLabel", semesterSections.periodLabel(info.periodKey()));
        variables.put(
                "consolidation",
                catalog.text(
                        "LESSON_CONSOLIDATION", Map.of("quantity", info.consideredLessonCount())));
        variables.put("sections", semesterSections.sections(info, model.content()));
        variables.put(
                "footerText",
                catalog.text("PDF_FOOTER")
                        + " — documento gerado a partir dos relatórios de aula do período.");
        fillStatus(variables, model.status(), model.approval(), model.version());
        variables.put(
                "approvedAt",
                model.approval() == null
                        ? ""
                        : catalog.text(
                                "APPROVED_AT",
                                Map.of(
                                        "data", formatting.data(model.approval().approvedAt()),
                                        "hora", formatting.hora(model.approval().approvedAt()))));
        return generate("semester-report", variables);
    }

    /** Variáveis comuns de situação (faixa de conferência ou linha de aprovação). */
    void fillStatus(
            Map<String, Object> variables, DocumentStatus status, Approval approval, int version) {
        boolean review = status == DocumentStatus.REVIEW;
        variables.put("review", review);
        variables.put("banner", catalog.text("PDF_REVIEW_BAND"));
        variables.put(
                "approval",
                review
                        ? null
                        : catalog.text(
                                "PDF_APPROVAL",
                                Map.of(
                                        "teacher", approval.teacherName(),
                                        "data", formatting.data(approval.approvedAt()),
                                        "hora", formatting.hora(approval.approvedAt()),
                                        "version", approval.version())));
        variables.put("versionText", "Versão " + version);
    }

    /** Renderiza o template e converte em PDF, com hash e tamanho para o registro do documento. */
    GeneratedDocument generate(String template, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        context.setVariable("styles", styles);
        context.setVariable("images", images);
        context.setVariable("paginationStyle", paginationStyle());
        String html = motor.process(template, context);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder constructor = new PdfRendererBuilder();
            constructor.useFastMode();
            constructor.withHtmlContent(html, null);
            fonts.forEach(
                    (font, bytes) ->
                            constructor.useFont(
                                    () -> new ByteArrayInputStream(bytes),
                                    font.family(),
                                    font.weight(),
                                    font.style(),
                                    true));
            constructor.toStream(output);
            constructor.run();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        byte[] bytes = output.toByteArray();
        return new GeneratedDocument(bytes, sha256(bytes), bytes.length);
    }

    private List<Section> sections(LessonReportContent content) {
        List<Section> sections = new ArrayList<>();
        sections.add(list("WORKED_TOPICS_SECTION", content.workedTopics()));
        sections.add(text("PROGRESS_SECTION", content.progress()));
        sections.add(list("DIFFICULTIES_SECTION", content.difficulties()));
        sections.add(list("PROPOSED_ACTIVITIES_SECTION", content.proposedActivities()));
        sections.add(text("OBSERVATIONS_SECTION", content.observations()));
        return sections;
    }

    Section list(String key, List<String> items) {
        return new Section(catalog.text(key), items, catalog.text("EMPTY_SECTION"));
    }

    Section text(String key, String text) {
        return new Section(
                catalog.text(key),
                List.of(),
                text == null || text.isBlank() ? catalog.text("EMPTY_SECTION") : text);
    }

    /** Seção do template: itens em lista ou um parágrafo; vazia mostra "—" (FR-020). */
    public static final class Section {
        private final String title;
        private final List<String> items;
        private final String text;

        Section(String title, List<String> items, String text) {
            this.title = title;
            this.items = items == null ? List.of() : items;
            this.text = text;
        }

        public String getTitle() {
            return title;
        }

        public List<String> getItems() {
            return items;
        }

        public String getText() {
            return text;
        }
    }

    /** Numeração "Página X de Y" toda gerada pelo CSS paginado, com o rótulo do catálogo. */
    private String paginationStyle() {
        String label = catalog.text("PDF_PAGE").replace("\\", "").replace("\"", "");
        return ".pagination::after { content: \""
                + label
                + " \" counter(page) \" de \" counter(pages); }";
    }

    /**
     * Desliga a tabela GSUB da fonte (renomeia a entrada no diretório de tabelas): sem ela o PDFBox
     * não troca "fi"/"ft" por ligaduras, cujo glifo não tem mapeamento Unicode e quebraria a cópia
     * do texto, a busca e os leitores de tela. O desenho das letras não muda.
     */
    static byte[] withoutReplacements(byte[] font) {
        byte[] copy = font.clone();
        int tables = ((copy[4] & 0xFF) << 8) | (copy[5] & 0xFF);
        for (int i = 0; i < tables; i++) {
            int registration = 12 + i * 16;
            if (copy[registration] == 'G'
                    && copy[registration + 1] == 'S'
                    && copy[registration + 2] == 'U'
                    && copy[registration + 3] == 'B') {
                copy[registration] = 'X';
            }
        }
        return copy;
    }

    private String image(String file) {
        return "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(resource(ROOT + "images/" + file));
    }

    private static byte[] resource(String path) {
        try (InputStream input =
                Objects.requireNonNull(
                        OpenHtmlToPdfGenerationAdapter.class
                                .getClassLoader()
                                .getResourceAsStream(path),
                        "recurso de PDF ausente: " + path)) {
            return input.readAllBytes();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
