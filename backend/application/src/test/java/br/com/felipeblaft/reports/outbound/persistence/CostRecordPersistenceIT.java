package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.job.AiCall;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.CostRecordPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.ScenarioIT;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FR-056: o custo e o tempo de cada chamada de IA ficam em {@code chamada_ia}; a transcrição e a
 * consolidação, registradas antes de o relatório existir, são ligadas a ele depois; as chamadas de
 * um trabalho sem relatório continuam sem ligação. Os fluxos completos nos dois canais estão nos
 * roteiros de paridade.
 */
class CostRecordPersistenceIT extends IntegrationBase {

    @Autowired CostRecordPort costs;
    @Autowired JobQueuePort queue;
    @Autowired ScenarioIT scenario;
    @Autowired TransactionTemplate transaction;

    private ProcessingJob job(Teacher teacher, Student student) {
        ProcessingJob job =
                ProcessingJob.create(
                        JobType.LESSON_REGISTRATION,
                        teacher.id(),
                        student.id(),
                        null,
                        null,
                        Channel.WEB,
                        Medium.AUDIO,
                        JobStep.UPLOADING,
                        clock.now());
        queue.enqueue(job);
        return job;
    }

    private void call(ProcessingJob job, UUID report, AiTask task, String cost) {
        costs.register(
                AiCall.create(
                        job.id(),
                        report == null ? null : ReportType.LESSON,
                        report,
                        task,
                        "provider/model",
                        null,
                        100,
                        50,
                        1200,
                        new BigDecimal(cost),
                        true,
                        null,
                        clock.now()));
    }

    @Test
    void linkOnlyBindsJobCallsThatDoNotHaveReportYet() {
        Teacher clara = scenario.clara();
        Student matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        Student helena = scenario.student(clara, "Helena Prado", "Violino");
        ProcessingJob a = job(clara, matteo);
        ProcessingJob other = job(clara, helena);
        UUID report = UUID.randomUUID();
        UUID alreadyOn = UUID.randomUUID();

        call(a, null, AiTask.TRANSCRIPTION, "0.01");
        call(a, alreadyOn, AiTask.STRUCTURING, "0.002");
        call(other, null, AiTask.TRANSCRIPTION, "0.5");

        transaction.executeWithoutResult(s -> costs.linkReport(a.id(), ReportType.LESSON, report));

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from ai_call where report_id = ?",
                                Integer.class,
                                report))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select task from ai_call where report_id = ?",
                                String.class,
                                report))
                .isEqualTo("TRANSCRIPTION");
        // a já ligada a outro relatório não muda, e o outro trabalho segue sem relatório
        assertThat(
                        jdbc.queryForObject(
                                "select report_id from ai_call where task = 'STRUCTURING'",
                                UUID.class))
                .isEqualTo(alreadyOn);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from ai_call where job_id = ?"
                                        + " and report_id is null",
                                Integer.class,
                                other.id()))
                .isEqualTo(1);
        // custo por relatório consultável e sem prompt nem resposta guardados
        assertThat(
                        jdbc.queryForObject(
                                "select sum(estimated_cost_usd) from ai_call where report_id = ?",
                                BigDecimal.class,
                                report))
                .isEqualByComparingTo("0.01");
        assertThat(
                        jdbc.queryForObject(
                                "select duration_ms from ai_call where report_id = ?",
                                Long.class,
                                report))
                .isEqualTo(1200L);
    }
}
