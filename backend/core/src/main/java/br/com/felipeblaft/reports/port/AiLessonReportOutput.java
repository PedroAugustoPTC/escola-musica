package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import java.util.List;

/**
 * Saída das tarefas de aula (contracts/portas-provedores.md §6.1). Campos ausentes viram vazios;
 * {@code semAlteracaoDeConteudo} e {@code pedidoDeAcao} só valem na revisão.
 */
public record AiLessonReportOutput(
        List<String> conteudosTrabalhados,
        String evolucao,
        List<String> dificuldades,
        List<String> atividadesPropostas,
        String observacoes,
        List<String> perguntasPendentes,
        Boolean semAlteracaoDeConteudo,
        String pedidoDeAcao) {

    public static final String NONE = "NENHUM";

    public AiLessonReportOutput {
        perguntasPendentes = perguntasPendentes == null ? List.of() : perguntasPendentes;
    }

    public LessonReportContent content() {
        return new LessonReportContent(
                conteudosTrabalhados, evolucao, dificuldades, atividadesPropostas, observacoes);
    }

    public boolean withoutChange() {
        return Boolean.TRUE.equals(semAlteracaoDeConteudo);
    }

    /** O pedido era para aprovar ou cancelar: nunca é executado (FR-027a). */
    public boolean indicatedActionRequest() {
        return pedidoDeAcao != null && !NONE.equals(pedidoDeAcao);
    }
}
