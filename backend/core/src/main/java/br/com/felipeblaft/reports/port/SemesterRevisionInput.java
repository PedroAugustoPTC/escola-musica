package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.semester.SemesterReportContent;
import java.util.Objects;

/** Entrada da revisão do semestral: conteúdo atual (seções 2 a 10) e o pedido do professor. */
public record SemesterRevisionInput(SemesterReportContent conteudoAtual, String pedido)
        implements SemesterTaskInput {

    public SemesterRevisionInput {
        Objects.requireNonNull(conteudoAtual);
        Objects.requireNonNull(pedido);
    }
}
