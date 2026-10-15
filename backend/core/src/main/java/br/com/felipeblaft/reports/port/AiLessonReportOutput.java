package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import java.util.List;

/**
 * Saída das tarefas de aula (contracts/portas-provedores.md §6.1). Campos ausentes viram vazios;
 * {@code semAlteracaoDeConteudo} e {@code pedidoDeAcao} só valem na revisão.
 */
public record AiLessonReportOutput(
        List<String> workedTopics,
        String progress,
        List<String> difficulties,
        List<String> proposedActivities,
        String observations,
        List<String> pendingQuestions,
        Boolean noContentChange,
        String actionRequest) {

    public static final String NONE = "NONE";

    public AiLessonReportOutput {
        pendingQuestions = pendingQuestions == null ? List.of() : pendingQuestions;
    }

    public LessonReportContent content() {
        return new LessonReportContent(
                workedTopics, progress, difficulties, proposedActivities, observations);
    }

    public boolean withoutChange() {
        return Boolean.TRUE.equals(noContentChange);
    }

    /** O pedido era para aprovar ou cancelar: nunca é executado (FR-027a). */
    public boolean indicatedActionRequest() {
        return actionRequest != null && !NONE.equals(actionRequest);
    }
}
