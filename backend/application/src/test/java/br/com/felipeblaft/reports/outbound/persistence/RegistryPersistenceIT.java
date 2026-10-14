package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.AccessCode;
import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.registry.Cpf;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.outbound.security.AccessDecryption;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RegistryPersistenceIT extends IntegrationBase {

    @Autowired TeacherRepository teachers;
    @Autowired StudentRepository students;
    @Autowired AssignmentRepository assignments;
    @Autowired DataProtectionPort protection;

    private final Instant now = Instant.parse("2026-09-21T13:00:00.123456Z");

    @Test
    void teacherRoundTrip() {
        Teacher clara = Teacher.create("Clara Souza", "clara@exemplo.test", true, now);
        clara.replaceCode(
                protection.hashAccessCode(AccessCode.of("K7QM2XPA")), now, Duration.ofDays(7));
        teachers.save(clara);

        Teacher read = teachers.searchByEmail("clara@exemplo.test").orElseThrow();
        assertThat(read.id()).isEqualTo(clara.id());
        assertThat(read.name()).isEqualTo("Clara Souza");
        assertThat(read.accessCodeExpiresAt())
                .isEqualTo(now.plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.MICROS));
        assertThat(teachers.searchByAccessCodeHash(clara.accessCodeHash()))
                .map(Teacher::id)
                .contains(clara.id());
        assertThat(teachers.searchById(clara.id())).isPresent();

        read.updateRegistry("Clara S.", false, now.plusSeconds(1));
        teachers.save(read);
        assertThat(teachers.searchById(clara.id()).orElseThrow().active()).isFalse();
    }

    @Test
    void encryptedCpfNeverReadableInDatabaseAndHmacStable() {
        Cpf cpf = Cpf.of("529.982.247-25");
        Student matteo =
                Student.create(
                        "Matteo Mafra",
                        LocalDate.of(2014, 5, 10),
                        protection.encryptCpf(cpf),
                        protection.hashCpf(cpf),
                        "Resp",
                        "Piano",
                        now);
        students.save(matteo);

        byte[] inDatabase =
                jdbc.queryForObject(
                        "select cpf_encrypted from student where id = ?",
                        byte[].class,
                        matteo.id());
        assertThat(new String(inDatabase, StandardCharsets.ISO_8859_1))
                .doesNotContain("52998224725");
        assertThat(inDatabase.length).isGreaterThan(12 + 11);
        assertThat(protection.encryptCpf(cpf)).isNotEqualTo(inDatabase);
        assertThat(protection.hashCpf(Cpf.of("52998224725")))
                .isEqualTo(matteo.cpfHash())
                .hasSize(64);
        assertThat(protection.hashCpf(cpf))
                .isNotEqualTo(protection.originHash(Channel.WEB, "52998224725"));
        assertThat(AccessDecryption.decrypt(protection, inDatabase)).isEqualTo("52998224725");

        String columns =
                String.join(
                        ",",
                        jdbc.queryForList(
                                "select column_name from information_schema.columns where table_name = 'student'",
                                String.class));
        assertThat(columns).doesNotContain("cpf,").contains("cpf_encrypted", "cpf_hash");

        Student read = students.searchByCpfHash(matteo.cpfHash()).orElseThrow();
        assertThat(read.name()).isEqualTo("Matteo Mafra");
        assertThat(read.encryptedCpf()).isEqualTo(inDatabase);
    }

    @Test
    void assignmentAndList() {
        Teacher clara = Teacher.create("Clara", "clara@exemplo.test", true, now);
        Teacher bruno = Teacher.create("Bruno", "bruno@exemplo.test", true, now);
        teachers.save(clara);
        teachers.save(bruno);
        Student olavo = student("Olavo Torrente", "123.456.789-09");
        Student matteo = student("matteo Mafra", "529.982.247-25");
        Student helena = student("Helena Prado", "318.264.597-82");
        assignments.save(TeacherStudent.create(clara.id(), olavo.id(), now));
        assignments.save(TeacherStudent.create(clara.id(), matteo.id(), now));
        assignments.save(TeacherStudent.create(bruno.id(), helena.id(), now));

        assertThat(assignments.exists(clara.id(), olavo.id())).isTrue();
        assertThat(assignments.exists(clara.id(), helena.id())).isFalse();
        assertThat(students.listByTeacher(clara.id()))
                .extracting(Student::name)
                .containsExactly("matteo Mafra", "Olavo Torrente");
    }

    private Student student(String name, String cpfText) {
        Cpf cpf = Cpf.of(cpfText);
        Student student =
                Student.create(
                        name,
                        LocalDate.of(2000, 1, 1),
                        protection.encryptCpf(cpf),
                        protection.hashCpf(cpf),
                        null,
                        null,
                        now);
        students.save(student);
        return student;
    }
}
