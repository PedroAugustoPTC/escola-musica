package br.com.felipeblaft.relatorios.porta;

import br.com.felipeblaft.relatorios.dominio.relatorio.ConteudoRelatorioAula;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Entrada da consolidação semestral (contracts/portas-provedores.md §5): os relatórios de aula
 * aprovados do período com a data de cada aula. Nunca leva nome, idade, CPF ou nascimento do aluno
 * (FR-044, Princípio IV).
 */
public record EntradaConsolidacaoSemestral(
        List<AulaConsolidada> aulas,
        int quantidadeAulas,
        LocalDate periodoInicio,
        LocalDate periodoFim)
        implements EntradaTarefaSemestral {

    public EntradaConsolidacaoSemestral {
        aulas = List.copyOf(aulas);
        Objects.requireNonNull(periodoInicio);
        Objects.requireNonNull(periodoFim);
    }

    public record AulaConsolidada(LocalDate dataAula, ConteudoRelatorioAula relatorio) {
        public AulaConsolidada {
            Objects.requireNonNull(dataAula);
            Objects.requireNonNull(relatorio);
        }
    }
}
