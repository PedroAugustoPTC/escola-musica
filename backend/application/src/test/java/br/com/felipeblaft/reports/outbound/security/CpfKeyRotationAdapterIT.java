package br.com.felipeblaft.reports.outbound.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Field;
import br.com.felipeblaft.reports.domain.telegram.StudentRegistrationDraft.Kind;
import br.com.felipeblaft.reports.domain.telegram.TelegramLink;
import br.com.felipeblaft.reports.inbound.seed.CpfKeyProbe;
import br.com.felipeblaft.reports.port.CpfKeyCollisionException;
import br.com.felipeblaft.reports.port.CpfKeyRotationPort.Options;
import br.com.felipeblaft.reports.port.CpfKeyRotationPort.Report;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRegistrationDraftRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.port.TelegramLinkRepository;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.env.MockEnvironment;

/** Casos de borda do adaptador da rotação: chaves erradas ou ausentes, colisão e banco vazio. */
class CpfKeyRotationAdapterIT extends IntegrationBase {

    @Autowired ApplicationProperties properties;
    @Autowired StudentRepository students;
    @Autowired DataProtectionPort protection;
    @Autowired TeacherRepository teachers;
    @Autowired TelegramLinkRepository links;
    @Autowired StudentRegistrationDraftRepository drafts;

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

        assertThat(report).isEqualTo(new Report(0, 0, 0));
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

    @Test
    void rotationDiscardsTelegramDraftsEncryptedWithTheOldKeyAndReportsOnlyTheCount() {
        Teacher teacher = Teacher.create("Clara Souza", "clara@example.test", true, clock.now());
        teachers.save(teacher);
        TelegramLink link = TelegramLink.create(teacher.id(), 7_001L, 8_001L, clock.now());
        links.save(link);
        Cpf cpf = CanaryCpf.cpf();
        drafts.save(
                new StudentRegistrationDraft(
                        link.id(),
                        Kind.CREATE,
                        null,
                        EnumSet.of(Field.NAME, Field.CPF),
                        CanaryStudent.NAME,
                        CanaryStudent.INSTRUMENT,
                        CanaryStudent.BIRTH_DATE,
                        protection.encryptCpf(cpf),
                        protection.hashCpf(cpf),
                        null,
                        null,
                        null,
                        "TOKEN12345",
                        clock.now(),
                        clock.now(),
                        clock.now().plus(Duration.ofMinutes(30))));
        save("f".repeat(64));
        MockEnvironment environment =
                new MockEnvironment()
                        .withProperty(
                                CpfKeyRotationAdapter.NEW_ENCRYPTION_KEY,
                                CpfKeyProbe.NEW_ENCRYPTION_KEY);

        Report report = adapter(environment).rotate(new Options(true, false, false));

        assertThat(report.discardedDrafts()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from student_registration_draft", Integer.class))
                .isZero();
        assertThat(report.toString())
                .doesNotContain(CanaryCpf.DIGITS)
                .doesNotContain(CanaryStudent.NAME);
    }
}
