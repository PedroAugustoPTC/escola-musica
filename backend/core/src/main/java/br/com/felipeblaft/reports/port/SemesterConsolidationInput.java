package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.report.LessonReportContent;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Entrada da consolidação semestral (contracts/portas-provedores.md §5): os relatórios de aula
 * aprovados do período com a data de cada aula. Nunca leva nome, idade, CPF ou nascimento do aluno
 * (FR-044, Princípio IV).
 */
public record SemesterConsolidationInput(
        List<ConsolidatedLesson> aulas,
        int quantidadeAulas,
        LocalDate periodoInicio,
        LocalDate periodoFim)
        implements SemesterTaskInput {

    public SemesterConsolidationInput {
        aulas = List.copyOf(aulas);
        Objects.requireNonNull(periodoInicio);
        Objects.requireNonNull(periodoFim);
    }

    public record ConsolidatedLesson(LocalDate dataAula, LessonReportContent relatorio) {
        public ConsolidatedLesson {
            Objects.requireNonNull(dataAula);
            Objects.requireNonNull(relatorio);
        }
    }
}
