package br.com.felipeblaft.reports.domain.operations;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tempo e custo estimado do mês no fuso da escola (FR-053), em USD. {@code totals} soma só as
 * chamadas de IA; {@code infrastructureUsd} é o custo fixo vigente no mês e vem à parte.
 */
public record MonthlyCostReport(
        YearMonth month,
        BigDecimal infrastructureUsd,
        CostAggregate totals,
        CostAggregate perAudio,
        CostAggregate perLesson,
        CostAggregate perSemesterReport,
        List<StudentCost> perStudent,
        Map<String, CostAggregate> byChannel,
        Map<String, CostAggregate> byProvider) {

    public MonthlyCostReport {
        perStudent = List.copyOf(perStudent);
        byChannel = Map.copyOf(byChannel);
        byProvider = Map.copyOf(byProvider);
    }

    /**
     * Custo de um aluno no mês. O nome (nulo se o aluno já foi eliminado) é resolvido só para a
     * exibição do administrador e nunca é gravado nem logado.
     */
    public record StudentCost(UUID studentId, String studentLabel, CostAggregate aggregate) {

        @Override
        public String toString() {
            return "StudentCost[...]";
        }
    }

    /** Total do mês: chamadas de IA mais o custo fixo de infraestrutura. */
    public BigDecimal grandTotalUsd() {
        return totals.totalCostUsd().add(infrastructureUsd);
    }
}
