package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.domain.semester.GeneralInformation;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * As 10 seções do semestral com os rótulos do catálogo (FR-003): a mesma estrutura alimenta o PDF e
 * o texto do bot. A seção 1 (Informações Gerais) vem do sistema; as seções 2 a 10, da IA.
 */
@Component
public class SemesterReportSections {

    /** Campo de uma seção: lista de itens ou texto (vazio quando não há informação). */
    public static final class Field {
        private final String label;
        private final List<String> items;
        private final String text;

        Field(String label, List<String> items, String text) {
            this.label = label;
            this.items = items == null ? List.of() : items;
            this.text = text == null ? "" : text;
        }

        public String getLabel() {
            return label;
        }

        public List<String> getItems() {
            return items;
        }

        public String getText() {
            return text;
        }

        public boolean empty() {
            return items.isEmpty() && text.isBlank();
        }
    }

    public static final class Section {
        private final int number;
        private final String title;
        private final List<Field> fields;

        Section(int number, String title, List<Field> fields) {
            this.number = number;
            this.title = title;
            this.fields = fields;
        }

        public int getNumber() {
            return number;
        }

        public String getTitle() {
            return title;
        }

        public List<Field> getFields() {
            return fields;
        }
    }

    private final MessageCatalog catalog;
    private final MessageFormatting formatting;

    public SemesterReportSections(MessageCatalog catalog, MessageFormatting formatting) {
        this.catalog = catalog;
        this.formatting = formatting;
    }

    public String periodLabel(String periodKey) {
        SemesterPeriod period = SemesterPeriod.of(periodKey);
        return catalog.text(
                "SEMESTRE_ROTULO", Map.of("numero", period.semester(), "ano", period.year()));
    }

    /** Seção 1 (sistema) seguida das seções 2 a 10 (IA). */
    public List<Section> sections(GeneralInformation info, SemesterReportContent content) {
        List<Section> sections = new ArrayList<>();
        sections.add(
                new Section(
                        1,
                        catalog.text("SECAO_INFORMACOES_GERAIS"),
                        List.of(
                                text("INFO_NOME", info.name()),
                                text(
                                        "INFO_IDADE",
                                        catalog.text(
                                                "INFO_IDADE_VALOR", Map.of("idade", info.age()))),
                                text("INFO_INSTRUMENTO", info.instrument()),
                                text("INFO_PROFESSOR", info.teacher()),
                                text(
                                        "INFO_PERIODO",
                                        periodLabel(info.periodKey())
                                                + " ("
                                                + formatting.data(info.periodStart())
                                                + " a "
                                                + formatting.data(info.periodEnd())
                                                + ")"),
                                text(
                                        "INFO_AULAS_CONSIDERADAS",
                                        Integer.toString(info.consideredLessonCount())))));
        sections.addAll(contentSections(content));
        return sections;
    }

    /** Seções 2 a 10. */
    public List<Section> contentSections(SemesterReportContent c) {
        var f = c.attendanceAndStudy();
        var t = c.technique();
        var m = c.musicality();
        var l = c.readingAndMemorization();
        var a = c.familySupport();
        var p = c.nextSemesterPlan();
        return List.of(
                new Section(
                        2,
                        catalog.text("SECAO_FREQUENCIA_E_ESTUDO"),
                        List.of(
                                text("CAMPO_FREQUENCIA_AULAS", f.lessonAttendance()),
                                text("CAMPO_REGULARIDADE_ESTUDO_EM_CASA", f.homeStudyRegularity()),
                                text("CAMPO_ENGAJAMENTO", f.engagement()),
                                text("CAMPO_COMENTARIO_GERAL", f.generalComment()))),
                new Section(
                        3,
                        catalog.text("SECAO_TECNICA"),
                        List.of(
                                list("CAMPO_CONTEUDOS_TRABALHADOS", t.workedTopics()),
                                text("CAMPO_EVOLUCAO_TECNICA", t.technicalProgress()),
                                list("CAMPO_PONTOS_A_DESENVOLVER", t.pointsToDevelop()),
                                list("CAMPO_ESTUDOS_REALIZADOS", t.completedStudies()))),
                new Section(
                        4,
                        catalog.text("SECAO_MUSICALIDADE"),
                        List.of(
                                list("CAMPO_REPERTORIO_ESTUDADO", m.studiedRepertoire()),
                                text("CAMPO_SOM", m.sound()),
                                text("CAMPO_RITMO", m.rhythm()),
                                text("CAMPO_FRASEADO", m.phrasing()),
                                text("CAMPO_ARTICULACAO", m.articulation()),
                                text("CAMPO_ESTILO", m.style()),
                                text("CAMPO_COMENTARIO_MUSICAL", m.musicalComment()))),
                new Section(
                        5,
                        catalog.text("SECAO_LEITURA_E_MEMORIZACAO"),
                        List.of(
                                text("CAMPO_AVALIACAO", l.assessment()),
                                text("CAMPO_COMENTARIO", l.comment()))),
                new Section(
                        6,
                        catalog.text("SECAO_PONTOS_DE_ATENCAO"),
                        List.of(new Field(null, c.attentionPoints().items(), ""))),
                new Section(
                        7,
                        catalog.text("SECAO_ESTRATEGIAS_PEDAGOGICAS"),
                        List.of(new Field(null, c.pedagogicalStrategies().items(), ""))),
                new Section(
                        8,
                        catalog.text("SECAO_ACOMPANHAMENTO_FAMILIAR"),
                        List.of(
                                text("CAMPO_NIVEL_DE_PRESENCA", a.presenceLevel()),
                                text("CAMPO_OBSERVACAO_DO_PROFESSOR", a.teacherObservation()))),
                new Section(
                        9,
                        catalog.text("SECAO_PLANEJAMENTO_PROXIMO_SEMESTRE"),
                        List.of(
                                list("CAMPO_METAS_TECNICAS", p.technicalGoals()),
                                list("CAMPO_METAS_MUSICAIS", p.musicalGoals()),
                                list("CAMPO_HABITOS_DE_ESTUDO", p.studyHabits()),
                                list("CAMPO_REPERTORIO_PROPOSTO", p.proposedRepertoire()),
                                list("CAMPO_ESTUDOS_TECNICOS", p.technicalStudies()),
                                list("CAMPO_LEITURA_E_TEORIA", p.readingAndTheory()),
                                text("CAMPO_PROJETO_DO_SEMESTRE", p.semesterProject()))),
                new Section(
                        10,
                        catalog.text("SECAO_PARECER_FINAL"),
                        List.of(new Field(null, List.of(), c.finalOpinion()))));
    }

    private Field text(String label, String value) {
        return new Field(catalog.text(label), List.of(), value);
    }

    private Field list(String label, List<String> items) {
        return new Field(catalog.text(label), items, "");
    }
}
