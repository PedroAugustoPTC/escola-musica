package br.com.felipeblaft.reports.inbound.seed;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.DataProtectionPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Trocar só a chave de cifra reescreve {@code cpf_encrypted} e não tem efeito colateral: o resumo e
 * os códigos de acesso dos professores ficam como estão, e a confirmação não é exigida.
 */
@ActiveProfiles("seed")
@TestPropertySource(properties = "ROTATE_NEW_CPF_ENCRYPTION_KEY=" + CpfKeyProbe.NEW_ENCRYPTION_KEY)
class RotateCpfKeyEncryptionOnlyIT extends IntegrationBase {

    @Autowired RotateCpfKeyCommand command;
    @Autowired StudentRepository students;
    @Autowired TeacherRepository teachers;
    @Autowired DataProtectionPort protection;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    @Test
    void onlyTheCipherChangesAndTheCodesStayValid() {
        Student student =
                Student.create(
                        "Aluno Canário",
                        LocalDate.of(1990, 2, 1),
                        protection.encryptCpf(CanaryCpf.cpf()),
                        protection.hashCpf(CanaryCpf.cpf()),
                        null,
                        null,
                        null,
                        null,
                        clock.now());
        students.save(student);
        Teacher teacher = Teacher.create("Clara Souza", "clara@example.test", true, clock.now());
        teacher.replaceCode("b".repeat(64), clock.now(), Duration.ofDays(7));
        teachers.save(teacher);
        String hashBefore = CpfKeyProbe.storedHash(jdbc, student.id());

        int code = command.run(List.of("rotate-cpf-key"), output);

        String text = bytes.toString(StandardCharsets.UTF_8);
        assertThat(code).isZero();
        assertThat(text)
                .contains("Rotação concluída")
                .contains("Alunos recifrados: 1")
                .contains("Códigos de acesso invalidados: 0")
                .doesNotContain("Atenção", CanaryCpf.DIGITS);
        assertThat(CpfKeyProbe.decrypt(jdbc, student.id(), CpfKeyProbe.NEW_ENCRYPTION_KEY))
                .isEqualTo(CanaryCpf.DIGITS);
        assertThat(CpfKeyProbe.storedHash(jdbc, student.id())).isEqualTo(hashBefore);
        assertThat(
                        jdbc.queryForObject(
                                "select access_code_hash from teacher where id = ?",
                                String.class,
                                teacher.id()))
                .isEqualTo("b".repeat(64));
    }
}
