package br.com.felipeblaft.reports.usecase.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Operation;
import br.com.felipeblaft.reports.domain.access.PermissionMatrix;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.domain.operations.MonthlyCostReport;
import br.com.felipeblaft.reports.domain.operations.PriceEntry;
import br.com.felipeblaft.reports.domain.operations.PriceTable;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.doubles.FakeCostReportQuery;
import br.com.felipeblaft.reports.doubles.FixedClock;
import br.com.felipeblaft.reports.doubles.InMemoryAuditTrail;
import br.com.felipeblaft.reports.doubles.ProcessingDoubles.ImmediateTransaction;
import br.com.felipeblaft.reports.port.CostReportQueryPort.CostLine;
import br.com.felipeblaft.reports.service.ActorAuthorizationService;
import br.com.felipeblaft.reports.service.AuthorshipService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Custo e tempo do mês (FR-053): fuso da escola, agregados e infraestrutura. */
class GetMonthlyCostReportUseCaseTest {

    private static final UUID LESSON_A = UUID.randomUUID();
    private static final UUID LESSON_B = UUID.randomUUID();
    private static final UUID SEMESTER_A = UUID.randomUUID();
    private static final UUID STUDENT_1 = UUID.randomUUID();
    private static final UUID STUDENT_2 = UUID.randomUUID();

    private final FixedClock clock = new FixedClock();
    private final InMemoryAuditTrail trail = new InMemoryAuditTrail();
    private final FakeCostReportQuery query = new FakeCostReportQuery();
    private final PriceTable prices =
            PriceTable.from(
                    List.of(),
                    List.<PriceEntry>of(),
                    new BigDecimal("12.50"),
                    LocalDate.parse("2026-02-01"));
    private final GetMonthlyCostReportUseCase useCase =
            new GetMonthlyCostReportUseCase(
                    new ActorAuthorizationService(PermissionMatrix.standard()),
                    query,
                    prices,
                    clock,
                    new AuthorshipService(trail, clock),
                    new ImmediateTransaction());

    private static CostLine line(
            ReportType type,
            UUID report,
            UUID student,
            AiTask task,
            Channel channel,
            String provider,
            String cost,
            long ms,
            boolean success) {
        return new CostLine(
                type, report, student, task, channel, provider, new BigDecimal(cost), ms, success);
    }

    @Test
    @DisplayName("o mês vai de 00:00 do primeiro dia às 00:00 do seguinte no fuso de São Paulo")
    void monthBoundariesFollowTheSchoolTimezone() {
        useCase.run(ActorFixtures.administratorWeb(), "2026-09", "req-1");

        assertThat(query.lastFrom).isEqualTo(Instant.parse("2026-09-01T03:00:00Z"));
        assertThat(query.lastTo).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
    }

    @Test
    @DisplayName("mês sem chamada devolve tudo zerado, com médias zero e a infraestrutura vigente")
    void emptyMonthHasZeroAverages() {
        MonthlyCostReport report =
                useCase.run(ActorFixtures.administratorWeb(), "2026-09", "req-1");

        assertThat(report.totals().count()).isZero();
        assertThat(report.totals().averageCostUsd()).isEqualByComparingTo("0");
        assertThat(report.totals().averageDurationMs()).isZero();
        assertThat(report.perStudent()).isEmpty();
        assertThat(report.infrastructureUsd()).isEqualByComparingTo("12.50");
        assertThat(report.grandTotalUsd()).isEqualByComparingTo("12.50");
    }

    @Test
    @DisplayName("custo fixo só vale a partir da vigência")
    void infrastructureFollowsTheEffectiveDate() {
        MonthlyCostReport report =
                useCase.run(ActorFixtures.administratorWeb(), "2026-01", "req-1");

        assertThat(report.infrastructureUsd()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("agrega por áudio, aula, semestral, aluno, canal e provedor e conta as falhas")
    void aggregatesEveryDimension() {
        query.lines.addAll(
                List.of(
                        line(ReportType.LESSON, LESSON_A, STUDENT_1, AiTask.TRANSCRIPTION,
                                Channel.WEB, "provider-a", "0.10", 2000, true),
                        line(ReportType.LESSON, LESSON_A, STUDENT_1, AiTask.STRUCTURING,
                                Channel.WEB, "provider-b", "0.20", 4000, true),
                        line(ReportType.LESSON, LESSON_B, STUDENT_2, AiTask.TRANSCRIPTION,
                                Channel.TELEGRAM, "provider-a", "0.30", 6000, true),
                        line(ReportType.LESSON, LESSON_B, STUDENT_2, AiTask.STRUCTURING,
                                Channel.TELEGRAM, "provider-b", "0.00", 1000, false),
                        line(ReportType.SEMESTER, SEMESTER_A, STUDENT_1, AiTask.CONSOLIDATION,
                                Channel.WEB, "provider-b", "0.40", 8000, true)));

        MonthlyCostReport report =
                useCase.run(ActorFixtures.administratorWeb(), "2026-09", "req-1");

        assertThat(report.totals().count()).isEqualTo(5);
        assertThat(report.totals().totalCostUsd()).isEqualByComparingTo("1.00");
        assertThat(report.totals().failedCalls()).isEqualTo(1);
        assertThat(report.perAudio().count()).isEqualTo(2);
        assertThat(report.perAudio().totalCostUsd()).isEqualByComparingTo("0.40");
        assertThat(report.perAudio().averageCostUsd()).isEqualByComparingTo("0.20");
        assertThat(report.perAudio().averageDurationMs()).isEqualTo(4000);
        assertThat(report.perLesson().count()).isEqualTo(2);
        assertThat(report.perLesson().totalCostUsd()).isEqualByComparingTo("0.60");
        assertThat(report.perLesson().averageCostUsd()).isEqualByComparingTo("0.30");
        assertThat(report.perSemesterReport().count()).isEqualTo(1);
        assertThat(report.perSemesterReport().totalCostUsd()).isEqualByComparingTo("0.40");
        assertThat(report.perStudent()).hasSize(2);
        assertThat(report.perStudent().getFirst().studentId()).isEqualTo(STUDENT_1);
        assertThat(report.perStudent().getFirst().aggregate().totalCostUsd())
                .isEqualByComparingTo("0.70");
        assertThat(report.byChannel().get("WEB").totalCostUsd()).isEqualByComparingTo("0.70");
        assertThat(report.byChannel().get("TELEGRAM").count()).isEqualTo(2);
        assertThat(report.byProvider().get("provider-a").count()).isEqualTo(2);
        assertThat(report.byProvider().get("provider-b").totalCostUsd())
                .isEqualByComparingTo("0.60");
        assertThat(report.grandTotalUsd()).isEqualByComparingTo("13.50");
    }

    @Test
    @DisplayName("chamada sem relatório entra no total, no canal e no provedor, mas não em aula nem aluno")
    void callsWithoutReportOnlyCountInTotals() {
        query.lines.add(
                line(ReportType.LESSON, null, null, AiTask.TRANSCRIPTION, Channel.WEB, null,
                        "0.05", 500, false));

        MonthlyCostReport report =
                useCase.run(ActorFixtures.administratorWeb(), "2026-09", "req-1");

        assertThat(report.totals().count()).isEqualTo(1);
        assertThat(report.perLesson().count()).isZero();
        assertThat(report.perStudent()).isEmpty();
        assertThat(report.byProvider()).containsKey("unknown");
    }

    @Test
    @DisplayName("mês inválido dá VALIDATION_FAILED e registra a falha; a leitura registra o evento")
    void invalidMonthIsRefusedAndReadsAreRecorded() {
        assertThatThrownBy(
                        () -> useCase.run(ActorFixtures.administratorWeb(), "2026-13", "req-1"))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(trail.records().getLast().operation())
                .isEqualTo(Operation.OPERATIONS_COST_READ.name());

        useCase.run(ActorFixtures.administratorWeb(), "2026-09", "req-2");
        assertThat(trail.records()).hasSize(2);
    }

    @Test
    @DisplayName("professor não lê custos")
    void teacherCannotReadCosts() {
        assertThatThrownBy(() -> useCase.run(ActorFixtures.teacherWeb(), "2026-09", "req-1"))
                .isInstanceOf(DomainException.class);
    }
}
