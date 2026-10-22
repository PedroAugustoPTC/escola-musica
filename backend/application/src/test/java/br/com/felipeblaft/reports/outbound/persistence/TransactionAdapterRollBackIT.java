package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import br.com.felipeblaft.reports.port.TransactionPort;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code runAndRollBack} (carga inicial com {@code --simulate}): o trecho roda de verdade, devolve
 * o resultado e nada do que gravou permanece, nem o registro de autoria (T130).
 */
class TransactionAdapterRollBackIT extends RegistryPersistenceBase {

    @Autowired TransactionPort transactions;
    @Autowired AuthorshipRecorderPort recorder;

    private AuthorshipRecord authorship() {
        return new AuthorshipRecord(
                UUID.randomUUID(),
                Instant.parse("2026-09-21T13:00:00Z"),
                UUID.randomUUID(),
                Role.ADMINISTRATOR,
                ActionOrigin.WEB,
                "TEACHER_REGISTER",
                AuthorshipTargetType.TEACHER,
                UUID.randomUUID(),
                AuthorshipResult.SUCCESS,
                null,
                null,
                "req-ab12cd");
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void undoesEverythingIncludingAuthorshipAndReturnsTheResult() {
        int[] seenInside = new int[3];

        String result =
                transactions.runAndRollBack(
                        () -> {
                            Teacher clara = teacher("Clara Souza", true);
                            Student matteo = studentOf(clara, "Matteo Ferreira");
                            openReport(clara, matteo);
                            recorder.record(authorship());
                            seenInside[0] = count("student");
                            seenInside[1] = count("teacher_student_assignment");
                            seenInside[2] = count("authorship_record");
                            return "outcome";
                        });

        assertThat(result).isEqualTo("outcome");
        // dentro do trecho o que foi gravado era visível (a carga simulada valida de verdade)
        assertThat(seenInside).containsExactly(1, 1, 1);
        for (String table :
                new String[] {
                    "teacher",
                    "student",
                    "teacher_student_assignment",
                    "lesson",
                    "lesson_report",
                    "authorship_record"
                }) {
            assertThat(count(table)).as(table).isZero();
        }
    }

    @Test
    void failureInsideTheExcerptPropagatesAndLeavesNothing() {
        assertThatThrownBy(
                        () ->
                                transactions.runAndRollBack(
                                        () -> {
                                            student("Aluno Fictício");
                                            recorder.record(authorship());
                                            throw new IllegalStateException("falha simulada");
                                        }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("student")).isZero();
        assertThat(count("authorship_record")).isZero();
    }

    @Test
    void doesNotUndoWhatWasCommittedBeforeNorAfter() {
        student("Aluno Anterior");

        transactions.runAndRollBack(() -> student("Aluno Simulado"));
        student("Aluno Posterior");

        assertThat(jdbc.queryForList("select name from student order by name", String.class))
                .containsExactly("Aluno Anterior", "Aluno Posterior");
    }

    @Test
    void runStillCommitsNormally() {
        transactions.run(() -> student("Aluno Gravado"));

        assertThat(count("student")).isEqualTo(1);
    }
}
