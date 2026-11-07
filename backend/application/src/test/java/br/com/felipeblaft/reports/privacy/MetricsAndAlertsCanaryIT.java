package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.Medium;
import br.com.felipeblaft.reports.domain.job.AiCall;
import br.com.felipeblaft.reports.domain.job.AiTask;
import br.com.felipeblaft.reports.domain.job.JobStep;
import br.com.felipeblaft.reports.domain.job.JobType;
import br.com.felipeblaft.reports.domain.job.ProcessingJob;
import br.com.felipeblaft.reports.domain.operations.AlertType;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.CostRecordPort;
import br.com.felipeblaft.reports.port.JobQueuePort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.service.OperationalAlertService;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.LeakAudit;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.support.PersonalDataSweep;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import br.com.felipeblaft.reports.usecase.operations.EvaluateOperationalAlertsUseCase;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Varredura da US5 (SC-001, FR-050, FR-055): com o aluno canário, aulas e semestral nos dois
 * canais, falha de provedor e de armazenamento, nenhum valor canário aparece em logs, {@code
 * ai_call}, {@code operational_alert}, trilha nem na resposta do painel de alertas. A tela de custo
 * do administrador é a única que mostra o nome do aluno (permitido e marcado como tal): os demais
 * valores (CPF, responsável, contato) continuam proibidos nela.
 */
class MetricsAndAlertsCanaryIT extends SchoolHistoryBase {

    @Autowired StudentRepository students;
    @Autowired CostRecordPort costs;
    @Autowired JobQueuePort queue;
    @Autowired OperationalAlertService alerts;
    @Autowired EvaluateOperationalAlertsUseCase evaluation;

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

    private void call(ProcessingJob job, String provider, boolean success) {
        clock.advance(Duration.ofSeconds(1));
        costs.register(
                AiCall.create(
                        job.id(),
                        job.requestChannel(),
                        null,
                        null,
                        AiTask.STRUCTURING,
                        provider,
                        null,
                        100,
                        50,
                        900,
                        new BigDecimal("0.003"),
                        success,
                        success ? null : "PROVIDER_UNAVAILABLE",
                        clock.now()));
    }

    @Test
    void costsAndAlertsLeaveNoCanaryBehind() throws Exception {
        Map<String, String> values = new LinkedHashMap<>(CanaryStudent.sweepValuesWithCpf());
        PersonalDataSweep sweep = PersonalDataSweep.of(jdbc, values);
        LeakAudit audit = sweep.newAudit();
        Map<String, String> withoutName = new LinkedHashMap<>(values);
        withoutName.remove("nome do aluno canário");
        LeakAudit costScreen = LeakAudit.searching(withoutName);

        Teacher clara = scenario.clara();
        Cookie administrator = signInNewAdministrator();
        try (LogCapture logs = LogCapture.start()) {
            UUID studentId =
                    registerStudentAsAdministrator(
                            administrator,
                            minorStudent(
                                    CanaryStudent.NAME,
                                    CanaryStudent.BIRTH_DATE_ISO,
                                    CanaryCpf.MASKED,
                                    CanaryStudent.GUARDIAN_NAME,
                                    CanaryStudent.GUARDIAN_PHONE_MASKED,
                                    CanaryStudent.GUARDIAN_EMAIL),
                            clara.id());
            Student student = students.searchById(studentId).orElseThrow();
            for (int day = 10; day < 13; day++) {
                scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, day), "Scales");
            }
            Cookie teacher = signInTeacher(ScenarioIT.CODE_CLARA);
            approvedSemester(teacher, studentId, "2026-1");

            // aulas pelo outro canal, com o provedor falhando e depois o armazenamento
            ProcessingJob telegram = job(clara, student, Channel.TELEGRAM);
            for (int i = 0; i < 3; i++) {
                call(telegram, "provider-x/model", false);
            }
            call(telegram, "provider-x/model", true);
            alerts.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");
            evaluation.run();

            MockHttpServletResponse costs =
                    call(HttpMethod.GET, ADMIN + "/operations/costs?month=2026-09", administrator);
            MockHttpServletResponse alertList =
                    call(HttpMethod.GET, ADMIN + "/operations/alerts", administrator);
            MockHttpServletResponse summary =
                    call(HttpMethod.GET, ADMIN + "/operations/alerts/summary", administrator);

            assertThat(costs.getStatus()).isEqualTo(200);
            assertThat(alertList.getStatus()).isEqualTo(200);
            assertThat(summary.getStatus()).isEqualTo(200);
            assertThat(costs.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                    .contains(CanaryStudent.NAME);
            assertThat(alertList.getContentAsString()).contains("AI_PROVIDER_FAILING");

            costScreen.text(
                    "tela de custo",
                    Place.BODY,
                    costs.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            audit.text("painel de alertas", Place.BODY, alertList.getContentAsString());
            audit.text("resumo de alertas", Place.BODY, summary.getContentAsString());
            sweep.sweep(audit, "custo e alertas", logs);
        }

        assertThat(audit.sweeps()).isPositive();
        audit.assertClean();
        costScreen.assertClean();
    }
}
