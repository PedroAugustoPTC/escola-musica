package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Actor;
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
import br.com.felipeblaft.reports.port.ErasureRequestRepository;
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
import br.com.felipeblaft.reports.usecase.privacy.ConfirmErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.PreviewErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RegisterErasureRequestUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RunRetentionUseCase;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Varredura completa da feature 007 (SC-001): o aluno canário percorre cadastro, aulas, semestral,
 * interações, custo e alertas, leitura da trilha, retenção com o relógio adiantado e eliminação.
 * Depois de cada etapa, logs, {@code ai_call}, {@code operational_alert}, trilha, itens de retenção e
 * pedidos de eliminação são varridos atrás de CPF, nome, nascimento, responsável, transcrição e texto
 * de relatório. Os achados se acumulam e a falha lista todos de uma vez.
 */
class PrivacyCanaryIT extends SchoolHistoryBase {

    @Autowired StudentRepository students;
    @Autowired CostRecordPort costs;
    @Autowired JobQueuePort queue;
    @Autowired OperationalAlertService alerts;
    @Autowired EvaluateOperationalAlertsUseCase evaluation;
    @Autowired RunRetentionUseCase retention;
    @Autowired RegisterErasureRequestUseCase register;
    @Autowired PreviewErasureUseCase preview;
    @Autowired ConfirmErasureUseCase confirm;
    @Autowired ErasureRequestRepository erasures;

    @Test
    void everyFlowLeavesNoCanaryInLogsMetricsAlertsOrTrail() throws Exception {
        PersonalDataSweep sweep = PersonalDataSweep.of(jdbc);
        LeakAudit audit = sweep.newAudit();
        Teacher clara = scenario.clara();
        var fixture = insertAdministrator();
        Cookie administrator = signInAdministrator(fixture);
        Actor admin = Actor.administrator(fixture.administrator().id(), false);

        try (LogCapture logs = LogCapture.start()) {
            // 1. cadastro pela Web, com todos os dados canário
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
            sweep.sweep(audit, "registration", logs);

            // 2. aulas e semestral, com transcrição e texto de relatório canário
            UUID report =
                    scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 10), "Scales")
                            .id();
            scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 17), "Rhythm");
            jdbc.update(
                    "update lesson_report set transcription = ? where id = ?",
                    CanaryStudent.TRANSCRIPTION_SNIPPET,
                    report);
            jdbc.update(
                    "insert into report_interaction (id, report_type, report_id, type, channel,"
                            + " medium, text, base_version, result, created_at)"
                            + " values (?, 'LESSON', ?, 'QUESTION_ANSWER', 'WEB', 'TEXT', ?, 1,"
                            + " 'APPLIED', ?)",
                    UUID.randomUUID(),
                    report,
                    CanaryStudent.REPORT_TEXT_SNIPPET,
                    Timestamp.from(clock.now()));
            Cookie teacher = signInTeacher(ScenarioIT.CODE_CLARA);
            approvedSemester(teacher, studentId, "2026-1");
            sweep.sweep(audit, "lessons and semester", logs);

            // 3. custo e alertas forçados nos dois canais
            ProcessingJob web = job(clara, student, Channel.WEB);
            ProcessingJob telegram = job(scenario.bruno(), student, Channel.TELEGRAM);
            call(web, true);
            for (int i = 0; i < 3; i++) {
                call(telegram, false);
            }
            call(telegram, true);
            alerts.report(AlertType.STORAGE_FAILED, "pdf-storage", "WRITE_FAILED");
            evaluation.run();
            sweepResponse(
                    audit, "cost", get("/operations/costs?month=2026-09", administrator), false);
            sweepResponse(audit, "alerts", get("/operations/alerts", administrator), true);
            sweepResponse(audit, "trail", get(trailPath(), administrator), false);
            sweep.sweep(audit, "cost, alerts and trail", logs);

            // 4. retenção com o relógio adiantado
            clock.advance(Duration.ofDays(31));
            assertThat(retention.run(Actor.retention(), UUID.randomUUID().toString()).failures())
                    .isZero();
            sweep.sweep(audit, "retention", logs);

            // 5. eliminação a pedido
            eliminate(admin, studentId);
            assertThat(erasures.isErased(studentId)).isTrue();
            // a sessão venceu com o relógio adiantado: entra de novo
            Cookie renewed = signInAdministrator(fixture, "198.51.100.11");
            sweepResponse(audit, "trail after erasure", get(trailPath(), renewed), true);
            sweep.sweep(audit, "erasure", logs);
        }

        assertThat(audit.sweeps()).isPositive();
        audit.assertClean();
    }

    /** Janela da trilha em torno do relógio de teste (a rota exige período). */
    private String trailPath() {
        LocalDate today = LocalDate.ofInstant(clock.now(), ZoneOffset.UTC);
        return "/audit-trail?from=" + today.minusDays(3) + "&to=" + today.plusDays(3);
    }

    private MockHttpServletResponse get(String path, Cookie administrator) throws Exception {
        MockHttpServletResponse response = call(HttpMethod.GET, ADMIN + path, administrator);
        assertThat(response.getStatus()).isEqualTo(200);
        return response;
    }

    private void sweepResponse(
            LeakAudit audit, String step, MockHttpServletResponse response, boolean strict)
            throws Exception {
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        if (strict) {
            audit.text(step, Place.BODY, body);
            return;
        }
        // as telas de custo e de trilha mostram o nome atual do aluno, resolvido na exibição (FR-054); depois da eliminação ele não aparece
        audit.text(step, Place.BODY, body.replace(CanaryStudent.NAME, ""));
    }

    private void eliminate(Actor admin, UUID studentId) {
        UUID requestId =
                register.run(
                                admin,
                                new RegisterErasureRequestUseCase.Command(
                                        studentId,
                                        "MOTHER",
                                        "Requester",
                                        LocalDate.ofInstant(clock.now(), ZoneOffset.UTC),
                                        "EMAIL",
                                        "PROT-CANARY",
                                        UUID.randomUUID().toString()))
                        .id();
        PreviewErasureUseCase.Preview shown =
                preview.run(admin, requestId, UUID.randomUUID().toString());
        confirm.run(
                admin,
                new ConfirmErasureUseCase.Command(
                        requestId,
                        shown.confirmationToken(),
                        "ELIMINAR",
                        UUID.randomUUID().toString()));
    }

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

    private void call(ProcessingJob job, boolean success) {
        clock.advance(Duration.ofSeconds(1));
        costs.register(
                AiCall.create(
                        job.id(),
                        job.requestChannel(),
                        null,
                        null,
                        AiTask.STRUCTURING,
                        "provider-x/model",
                        null,
                        100,
                        50,
                        900,
                        new BigDecimal("0.003"),
                        success,
                        success ? null : "PROVIDER_UNAVAILABLE",
                        clock.now()));
    }
}
