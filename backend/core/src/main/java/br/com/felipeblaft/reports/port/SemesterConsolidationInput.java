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
        List<ConsolidatedLesson> lessons,
        int lessonCount,
        LocalDate periodStart,
        LocalDate periodEnd)
        implements SemesterTaskInput {

    public SemesterConsolidationInput {
        lessons = List.copyOf(lessons);
        Objects.requireNonNull(periodStart);
        Objects.requireNonNull(periodEnd);
    }

    public record ConsolidatedLesson(LocalDate lessonDate, LessonReportContent report) {
        public ConsolidatedLesson {
            Objects.requireNonNull(lessonDate);
            Objects.requireNonNull(report);
        }
    }
}
