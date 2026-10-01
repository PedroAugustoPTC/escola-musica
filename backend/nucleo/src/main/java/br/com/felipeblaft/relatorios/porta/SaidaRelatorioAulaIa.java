package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import java.util.List;

/**
 * Saída das tarefas de aula (contracts/portas-provedores.md §6.1). Campos ausentes viram vazios;
 * {@code semAlteracaoDeConteudo} e {@code pedidoDeAcao} só valem na revisão.
 */
public record SaidaRelatorioAulaIa(
        List<String> conteudosTrabalhados,
        String evolucao,
        List<String> dificuldades,
        List<String> atividadesPropostas,
        String observacoes,
        List<String> perguntasPendentes,
        Boolean semAlteracaoDeConteudo,
        String pedidoDeAcao) {

    public static final String NENHUM = "NENHUM";

    public SaidaRelatorioAulaIa {
        perguntasPendentes = perguntasPendentes == null ? List.of() : perguntasPendentes;
    }

    public ConteudoRelatorioAula conteudo() {
        return new ConteudoRelatorioAula(
                conteudosTrabalhados, evolucao, dificuldades, atividadesPropostas, observacoes);
    }

    public boolean semAlteracao() {
        return Boolean.TRUE.equals(semAlteracaoDeConteudo);
    }

    /** O pedido era para aprovar ou cancelar: nunca é executado (FR-027a). */
    public boolean pedidoDeAcaoIndicado() {
        return pedidoDeAcao != null && !NENHUM.equals(pedidoDeAcao);
    }
}
