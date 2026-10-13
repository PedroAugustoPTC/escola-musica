package br.com.felipeblaft.reports.domain.report;

import java.util.List;
import java.util.Objects;

/**
 * As cinco seções do relatório de aula (FR-019). Campo sem informação fica vazio, nunca inventado
 * (FR-020): textos são normalizados e itens em branco descartados.
 */
public record LessonReportContent(
        List<String> conteudosTrabalhados,
        String evolucao,
        List<String> dificuldades,
        List<String> atividadesPropostas,
        String observacoes)
        implements VersionContent {

    public LessonReportContent {
        conteudosTrabalhados = items(conteudosTrabalhados);
        evolucao = text(evolucao);
        dificuldades = items(dificuldades);
        atividadesPropostas = items(atividadesPropostas);
        observacoes = text(observacoes);
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
        return conteudosTrabalhados.isEmpty()
                && evolucao.isEmpty()
                && dificuldades.isEmpty()
                && atividadesPropostas.isEmpty()
                && observacoes.isEmpty();
    }
}
