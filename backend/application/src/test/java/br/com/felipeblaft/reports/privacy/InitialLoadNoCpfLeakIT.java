package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.inbound.seed.CpfKeyProbe;
import br.com.felipeblaft.reports.inbound.seed.IssueCodeCommand;
import br.com.felipeblaft.reports.inbound.seed.RotateCpfKeyCommand;
import br.com.felipeblaft.reports.inbound.seed.SeedCommand;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.LeakAudit;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.support.PrivacyProbes;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * FR-020, FR-034a e SC-004 (carga inicial e rotação): a saída do terminal, o log e a autoria da
 * carga e da rotação das chaves não contêm o CPF canário nem os dados do aluno canário (nome,
 * nascimento e contato do responsável); os erros da carga citam seção, índice e campo, nunca o
 * valor. Usa os comandos reais sobre os casos de uso reais e o Postgres de teste.
 */
@ActiveProfiles("seed")
@TestPropertySource(
        properties = {
            "ROTATE_NEW_CPF_ENCRYPTION_KEY=" + CpfKeyProbe.NEW_ENCRYPTION_KEY,
            "ROTATE_NEW_HMAC_SECRET=" + CpfKeyProbe.NEW_HMAC_SECRET
        })
class InitialLoadNoCpfLeakIT extends IntegrationBase {

    @Autowired SeedCommand seed;
    @Autowired RotateCpfKeyCommand rotation;
    @Autowired IssueCodeCommand issueCode;

    @TempDir Path temporary;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    private String terminal() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static String canaryRow(String cpf, String phone, String email) {
        return "{\"cpf\":\""
                + cpf
                + "\",\"name\":\""
                + CanaryStudent.NAME
                + "\",\"birthDate\":\""
                + CanaryStudent.BIRTH_DATE_ISO
                + "\",\"guardianName\":\""
                + CanaryStudent.GUARDIAN_NAME
                + "\",\"guardianPhone\":\""
                + phone
                + "\",\"guardianEmail\":\""
                + email
                + "\",\"instrument\":\""
                + CanaryStudent.INSTRUMENT
                + "\",\"teachers\":[\"clara@example.test\"]}";
    }

    private Path file(String name, String students) throws Exception {
        Path file = temporary.resolve(name);
        Files.writeString(
                file,
                "{\"teachers\":[{\"email\":\"clara@example.test\",\"name\":\"Clara Souza\","
                        + "\"active\":true}],\"students\":["
                        + students
                        + "]}");
        return file;
    }

    @Test
    void terminalLogAndAuthorshipOfTheLoadAndTheRotationNeverCarryTheCanary() throws Exception {
        try (LogCapture logs = LogCapture.start()) {
            run(logs);
        }
    }

    private void run(LogCapture logs) throws Exception {
        LeakAudit audit = LeakAudit.searching(CanaryStudent.sweepValuesWithCpf());

        // carga válida
        Path valid =
                file(
                        "valid.json",
                        canaryRow(
                                CanaryCpf.MASKED,
                                CanaryStudent.GUARDIAN_PHONE_MASKED,
                                CanaryStudent.GUARDIAN_EMAIL));
        assertThat(seed.run(List.of("seed", "--file", valid.toString()), output)).isZero();
        assertThat(terminal()).contains("Carga concluída.").contains("Alunos: 1 criados");
        audit.text("terminal da carga", Place.LOG, terminal());
        bytes.reset();

        // simulação e reexecução idempotente
        assertThat(seed.run(List.of("seed", "--file", valid.toString(), "--simulate"), output))
                .isZero();
        audit.text("terminal da simulação", Place.LOG, terminal());
        bytes.reset();
        assertThat(seed.run(List.of("seed", "--file", valid.toString()), output)).isZero();
        assertThat(terminal()).contains("Alunos: 0 criados, 0 atualizados, 1 inalterados");
        audit.text("terminal da reexecução", Place.LOG, terminal());
        bytes.reset();

        // erros: cada um cita seção, índice e campo; os valores (dados canários) não voltam
        Path invalid =
                file(
                        "invalid.json",
                        canaryRow(
                                        "111.444.777-36",
                                        "(11) 94321-8765x",
                                        "balburdia.cantarelli@example")
                                + ","
                                + canaryRow(
                                        CanaryCpf.DIGITS,
                                        CanaryStudent.GUARDIAN_PHONE_MASKED,
                                        CanaryStudent.GUARDIAN_EMAIL)
                                + ","
                                + canaryRow(
                                        CanaryCpf.MASKED,
                                        CanaryStudent.GUARDIAN_PHONE_MASKED,
                                        CanaryStudent.GUARDIAN_EMAIL)
                                + ",{\"cpf\":\""
                                + CanaryCpf.MASKED
                                + "\",\"name\":\"\",\"birthDate\":\"14/03/2016\",\"teachers\":"
                                + "[\"missing@example.test\"]}");
        assertThat(seed.run(List.of("seed", "--file", invalid.toString()), output)).isEqualTo(1);
        assertThat(terminal())
                .contains("Carga recusada: nada foi gravado.")
                .containsPattern("students\\[\\d+]\\.[a-zA-Z]+: [A-Z_]+");
        audit.text("terminal da carga recusada", Place.LOG, terminal());
        bytes.reset();

        // arquivo ilegível que contém o CPF e os dados canários
        Path broken = temporary.resolve("broken.json");
        Files.writeString(
                broken,
                "{ \"students\": [ {\"cpf\":\""
                        + CanaryCpf.MASKED
                        + "\",\"name\":\""
                        + CanaryStudent.NAME
                        + "\" ");
        assertThat(seed.run(List.of("seed", "--file", broken.toString()), output)).isEqualTo(1);
        audit.text("terminal do arquivo ilegível", Place.LOG, terminal());
        bytes.reset();

        // reemissão de código (emergência) e rotação das chaves
        assertThat(issueCode.run(List.of("issue-code", "--email", "clara@example.test"), output))
                .isZero();
        audit.text("terminal da reemissão", Place.LOG, terminal());
        bytes.reset();
        assertThat(rotation.run(List.of("rotate-cpf-key"), output)).isEqualTo(1);
        audit.text("terminal da rotação sem confirmação", Place.LOG, terminal());
        bytes.reset();
        assertThat(rotation.run(List.of("rotate-cpf-key", "--simulate"), output)).isZero();
        audit.text("terminal da simulação da rotação", Place.LOG, terminal());
        bytes.reset();
        assertThat(rotation.run(List.of("rotate-cpf-key", "--confirm-invalidate-codes"), output))
                .isZero();
        assertThat(terminal()).contains("Rotação concluída").contains("Alunos recifrados: 1");
        audit.text("terminal da rotação", Place.LOG, terminal());

        // o que ficou: log, rastros, autoria e o banco (o aluno só na própria tabela)
        audit.text("log", Place.LOG, logs.text());
        audit.text("rastros de exceção", Place.EXCEPTION_TRACE, PrivacyProbes.traceText(logs));
        audit.text("authorship_record", Place.AUTHORSHIP, PrivacyProbes.authorshipText(jdbc));
        assertThat(PrivacyProbes.authorshipText(jdbc)).contains("INITIAL_LOAD");
        for (String table : PrivacyProbes.tables(jdbc)) {
            if (!table.equals("student")) {
                audit.text(
                        "tabela " + table, Place.AUTHORSHIP, PrivacyProbes.tableText(jdbc, table));
            }
        }
        audit.text("CPF cifrado na tabela do aluno", Place.AUTHORSHIP, studentCipherColumns());

        assertThat(audit.sweeps()).isGreaterThan(14);
        audit.assertClean();
    }

    /** As colunas do aluno que não são texto de cadastro: o CPF vive só cifrado e como resumo. */
    private String studentCipherColumns() {
        return jdbc.queryForObject(
                "select string_agg(encode(cpf_encrypted, 'escape') || ' ' || cpf_hash, ' ') from student",
                String.class);
    }
}
