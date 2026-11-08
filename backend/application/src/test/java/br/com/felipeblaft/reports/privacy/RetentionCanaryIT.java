package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.LeakAudit;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.support.PersonalDataSweep;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import br.com.felipeblaft.reports.usecase.privacy.RunRetentionUseCase;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Retenção e dado pessoal (SC-001, SC-003): depois da rotina, o trecho de transcrição do aluno
 * canário não existe em nenhuma coluna do relatório vencido, e a varredura não acha o canário em
 * logs, trilha, alertas nem nos itens da execução.
 */
class RetentionCanaryIT extends SchoolHistoryBase {

    @Autowired RunRetentionUseCase retention;

    @Test
    void expiredSourceLeavesNoCanaryInTheReportNorInLogsTrailOrRunItems() {
        Teacher clara = scenario.clara();
        Student student = scenario.student(clara, CanaryStudent.NAME, "Violão");
        UUID report =
                scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 10), "Escalas").id();
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
        assertThat(rowsContaining("lesson_report", CanaryStudent.TRANSCRIPTION_SNIPPET)).isOne();

        PersonalDataSweep sweep = PersonalDataSweep.of(jdbc);
        LeakAudit audit = sweep.newAudit();
        try (LogCapture logs = LogCapture.start()) {
            clock.advance(Duration.ofDays(31));
            RunRetentionUseCase.Result result =
                    retention.run(Actor.retention(), UUID.randomUUID().toString());
            assertThat(result.failures()).isZero();
            sweep.sweep(audit, "retenção", logs);
        }

        assertThat(rowsContaining("lesson_report", CanaryStudent.TRANSCRIPTION_SNIPPET)).isZero();
        assertThat(rowsContaining("report_interaction", CanaryStudent.REPORT_TEXT_SNIPPET))
                .isZero();
        assertThat(audit.sweeps()).isPositive();
        audit.assertClean();
    }

    /** Linhas da tabela cujo texto completo (todas as colunas) contém o trecho. */
    private int rowsContaining(String table, String snippet) {
        return jdbc.queryForObject(
                "select count(*) from " + table + " t where t::text like ?",
                Integer.class,
                "%" + snippet + "%");
    }
}
