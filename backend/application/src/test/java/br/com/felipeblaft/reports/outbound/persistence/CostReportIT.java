package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiCall;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.operations.MonthlyCostReport;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.CostRecordPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.usecase.operations.GetMonthlyCostReportUseCase;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Custo do mês contra o Postgres real (SC-008): 10 aulas e 2 semestrais em dois canais e dois
 * provedores, com custos conhecidos. O total do painel difere menos de 1% da soma de {@code
 * estimated_cost_usd} mais a infraestrutura, e os agregados por unidade conferem.
 */
class CostReportIT extends IntegrationBase {

    private static final String PROVIDER_A = "provider-a/model";
    private static final String PROVIDER_B = "provider-b/model";

    @Autowired GetMonthlyCostReportUseCase useCase;
    @Autowired CostRecordPort costs;
    @Autowired JobQueuePort queue;
    @Autowired ScenarioIT scenario;

    private ProcessingJob job(Teacher teacher, Student student, Channel channel) {
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        teacher.id(),
                        student.id(),
                        null,
                        null,
                        channel,
                        Medium.AUDIO,
                        JobStep.UPLOADING,
                        clock.now());
        queue.enqueue(job);
        return job;
    }

    private void call(
            ProcessingJob job,
            Channel channel,
            ReportType type,
            UUID report,
            AiTask task,
            String provider,
            String cost,
            long ms) {
        costs.register(
                AiCall.create(
                        job.id(),
                        channel,
                        type,
                        report,
                        task,
                        provider,
                        null,
                        100,
                        50,
                        ms,
                        new BigDecimal(cost),
                        true,
                        null,
                        clock.now()));
    }

    @Test
    void monthlyTotalMatchesTheSumOfTheRecordedCalls() {
        Teacher clara = scenario.clara();
        Student matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        Student helena = scenario.student(clara, "Helena Prado", "Violino");
        ProcessingJob matteoJob = job(clara, matteo, Channel.WEB);
        ProcessingJob helenaJob = job(clara, helena, Channel.WEB);
        for (int i = 0; i < 10; i++) {
            Student student = i < 6 ? matteo : helena;
            Channel channel = i % 2 == 0 ? Channel.WEB : Channel.TELEGRAM;
            String provider = i % 2 == 0 ? PROVIDER_A : PROVIDER_B;
            UUID lesson =
                    scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 10 + i), "Scales")
                            .id();
            ProcessingJob job = student == matteo ? matteoJob : helenaJob;
            call(
                    job,
                    channel,
                    ReportType.LESSON,
                    lesson,
                    AiTask.TRANSCRIPTION,
                    provider,
                    "0.010000",
                    2000);
            call(
                    job,
                    channel,
                    ReportType.LESSON,
                    lesson,
                    AiTask.STRUCTURING,
                    provider,
                    "0.002000",
                    4000);
        }
        for (int i = 0; i < 2; i++) {
            call(
                    matteoJob,
                    Channel.TELEGRAM,
                    ReportType.SEMESTER,
                    UUID.randomUUID(),
                    AiTask.CONSOLIDATION,
                    PROVIDER_B,
                    "0.050000",
                    8000);
        }
        // fora do mês: não entra
        clock.advance(Duration.ofDays(40));
        call(
                helenaJob,
                Channel.WEB,
                ReportType.LESSON,
                null,
                AiTask.TRANSCRIPTION,
                PROVIDER_A,
                "9.000000",
                100);
        clock.define(clock.now().minus(Duration.ofDays(40)));

        MonthlyCostReport report =
                useCase.run(Actor.administrator(UUID.randomUUID(), false), "2026-09", "req-cost");

        BigDecimal sum =
                jdbc.queryForObject(
                        "select sum(estimated_cost_usd) from ai_call where created_at >= ? and"
                                + " created_at < ?",
                        BigDecimal.class,
                        java.sql.Timestamp.from(java.time.Instant.parse("2026-09-01T03:00:00Z")),
                        java.sql.Timestamp.from(java.time.Instant.parse("2026-10-01T03:00:00Z")));
        BigDecimal expected = sum.add(report.infrastructureUsd());
        BigDecimal difference = report.grandTotalUsd().subtract(expected).abs();
        assertThat(difference.divide(expected, 10, java.math.RoundingMode.HALF_EVEN))
                .isLessThan(new BigDecimal("0.01"));

        assertThat(report.totals().count()).isEqualTo(22);
        assertThat(report.totals().totalCostUsd()).isEqualByComparingTo("0.22");
        assertThat(report.perAudio().count()).isEqualTo(10);
        assertThat(report.perAudio().totalCostUsd()).isEqualByComparingTo("0.10");
        assertThat(report.perLesson().count()).isEqualTo(10);
        assertThat(report.perLesson().averageCostUsd()).isEqualByComparingTo("0.012");
        assertThat(report.perLesson().averageDurationMs()).isEqualTo(6000);
        assertThat(report.perSemesterReport().count()).isEqualTo(2);
        assertThat(report.perSemesterReport().totalCostUsd()).isEqualByComparingTo("0.10");
        assertThat(report.byChannel().get("WEB").totalCostUsd()).isEqualByComparingTo("0.06");
        assertThat(report.byChannel().get("TELEGRAM").totalCostUsd()).isEqualByComparingTo("0.16");
        assertThat(report.byProvider().get(PROVIDER_A).count()).isEqualTo(10);
        assertThat(report.byProvider().get(PROVIDER_B).totalCostUsd()).isEqualByComparingTo("0.16");
        assertThat(report.perStudent()).hasSize(2);
        assertThat(report.perStudent().getFirst().studentLabel()).isEqualTo("Matteo Ferreira");
        assertThat(report.perStudent().getFirst().aggregate().totalCostUsd())
                .isEqualByComparingTo("0.072");
        assertThat(report.perStudent().getLast().aggregate().totalCostUsd())
                .isEqualByComparingTo("0.048");
    }

    @Test
    void monthWithoutCallsIsEmpty() {
        MonthlyCostReport report =
                useCase.run(Actor.administrator(UUID.randomUUID(), false), "2025-01", "req-cost");

        assertThat(report.totals().count()).isZero();
        assertThat(report.perStudent()).isEmpty();
    }
}
