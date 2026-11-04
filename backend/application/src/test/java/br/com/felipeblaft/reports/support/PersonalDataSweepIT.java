package br.com.felipeblaft.reports.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Autoteste da varredura de dado pessoal: o valor plantado no log, em {@code ai_call} e em coluna
 * {@code jsonb} é achado; banco vazio e log limpo passam; a mensagem de falha nunca traz o valor.
 * As colunas de {@code operational_alert}, {@code authorship_record} e {@code retention_run_item}
 * têm {@code CHECK} que já recusa texto livre, então a varredura delas é provada pela montagem do
 * texto da tabela (a mesma consulta de {@code ai_call}).
 */
class PersonalDataSweepIT extends IntegrationBase {

    private static final Logger LOG = LoggerFactory.getLogger(PersonalDataSweepIT.class);

    private PersonalDataSweep sweep() {
        return PersonalDataSweep.of(jdbc);
    }

    private UUID aProcessingJob() {
        UUID teacher = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        jdbc.update(
                "insert into teacher (id, name, email, active, created_at, updated_at) values (?,"
                        + " 'Clara', 'c@example.test', true, now(), now())",
                teacher);
        jdbc.update(
                "insert into student (id, name, birth_date, cpf_encrypted, cpf_hash, active,"
                        + " created_at, updated_at) values (?, 'Aluno', '2012-05-10', '\\x00', 'h',"
                        + " true, now(), now())",
                student);
        jdbc.update(
                "insert into processing_job (id, type, teacher_id, student_id, request_channel, medium,"
                        + " parameters, status, step, attempts, delay_notice_sent, created_at) values"
                        + " (?, 'LESSON_REGISTRATION', ?, ?, 'WEB', 'AUDIO', '{}'::jsonb, 'COMPLETED',"
                        + " 'STRUCTURING', 0, false, now())",
                job,
                teacher,
                student);
        return job;
    }

    @Test
    void cleanDatabaseAndCleanLogPass() {
        try (LogCapture log = LogCapture.start()) {
            LOG.info("Linha de log sem dado pessoal.");
            LeakAudit audit = sweep().newAudit();

            sweep().sweep(audit, "clean", log);

            audit.assertClean();
            assertThat(audit.sweeps()).isGreaterThanOrEqualTo(6);
        }
    }

    @Test
    void valuePlantedInTheLogIsFoundAndTheMessageNeverCarriesIt() {
        try (LogCapture log = LogCapture.start()) {
            LOG.info("Processando {}", CanaryStudent.NAME);
            LeakAudit audit = sweep().newAudit();

            sweep().sweep(audit, "log", log);

            assertThatThrownBy(audit::assertClean)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("nome do aluno canário")
                    .hasMessageContaining("LOG")
                    .hasMessageContaining("log")
                    .hasMessageNotContaining(CanaryStudent.NAME);
        }
    }

    @Test
    void valuePlantedInAiCallIsFound() {
        UUID job = aProcessingJob();
        jdbc.update(
                "insert into ai_call (id, job_id, task, provider_model, duration_ms, success, created_at,"
                        + " channel) values (gen_random_uuid(), ?, 'TRANSCRIPTION', ?, 5, true, now(),"
                        + " 'WEB')",
                job,
                CanaryStudent.TRANSCRIPTION_SNIPPET);
        LeakAudit audit = sweep().newAudit();

        sweep().sweep(audit, "métricas", null);

        assertThatThrownBy(audit::assertClean)
                .hasMessageContaining("trecho de transcrição canário")
                .hasMessageContaining("AI_CALL")
                .hasMessageContaining("métricas")
                .hasMessageNotContaining(CanaryStudent.TRANSCRIPTION_SNIPPET);
    }

    @Test
    void valueInsideAJsonbColumnOfAiCallIsFound() {
        UUID job = aProcessingJob();
        jdbc.update("alter table ai_call add column probe jsonb");
        try {
            jdbc.update(
                    "insert into ai_call (id, job_id, task, duration_ms, success, created_at, channel,"
                            + " probe) values (gen_random_uuid(), ?, 'STRUCTURING', 5, true, now(), 'WEB',"
                            + " ?::jsonb)",
                    job,
                    "{\"texto\": \"" + CanaryStudent.REPORT_TEXT_SNIPPET + "\"}");
            LeakAudit audit = sweep().newAudit();

            sweep().sweep(audit, "jsonb", null);

            assertThatThrownBy(audit::assertClean)
                    .hasMessageContaining("trecho de texto de relatório canário")
                    .hasMessageContaining("AI_CALL");
        } finally {
            jdbc.update("alter table ai_call drop column probe");
        }
    }

    @Test
    void sweepValuesCoverEveryKindOfPersonalDatum() {
        assertThat(sweep().values())
                .containsValues(
                        CanaryStudent.NAME,
                        CanaryStudent.GUARDIAN_NAME,
                        CanaryStudent.TRANSCRIPTION_SNIPPET,
                        CanaryStudent.REPORT_TEXT_SNIPPET);
        assertThat(sweep().values().size()).isGreaterThan(CanaryStudent.sweepValues().size());
    }
}
