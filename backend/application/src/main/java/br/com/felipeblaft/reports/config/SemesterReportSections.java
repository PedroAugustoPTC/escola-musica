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
                "SEMESTER_LABEL_TEXT", Map.of("number", period.semester(), "year", period.year()));
    }

    /** Seção 1 (sistema) seguida das seções 2 a 10 (IA). */
    public List<Section> sections(GeneralInformation info, SemesterReportContent content) {
        List<Section> sections = new ArrayList<>();
        sections.add(
                new Section(
                        1,
                        catalog.text("GENERAL_INFORMATION_SECTION"),
                        List.of(
                                text("INFO_NAME", info.name()),
                                text(
                                        "INFO_AGE",
                                        catalog.text("AGE_VALUE_INFO", Map.of("age", info.age()))),
                                text("INFO_INSTRUMENT", info.instrument()),
                                text("INFO_TEACHER", info.teacher()),
                                text(
                                        "INFO_PERIOD",
                                        periodLabel(info.periodKey())
                                                + " ("
                                                + formatting.data(info.periodStart())
                                                + " a "
                                                + formatting.data(info.periodEnd())
                                                + ")"),
                                text(
                                        "CONSIDERED_LESSONS_INFO",
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
                        catalog.text("ATTENDANCE_AND_STUDY_SECTION"),
                        List.of(
                                text("LESSON_ATTENDANCE_FIELD", f.lessonAttendance()),
                                text("HOME_STUDY_REGULARITY_FIELD", f.homeStudyRegularity()),
                                text("ENGAGEMENT_FIELD", f.engagement()),
                                text("GENERAL_COMMENT_FIELD", f.generalComment()))),
                new Section(
                        3,
                        catalog.text("TECHNIQUE_SECTION"),
                        List.of(
                                list("WORKED_TOPICS_FIELD", t.workedTopics()),
                                text("TECHNICAL_PROGRESS_FIELD", t.technicalProgress()),
                                list("POINTS_TO_DEVELOP_FIELD", t.pointsToDevelop()),
                                list("PERFORMED_STUDIES_FIELD", t.completedStudies()))),
                new Section(
                        4,
                        catalog.text("MUSICALITY_SECTION"),
                        List.of(
                                list("STUDIED_REPERTOIRE_FIELD", m.studiedRepertoire()),
                                text("SOUND_FIELD", m.sound()),
                                text("RHYTHM_FIELD", m.rhythm()),
                                text("PHRASING_FIELD", m.phrasing()),
                                text("ARTICULATION_FIELD", m.articulation()),
                                text("STYLE_FIELD", m.style()),
                                text("MUSICAL_COMMENT_FIELD", m.musicalComment()))),
                new Section(
                        5,
                        catalog.text("READING_AND_MEMORIZATION_SECTION"),
                        List.of(
                                text("ASSESSMENT_FIELD", l.assessment()),
                                text("COMMENT_FIELD", l.comment()))),
                new Section(
                        6,
                        catalog.text("ATTENTION_POINTS_SECTION"),
                        List.of(new Field(null, c.attentionPoints().items(), ""))),
                new Section(
                        7,
                        catalog.text("PEDAGOGICAL_STRATEGIES_SECTION"),
                        List.of(new Field(null, c.pedagogicalStrategies().items(), ""))),
                new Section(
                        8,
                        catalog.text("FAMILY_SUPPORT_SECTION"),
                        List.of(
                                text("PRESENCE_LEVEL_FIELD", a.presenceLevel()),
                                text("TEACHER_OBSERVATION_FIELD", a.teacherObservation()))),
                new Section(
                        9,
                        catalog.text("NEXT_SEMESTER_PLAN_SECTION"),
                        List.of(
                                list("TECHNICAL_GOALS_FIELD", p.technicalGoals()),
                                list("MUSICAL_GOALS_FIELD", p.musicalGoals()),
                                list("STUDY_HABITS_FIELD", p.studyHabits()),
                                list("PROPOSED_REPERTOIRE_FIELD", p.proposedRepertoire()),
                                list("TECHNICAL_STUDIES_FIELD", p.technicalStudies()),
                                list("READING_AND_THEORY_FIELD", p.readingAndTheory()),
                                text("SEMESTER_PROJECT_FIELD", p.semesterProject()))),
                new Section(
                        10,
                        catalog.text("FINAL_OPINION_SECTION"),
                        List.of(new Field(null, List.of(), c.finalOpinion()))));
    }

    private Field text(String label, String value) {
        return new Field(catalog.text(label), List.of(), value);
    }

    private Field list(String label, List<String> items) {
        return new Field(catalog.text(label), items, "");
    }
}
