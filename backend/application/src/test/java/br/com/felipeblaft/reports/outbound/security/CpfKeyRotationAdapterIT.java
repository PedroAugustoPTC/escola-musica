package br.com.felipeblaft.reports.outbound.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.inbound.seed.CpfKeyProbe;
import br.com.felipeblaft.reports.port.CpfKeyCollisionException;
import br.com.felipeblaft.reports.port.CpfKeyRotationPort.Options;
import br.com.felipeblaft.reports.port.CpfKeyRotationPort.Report;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.env.MockEnvironment;

/** Casos de borda do adaptador da rotação: chaves erradas ou ausentes, colisão e banco vazio. */
class CpfKeyRotationAdapterIT extends IntegrationBase {

    @Autowired ApplicationProperties properties;
    @Autowired StudentRepository students;
    @Autowired DataProtectionPort protection;

    private CpfKeyRotationAdapter adapter(MockEnvironment environment) {
        return new CpfKeyRotationAdapter(jdbc, environment, properties);
    }

    private Student save(String hash) {
        Student student =
                new Student(
                        br.com.felipeblaft.reports.domain.common.GeneratorId.create(),
                        "Aluno " + hash.charAt(0),
                        LocalDate.of(1990, 2, 1),
                        protection.encryptCpf(CanaryCpf.cpf()),
                        hash,
                        null,
                        null,
                        true,
                        clock.now(),
                        clock.now());
        students.save(student);
        return student;
    }

    @Test
    void emptyDatabaseRotatesNothing() {
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty(
                                CpfKeyRotationAdapter.NEW_ENCRYPTION_KEY,
                                CpfKeyProbe.NEW_ENCRYPTION_KEY);

        Report report = adapter(environment).rotate(new Options(true, false, false));

        assertThat(report).isEqualTo(new Report(0, 0));
    }

    @Test
    void rotatesMoreStudentsThanOneBatch() {
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty(
                                CpfKeyRotationAdapter.NEW_ENCRYPTION_KEY,
                                CpfKeyProbe.NEW_ENCRYPTION_KEY);
        for (int i = 0; i < 520; i++) {
            save(String.format("%064d", i));
        }

        Report report = adapter(environment).rotate(new Options(true, false, false));

        assertThat(report.rotatedStudents()).isEqualTo(520);
    }

    @Test
    void oldKeyThatDoesNotDecryptRefusesWithoutRevealingAnything() {
        Student student = save("c".repeat(64));
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty(
                                CpfKeyRotationAdapter.OLD_ENCRYPTION_KEY,
                                CpfKeyProbe.NEW_ENCRYPTION_KEY)
                        .withProperty(
                                CpfKeyRotationAdapter.NEW_ENCRYPTION_KEY,
                                CpfKeyProbe.NEW_ENCRYPTION_KEY);

        assertThatThrownBy(() -> adapter(environment).rotate(new Options(true, false, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CpfKeyRotationAdapter.OLD_ENCRYPTION_KEY)
                .hasMessageNotContaining(CanaryCpf.DIGITS);
        assertThat(CpfKeyProbe.decrypt(jdbc, student.id(), CpfKeyProbe.CURRENT_ENCRYPTION_KEY))
                .isEqualTo(CanaryCpf.DIGITS);
    }

    @Test
    void newKeyMissingOrMalformedIsRefused() {
        assertThatThrownBy(
                        () ->
                                adapter(new MockEnvironment())
                                        .rotate(new Options(true, false, false)))
                .hasMessageContaining(CpfKeyRotationAdapter.NEW_ENCRYPTION_KEY)
                .hasMessageContaining("não informada");
        assertThatThrownBy(
                        () ->
                                adapter(
                                                new MockEnvironment()
                                                        .withProperty(
                                                                CpfKeyRotationAdapter
                                                                        .NEW_ENCRYPTION_KEY,
                                                                "isto não é base64!"))
                                        .rotate(new Options(true, false, false)))
                .hasMessageContaining("Base64");
        assertThatThrownBy(
                        () ->
                                adapter(
                                                new MockEnvironment()
                                                        .withProperty(
                                                                CpfKeyRotationAdapter
                                                                        .NEW_ENCRYPTION_KEY,
                                                                "c2hvcnQ="))
                                        .rotate(new Options(true, false, false)))
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(
                        () ->
                                adapter(
                                                new MockEnvironment()
                                                        .withProperty(
                                                                CpfKeyRotationAdapter
                                                                        .NEW_HMAC_SECRET,
                                                                "short"))
                                        .rotate(new Options(false, true, false)))
                .hasMessageContaining("32 bytes");
    }

    @Test
    void newDigestSecretThatMakesTwoStudentsCollideUndoesBeforeWritingAnything() {
        // dois alunos com o mesmo CPF cifrado e resumos antigos diferentes passam a colidir
        Student first = save("d".repeat(64));
        Student second = save("e".repeat(64));
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty(
                                CpfKeyRotationAdapter.NEW_HMAC_SECRET, CpfKeyProbe.NEW_HMAC_SECRET);

        assertThatThrownBy(() -> adapter(environment).rotate(new Options(false, true, false)))
                .isInstanceOfSatisfying(
                        CpfKeyCollisionException.class,
                        collision -> assertThat(collision.collisions()).isEqualTo(1));
        assertThat(CpfKeyProbe.storedHash(jdbc, first.id())).isEqualTo("d".repeat(64));
        assertThat(CpfKeyProbe.storedHash(jdbc, second.id())).isEqualTo("e".repeat(64));
    }
}
