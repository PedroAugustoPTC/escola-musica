package br.com.felipeblaft.relatorios.dominio.relatorio;

import java.util.List;
import java.util.Objects;

/**
 * As cinco seções do relatório de aula (FR-019). Campo sem informação fica vazio, nunca inventado
 * (FR-020): textos são normalizados e itens em branco descartados.
 */
public record ConteudoRelatorioAula(
        List<String> conteudosTrabalhados,
        String evolucao,
        List<String> dificuldades,
        List<String> atividadesPropostas,
        String observacoes)
        implements ConteudoVersao {

    public ConteudoRelatorioAula {
        conteudosTrabalhados = itens(conteudosTrabalhados);
        evolucao = texto(evolucao);
        dificuldades = itens(dificuldades);
        atividadesPropostas = itens(atividadesPropostas);
        observacoes = texto(observacoes);
    }

    public static ConteudoRelatorioAula vazio() {
        return new ConteudoRelatorioAula(null, null, null, null, null);
    }

    static List<String> itens(List<String> itens) {
        if (itens == null) {
            return List.of();
        }
        return itens.stream().filter(Objects::nonNull).map(String::strip).filter(i -> !i.isEmpty()).toList();
    }

    static String texto(String texto) {
        return texto == null ? "" : texto.strip();
    }

    public boolean estaVazio() {
        return conteudosTrabalhados.isEmpty()
                && evolucao.isEmpty()
                && dificuldades.isEmpty()
                && atividadesPropostas.isEmpty()
                && observacoes.isEmpty();
    }
}
