package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.support.ResponseSweeper;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * O CPF só existe cifrado e como resumo com chave: nunca em claro em coluna, log ou mensagem de
 * erro (T110, FR-011, R-25).
 */
class StudentCpfStorageIT extends RegistryPersistenceBase {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

    private Student canary() {
        Cpf cpf = CanaryCpf.cpf();
        return Student.create(
                CanaryStudent.NAME,
                CanaryStudent.BIRTH_DATE,
                protection.encryptCpf(cpf),
                protection.hashCpf(cpf),
                CanaryStudent.GUARDIAN_NAME,
                CanaryStudent.GUARDIAN_PHONE_DIGITS,
                CanaryStudent.GUARDIAN_EMAIL,
                CanaryStudent.INSTRUMENT,
                now());
    }

    private ResponseSweeper cpfSweeper() {
        return ResponseSweeper.searching(CanaryCpf.sweepValues());
    }

    @Test
    void cpfIsStoredEncryptedWithKeyedHashOnly() throws Exception {
        Student student = canary();
        students.save(student);

        byte[] encrypted =
                jdbc.queryForObject(
                        "select cpf_encrypted from student where id = ?",
                        byte[].class,
                        student.id());
        String hash =
                jdbc.queryForObject(
                        "select cpf_hash from student where id = ?", String.class, student.id());

        assertThat(new String(encrypted, StandardCharsets.ISO_8859_1))
                .doesNotContain(CanaryCpf.DIGITS);
        assertThat(encrypted).isNotEqualTo(CanaryCpf.DIGITS.getBytes(StandardCharsets.UTF_8));
        assertThat(hash).matches(HEX_64).isEqualTo(protection.hashCpf(CanaryCpf.cpf()));
        // resumo com chave: não é o SHA-256 simples do CPF
        String plainSha256 =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(CanaryCpf.DIGITS.getBytes(StandardCharsets.UTF_8)));
        assertThat(hash).isNotEqualTo(plainSha256).doesNotContain(CanaryCpf.DIGITS);
        // cifra com nonce: cifrar duas vezes dá bytes diferentes, o resumo continua estável
        assertThat(protection.encryptCpf(CanaryCpf.cpf())).isNotEqualTo(encrypted);
        assertThat(protection.hashCpf(CanaryCpf.cpf())).isEqualTo(hash);
    }

    @Test
    void noColumnOfAnyTableHoldsTheCpfInClear() {
        students.save(canary());
        students.insertIfAbsent(canary());
        List<String> tables =
                jdbc.queryForList(
                        "select tablename from pg_tables where schemaname = 'public'"
                                + " and tablename <> 'flyway_schema_history'",
                        String.class);
        assertThat(tables).contains("student", "student_registration_draft");

        for (String table : tables) {
            List<String> rows =
                    jdbc.queryForList("select t::text from " + table + " t", String.class);
            for (String row : rows) {
                assertThat(cpfSweeper().sweepText(Place.BODY, row))
                        .as("tabela %s", table)
                        .isEmpty();
            }
        }
    }

    @Test
    void persistenceAndDuplicateErrorNeverLeakTheCpf() {
        try (LogCapture log = LogCapture.start()) {
            Student first = canary();
            students.save(first);
            assertThat(students.insertIfAbsent(canary())).isFalse();
            students.searchByCpfHash(protection.hashCpf(CanaryCpf.cpf()));
            students.findVisible(
                    br.com.felipeblaft.reports.domain.registry.StudentScope.all(), first.id());

            // duplicidade que escapa ao insertIfAbsent: a mensagem do banco não traz o CPF
            Throwable failure =
                    org.assertj.core.api.Assertions.catchThrowable(() -> students.save(canary()));
            assertThat(failure).isNotNull();
            StringBuilder messages = new StringBuilder();
            for (Throwable t = failure; t != null; t = t.getCause()) {
                messages.append(t.getMessage()).append('\n');
            }
            assertThat(cpfSweeper().sweepText(Place.ERROR_MESSAGE, messages.toString())).isEmpty();
            assertThat(cpfSweeper().sweepText(Place.LOG, log.text())).isEmpty();
        }
    }

    @Test
    void studentToStringHidesPersonalData() {
        Student student = canary();

        assertThat(
                        ResponseSweeper.searching(CanaryStudent.sweepValuesWithCpf())
                                .sweepText(Place.LOG, student.toString()))
                .isEmpty();
    }

    @Test
    void sameCpfInDifferentFormatsProducesTheSameHash() {
        Student student = canary();
        students.save(student);

        assertThat(students.searchByCpfHash(protection.hashCpf(Cpf.of(CanaryCpf.MASKED))))
                .map(Student::id)
                .contains(student.id());
        assertThatThrownBy(() -> Cpf.of("111.111.111-11")).isInstanceOf(RuntimeException.class);
    }
}
