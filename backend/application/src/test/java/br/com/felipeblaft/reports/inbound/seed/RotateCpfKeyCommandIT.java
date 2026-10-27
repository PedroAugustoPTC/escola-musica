package br.com.felipeblaft.reports.inbound.seed;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Cpf;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Comando {@code rotate-cpf-key} contra o Postgres de teste, com as duas chaves novas no ambiente.
 * Confere a recifragem e o novo resumo, a confirmação exigida quando o segredo do resumo muda, o
 * aviso do efeito sobre os códigos de acesso e que a saída nunca traz o CPF. A simulação ({@code
 * --simulate}, que depende de {@code TransactionPort.runAndRollBack}) entra no Fecho (S5), quando o
 * adaptador de transação do fluxo W-G estiver integrado.
 */
@ActiveProfiles("seed")
@TestPropertySource(
        properties = {
            "ROTATE_NEW_CPF_ENCRYPTION_KEY=" + CpfKeyProbe.NEW_ENCRYPTION_KEY,
            "ROTATE_NEW_HMAC_SECRET=" + CpfKeyProbe.NEW_HMAC_SECRET
        })
class RotateCpfKeyCommandIT extends IntegrationBase {

    @Autowired RotateCpfKeyCommand command;
    @Autowired StudentRepository students;
    @Autowired TeacherRepository teachers;
    @Autowired DataProtectionPort protection;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    private Student canary;
    private Student other;
    private Teacher teacher;

    @BeforeEach
    void createData() {
        Instant now = clock.now();
        canary = student("Aluno Canário", CanaryCpf.cpf(), now);
        other = student("Outro Aluno", Cpf.of("529.982.247-25"), now);
        teacher = Teacher.create("Clara Souza", "clara@example.test", true, now);
        teacher.replaceCode("a".repeat(64), now, Duration.ofDays(7));
        teachers.save(teacher);
    }

    private Student student(String name, Cpf cpf, Instant now) {
        Student student =
                Student.create(
                        name,
                        LocalDate.of(1990, 2, 1),
                        protection.encryptCpf(cpf),
                        protection.hashCpf(cpf),
                        null,
                        null,
                        null,
                        "Piano",
                        now);
        students.save(student);
        return student;
    }

    private String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private boolean teacherHasCode() {
        return jdbc.queryForObject(
                        "select access_code_hash is not null from teacher where id = ?",
                        Boolean.class,
                        teacher.id())
                .booleanValue();
    }

    @Test
    void withoutTheConfirmationNothingIsChangedAndTheEffectIsExplained() {
        String hashBefore = CpfKeyProbe.storedHash(jdbc, canary.id());

        int code = command.run(List.of("rotate-cpf-key"), output);

        assertThat(code).isEqualTo(1);
        assertThat(text())
                .contains("invalida os códigos de acesso vigentes")
                .contains("AUTH_HMAC_SECRET")
                .contains("--confirm-invalidate-codes");
        assertThat(CpfKeyProbe.storedHash(jdbc, canary.id())).isEqualTo(hashBefore);
        assertThat(CpfKeyProbe.decrypt(jdbc, canary.id(), CpfKeyProbe.CURRENT_ENCRYPTION_KEY))
                .isEqualTo(CanaryCpf.DIGITS);
        assertThat(teacherHasCode()).isTrue();
    }

    @Test
    void confirmedRotationRewritesCipherAndDigestInvalidatesCodesAndPrintsOnlyCounts() {
        int code = command.run(List.of("rotate-cpf-key", "--confirm-invalidate-codes"), output);

        assertThat(code).isZero();
        assertThat(text())
                .contains("Rotação concluída")
                .contains("Alunos recifrados: 2")
                .contains("Códigos de acesso invalidados: 1")
                .doesNotContain(CanaryCpf.DIGITS, CanaryCpf.MASKED, "52998224725", "529.982");
        assertThat(CpfKeyProbe.decrypt(jdbc, canary.id(), CpfKeyProbe.NEW_ENCRYPTION_KEY))
                .isEqualTo(CanaryCpf.DIGITS);
        assertThat(CpfKeyProbe.decrypt(jdbc, canary.id(), CpfKeyProbe.CURRENT_ENCRYPTION_KEY))
                .isNull();
        assertThat(CpfKeyProbe.storedHash(jdbc, canary.id()))
                .isEqualTo(CpfKeyProbe.hash(CanaryCpf.DIGITS, CpfKeyProbe.NEW_HMAC_SECRET));
        assertThat(CpfKeyProbe.decrypt(jdbc, other.id(), CpfKeyProbe.NEW_ENCRYPTION_KEY))
                .isEqualTo("52998224725");
        assertThat(CpfKeyProbe.storedHash(jdbc, other.id()))
                .isEqualTo(CpfKeyProbe.hash("52998224725", CpfKeyProbe.NEW_HMAC_SECRET));
        assertThat(teacherHasCode()).isFalse();
    }
}
