package br.com.felipeblaft.reports.inbound.web.admin;

import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.operations.AlertThresholds;
import br.com.felipeblaft.reports.domain.operations.CostAggregate;
import br.com.felipeblaft.reports.domain.operations.MonthlyCostReport;
import br.com.felipeblaft.reports.domain.operations.OperationalAlert;
import br.com.felipeblaft.reports.domain.privacy.PolicyValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Respostas de custo e alertas de operação (schemas do contrato da feature 007). */
final class AdminOperationsDtos {

    private AdminOperationsDtos() {}

    /** Agregado de custo e tempo; valores em USD. */
    record CostAggregateBody(
            int count,
            BigDecimal totalCostUsd,
            BigDecimal averageCostUsd,
            long totalDurationMs,
            long averageDurationMs,
            int failedCalls) {}

    /**
     * Custo de um aluno: os campos do agregado vêm direto no item, como no contrato. O nome só
     * serve à exibição do administrador.
     */
    record StudentCostBody(
            UUID studentId,
            String studentLabel,
            int count,
            BigDecimal totalCostUsd,
            BigDecimal averageCostUsd,
            long totalDurationMs,
            long averageDurationMs,
            int failedCalls) {

        @Override
        public String toString() {
            return "StudentCostBody[...]";
        }
    }

    record MonthlyCostBody(
            String month,
            String currency,
            BigDecimal infrastructureUsd,
            BigDecimal grandTotalUsd,
            CostAggregateBody totals,
            CostAggregateBody perAudio,
            CostAggregateBody perLesson,
            CostAggregateBody perSemesterReport,
            List<StudentCostBody> perStudent,
            Map<String, CostAggregateBody> byChannel,
            Map<String, CostAggregateBody> byProvider) {

        @Override
        public String toString() {
            return "MonthlyCostBody[...]";
        }
    }

    record AlertBody(
            UUID id,
            String type,
            String component,
            String detailCode,
            String status,
            Instant startedAt,
            Instant lastSeenAt,
            Instant normalizedAt,
            int occurrences) {

        @Override
        public String toString() {
            return "AlertBody[...]";
        }
    }

    record PolicyValueBody(int value, String source) {

        @Override
        public String toString() {
            return "PolicyValueBody[value=" + value + "]";
        }
    }

    private static StudentCostBody studentCost(MonthlyCostReport.StudentCost s) {
        CostAggregateBody a = aggregate(s.aggregate());
        return new StudentCostBody(
                s.studentId(),
                s.studentLabel(),
                a.count(),
                a.totalCostUsd(),
                a.averageCostUsd(),
                a.totalDurationMs(),
                a.averageDurationMs(),
                a.failedCalls());
    }

    record AlertPage(
            List<AlertBody> items,
            int page,
            int size,
            long total,
            Map<String, PolicyValueBody> effectivePolicy) {

        @Override
        public String toString() {
            return "AlertPage[page=" + page + ", total=" + total + "]";
        }
    }

    record AlertSummary(int activeCount) {}

    static CostAggregateBody aggregate(CostAggregate a) {
        return new CostAggregateBody(
                a.count(),
                scaled(a.totalCostUsd()),
                scaled(a.averageCostUsd()),
                a.totalDurationMs(),
                a.averageDurationMs(),
                a.failedCalls());
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(6, java.math.RoundingMode.HALF_EVEN);
    }

    static MonthlyCostBody cost(MonthlyCostReport report) {
        Map<String, CostAggregateBody> channels = new LinkedHashMap<>();
        report.byChannel().forEach((k, v) -> channels.put(k, aggregate(v)));
        Map<String, CostAggregateBody> providers = new LinkedHashMap<>();
        report.byProvider().forEach((k, v) -> providers.put(k, aggregate(v)));
        return new MonthlyCostBody(
                report.month().toString(),
                "USD",
                scaled(report.infrastructureUsd()),
                scaled(report.grandTotalUsd()),
                aggregate(report.totals()),
                aggregate(report.perAudio()),
                aggregate(report.perLesson()),
                aggregate(report.perSemesterReport()),
                report.perStudent().stream().map(s -> studentCost(s)).toList(),
                channels,
                providers);
    }

    static AlertBody alert(OperationalAlert alert) {
        return new AlertBody(
                alert.id(),
                alert.type().name(),
                alert.component(),
                alert.detailCode(),
                alert.status().name(),
                alert.startedAt(),
                alert.lastSeenAt(),
                alert.normalizedAt(),
                alert.occurrences());
    }

    static AlertPage alerts(PageResult<OperationalAlert> page, AlertThresholds thresholds) {
        return new AlertPage(
                page.items().stream().map(AdminOperationsDtos::alert).toList(),
                page.page(),
                page.size(),
                page.total(),
                policy(thresholds));
    }

    /** Valores efetivos dos limites e a origem de cada um (FR-001). */
    static Map<String, PolicyValueBody> policy(AlertThresholds t) {
        Map<String, PolicyValueBody> policy = new LinkedHashMap<>();
        put(policy, "providerConsecutiveFailures", t.providerConsecutiveFailures());
        put(policy, "providerFailureRatePercent", t.providerFailureRatePercent());
        put(policy, "providerWindowMinutes", t.providerWindowMinutes());
        put(policy, "queueMaxAgeMinutes", t.queueMaxAgeMinutes());
        put(policy, "queueMaxItems", t.queueMaxItems());
        put(policy, "storageQuietMinutes", t.storageQuietMinutes());
        put(policy, "evaluationSeconds", t.evaluationSeconds());
        return policy;
    }

    private static void put(Map<String, PolicyValueBody> map, String key, PolicyValue value) {
        map.put(key, new PolicyValueBody(value.value(), value.origin().name()));
    }
}
