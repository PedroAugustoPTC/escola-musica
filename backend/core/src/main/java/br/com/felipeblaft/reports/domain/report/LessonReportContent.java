package br.com.felipeblaft.reports.domain.report;

import java.util.List;
import java.util.Objects;

/**
 * As cinco seções do relatório de aula (FR-019). Campo sem informação fica vazio, nunca inventado
 * (FR-020): textos são normalizados e itens em branco descartados.
 */
public record LessonReportContent(
        List<String> workedTopics,
        String progress,
        List<String> difficulties,
        List<String> proposedActivities,
        String observations)
        implements VersionContent {

    public LessonReportContent {
        workedTopics = items(workedTopics);
        progress = text(progress);
        difficulties = items(difficulties);
        proposedActivities = items(proposedActivities);
        observations = text(observations);
    }

    public static LessonReportContent empty() {
        return new LessonReportContent(null, null, null, null, null);
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

    public boolean isEmpty() {
        return workedTopics.isEmpty()
                && progress.isEmpty()
                && difficulties.isEmpty()
                && proposedActivities.isEmpty()
                && observations.isEmpty();
    }
}
