package br.com.felipeblaft.reports.domain.semester;

import br.com.felipeblaft.reports.domain.report.VersionContent;
import java.util.List;
import java.util.Objects;

/**
 * Seções 2 a 10 do relatório semestral (data-model.md §9), geradas e revisadas pela IA. Campos sem
 * base nos relatórios de aula ficam vazios (FR-020): textos normalizados, itens em branco
 * descartados e seções ausentes tratadas como vazias.
 */
public record SemesterReportContent(
        AttendanceAndStudy attendanceAndStudy,
        Technique technique,
        Musicality musicality,
        ReadingAndMemorization readingAndMemorization,
        Items attentionPoints,
        Items pedagogicalStrategies,
        FamilySupport familySupport,
        NextSemesterPlan nextSemesterPlan,
        String finalOpinion)
        implements VersionContent {

    public SemesterReportContent {
        attendanceAndStudy =
                Objects.requireNonNullElseGet(
                        attendanceAndStudy, () -> new AttendanceAndStudy(null, null, null, null));
        technique =
                Objects.requireNonNullElseGet(
                        technique, () -> new Technique(null, null, null, null));
        musicality =
                Objects.requireNonNullElseGet(
                        musicality, () -> new Musicality(null, null, null, null, null, null, null));
        readingAndMemorization =
                Objects.requireNonNullElseGet(
                        readingAndMemorization, () -> new ReadingAndMemorization(null, null));
        attentionPoints = Objects.requireNonNullElseGet(attentionPoints, () -> new Items(null));
        pedagogicalStrategies =
                Objects.requireNonNullElseGet(pedagogicalStrategies, () -> new Items(null));
        familySupport =
                Objects.requireNonNullElseGet(familySupport, () -> new FamilySupport(null, null));
        nextSemesterPlan =
                Objects.requireNonNullElseGet(
                        nextSemesterPlan,
                        () -> new NextSemesterPlan(null, null, null, null, null, null, null));
        finalOpinion = text(finalOpinion);
    }

    public static SemesterReportContent empty() {
        return new SemesterReportContent(null, null, null, null, null, null, null, null, null);
    }

    static List<String> items(List<String> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(i -> !i.isEmpty())
                .toList();
    }

    static String text(String text) {
        return text == null ? "" : text.strip();
    }

    public record AttendanceAndStudy(
            String lessonAttendance,
            String homeStudyRegularity,
            String engagement,
            String generalComment) {
        public AttendanceAndStudy {
            lessonAttendance = text(lessonAttendance);
            homeStudyRegularity = text(homeStudyRegularity);
            engagement = text(engagement);
            generalComment = text(generalComment);
        }
    }

    public record Technique(
            List<String> workedTopics,
            String technicalProgress,
            List<String> pointsToDevelop,
            List<String> completedStudies) {
        public Technique {
            workedTopics = items(workedTopics);
            technicalProgress = text(technicalProgress);
            pointsToDevelop = items(pointsToDevelop);
            completedStudies = items(completedStudies);
        }
    }

    public record Musicality(
            List<String> studiedRepertoire,
            String sound,
            String rhythm,
            String phrasing,
            String articulation,
            String style,
            String musicalComment) {
        public Musicality {
            studiedRepertoire = items(studiedRepertoire);
            sound = text(sound);
            rhythm = text(rhythm);
            phrasing = text(phrasing);
            articulation = text(articulation);
            style = text(style);
            musicalComment = text(musicalComment);
        }
    }

    public record ReadingAndMemorization(String assessment, String comment) {
        public ReadingAndMemorization {
            assessment = text(assessment);
            comment = text(comment);
        }
    }

    public record Items(List<String> items) {
        public Items {
            items = SemesterReportContent.items(items);
        }
    }

    public record FamilySupport(String presenceLevel, String teacherObservation) {
        public FamilySupport {
            presenceLevel = text(presenceLevel);
            teacherObservation = text(teacherObservation);
        }
    }

    public record NextSemesterPlan(
            List<String> technicalGoals,
            List<String> musicalGoals,
            List<String> studyHabits,
            List<String> proposedRepertoire,
            List<String> technicalStudies,
            List<String> readingAndTheory,
            String semesterProject) {
        public NextSemesterPlan {
            technicalGoals = items(technicalGoals);
            musicalGoals = items(musicalGoals);
            studyHabits = items(studyHabits);
            proposedRepertoire = items(proposedRepertoire);
            technicalStudies = items(technicalStudies);
            readingAndTheory = items(readingAndTheory);
            semesterProject = text(semesterProject);
        }
    }
}
