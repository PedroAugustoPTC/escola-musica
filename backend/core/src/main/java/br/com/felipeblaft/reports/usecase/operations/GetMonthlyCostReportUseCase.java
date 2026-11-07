package br.com.felipeblaft.reports.usecase.operations;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.domain.operations.CostAggregate;
import br.com.felipeblaft.reports.domain.operations.MonthlyCostReport;
import br.com.felipeblaft.reports.domain.operations.MonthlyCostReport.StudentCost;
import br.com.felipeblaft.reports.domain.operations.PriceTable;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.CostReportQueryPort;
import br.com.felipeblaft.reports.port.CostReportQueryPort.CostLine;
import br.com.felipeblaft.reports.port.TransactionPort;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tempo e custo do mês ({@code OPERATIONS_COST_READ}, FR-053). O mês vai de 00:00 do primeiro dia
 * às 00:00 do seguinte no fuso da escola. "Por áudio" são as chamadas de transcrição; "por aula" e
 * "por semestral" agrupam por relatório; "por aluno" pelo aluno do relatório. O custo fixo de
 * infraestrutura é o vigente no primeiro dia do mês e vem à parte das chamadas.
 */
public class GetMonthlyCostReportUseCase {

    private static final String UNKNOWN = "unknown";

    private final ActorAuthorizationService authorization;
    private final CostReportQueryPort costs;
    private final PriceTable prices;
    private final ClockPort clock;
    private final AuthorshipService authorship;
    private final TransactionPort transaction;

    public GetMonthlyCostReportUseCase(
            ActorAuthorizationService authorization,
            CostReportQueryPort costs,
            PriceTable prices,
            ClockPort clock,
            AuthorshipService authorship,
            TransactionPort transaction) {
        this.authorization = Objects.requireNonNull(authorization);
        this.costs = Objects.requireNonNull(costs);
        this.prices = Objects.requireNonNull(prices);
        this.clock = Objects.requireNonNull(clock);
        this.authorship = Objects.requireNonNull(authorship);
        this.transaction = Objects.requireNonNull(transaction);
    }

    public MonthlyCostReport run(Actor actor, String month, String correlationId) {
        authorization.require(actor, Operation.OPERATIONS_COST_READ);
        YearMonth period = parse(actor, month, correlationId);
        Instant from = period.atDay(1).atStartOfDay(clock.schoolTimezone()).toInstant();
        Instant to = period.plusMonths(1).atDay(1).atStartOfDay(clock.schoolTimezone()).toInstant();
        return transaction.run(
                () -> {
                    List<CostLine> lines = costs.lines(from, to);
                    Set<UUID> studentIds = new HashSet<>();
                    lines.stream()
                            .map(CostLine::studentId)
                            .filter(Objects::nonNull)
                            .forEach(studentIds::add);
                    MonthlyCostReport report = build(period, lines, costs.studentNames(studentIds));
                    authorship.recordRead(
                            actor,
                            Operation.OPERATIONS_COST_READ,
                            null,
                            null,
                            null,
                            AuthorshipResult.SUCCESS,
                            null,
                            correlationId);
                    return report;
                });
    }

    private YearMonth parse(Actor actor, String month, String correlationId) {
        try {
            if (month == null) {
                throw new IllegalArgumentException("mês ausente");
            }
            return YearMonth.parse(month);
        } catch (RuntimeException invalid) {
            authorship.recordRead(
                    actor,
                    Operation.OPERATIONS_COST_READ,
                    null,
                    null,
                    null,
                    AuthorshipResult.FAILURE,
                    ErrorCode.VALIDATION_FAILED.name(),
                    correlationId);
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("fields", List.of(Map.of("field", "month", "code", "INVALID_VALUE"))));
        }
    }

    private MonthlyCostReport build(
            YearMonth period, List<CostLine> lines, Map<UUID, String> names) {
        CostAggregate totals = CostAggregate.EMPTY;
        CostAggregate perAudio = CostAggregate.EMPTY;
        Map<UUID, CostAggregate> lessons = new HashMap<>();
        Map<UUID, CostAggregate> semesters = new HashMap<>();
        Map<UUID, CostAggregate> students = new HashMap<>();
        Map<String, CostAggregate> channels = new LinkedHashMap<>();
        Map<String, CostAggregate> providers = new LinkedHashMap<>();
        for (CostLine line : lines) {
            boolean failed = !line.success();
            totals = totals.plus(line.costUsd(), line.durationMs(), failed);
            if (line.task() == AiTask.TRANSCRIPTION) {
                perAudio = perAudio.plus(line.costUsd(), line.durationMs(), failed);
            }
            if (line.reportId() != null) {
                Map<UUID, CostAggregate> target =
                        line.reportType() == ReportType.SEMESTER ? semesters : lessons;
                target.merge(line.reportId(), plus(line), CostAggregate::combine);
            }
            if (line.studentId() != null) {
                students.merge(line.studentId(), plus(line), CostAggregate::combine);
            }
            channels.merge(line.channel().name(), plus(line), CostAggregate::combine);
            providers.merge(
                    line.provider() == null ? UNKNOWN : line.provider(),
                    plus(line),
                    CostAggregate::combine);
        }
        List<StudentCost> perStudent =
                students.entrySet().stream()
                        .map(e -> new StudentCost(e.getKey(), names.get(e.getKey()), e.getValue()))
                        .sorted(
                                Comparator.comparing(
                                                (StudentCost s) -> s.aggregate().totalCostUsd())
                                        .reversed())
                        .toList();
        BigDecimal infrastructure = prices.infrastructureMonthlyUsdOn(period.atDay(1));
        return new MonthlyCostReport(
                period,
                infrastructure,
                totals,
                perAudio,
                perReport(lessons),
                perReport(semesters),
                perStudent,
                channels,
                providers);
    }

    private static CostAggregate plus(CostLine line) {
        return CostAggregate.EMPTY.plus(line.costUsd(), line.durationMs(), !line.success());
    }

    /** Uma unidade por relatório: a contagem é a de relatórios, os totais somam as suas chamadas. */
    private static CostAggregate perReport(Map<UUID, CostAggregate> reports) {
        CostAggregate sum = CostAggregate.EMPTY;
        for (CostAggregate each : reports.values()) {
            sum = sum.combine(each);
        }
        return sum.withCount(reports.size());
    }
}
