package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import java.util.Objects;

/** Entrada da revisão do semestral: conteúdo atual (seções 2 a 10) e o pedido do professor. */
public record SemesterRevisionInput(SemesterReportContent currentContent, String request)
        implements SemesterTaskInput {

    public SemesterRevisionInput {
        Objects.requireNonNull(currentContent);
        Objects.requireNonNull(request);
    }
}
