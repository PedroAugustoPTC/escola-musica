package br.com.felipeblaft.reports.inbound.seed;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.support.IntegrationBase;
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

@ActiveProfiles("seed")
class SeedCommandIT extends IntegrationBase {

    private static final String EXAMPLE = Path.of("../../infra/carga/exemplo.json").toString();

    @Autowired SeedCommand seed;
    @Autowired IssueCodeCommand issueCode;

    @TempDir Path temporary;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    private String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void exampleSeedCreatesRegistriesAndIssuesCodes() {
        int code = seed.run(List.of("seed", "--file", EXAMPLE), output);

        assertThat(code).isZero();
        assertThat(text())
                .contains("Carga concluída.")
                .contains("Professores: 2 criados, 0 atualizados, 0 inalterados")
                .contains("Alunos: 4 criados, 0 atualizados, 0 inalterados")
                .contains("Associações criadas: 4")
                .containsPattern(
                        "clara@example\\.test → [A-HJ-NP-Z2-9]{8} \\(válido até 28/09/2026 10:00\\)")
                .containsPattern("bruno@example\\.test → [A-HJ-NP-Z2-9]{8}")
                .doesNotContain("529.982.247-25", "52998224725");
        assertThat(count("teacher")).isEqualTo(2);
        assertThat(count("student")).isEqualTo(4);
        assertThat(count("teacher_student_assignment")).isEqualTo(4);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from teacher where access_code_hash is not null",
                                Integer.class))
                .isEqualTo(2);
    }

    @Test
    void secondRunIsIdempotent() {
        seed.run(List.of("seed", "--file", EXAMPLE), output);
        bytes.reset();

        int code = seed.run(List.of("seed", "--file=" + EXAMPLE), output);

        assertThat(code).isZero();
        assertThat(text())
                .contains("Professores: 0 criados, 0 atualizados, 2 inalterados")
                .contains("Alunos: 0 criados, 0 atualizados, 4 inalterados")
                .contains("Associações criadas: 0")
                .doesNotContain("→");
        assertThat(count("student")).isEqualTo(4);
    }

    @Test
    void fileWithErrorSavesNothing() throws Exception {
        Path file = temporary.resolve("error.json");
        Files.writeString(
                file,
                """
                {"teachers":[{"email":"clara@example.test","name":"Clara"}],
                 "students":[{"cpf":"529.982.247-25","name":"Matteo","birthDate":"2014-05-10",
                            "guardianName":"R","teachers":["clara@example.test"]},
                           {"cpf":"111.111.111-11","name":"X","birthDate":"2000-01-01",
                            "teachers":[]}]}
                """);

        int code = seed.run(List.of("seed", "--file", file.toString()), output);

        assertThat(code).isEqualTo(1);
        assertThat(text())
                .contains("Carga recusada: nada foi gravado.")
                .contains("students[1].cpf: INVALID_CPF — CPF inválido.")
                .doesNotContain("111.111");
        assertThat(count("teacher")).isZero();
        assertThat(count("student")).isZero();
    }

    @Test
    void simulateDoesNotSave() {
        int code = seed.run(List.of("seed", "--file", EXAMPLE, "--simulate"), output);

        assertThat(code).isZero();
        assertThat(text())
                .contains("Simulação concluída: nada foi gravado.")
                .contains("Alunos: 4 criados");
        assertThat(count("teacher")).isZero();
        assertThat(count("student")).isZero();
    }

    @Test
    void invalidArgumentsAndFile() throws Exception {
        assertThat(seed.run(List.of("seed"), output)).isEqualTo(2);
        assertThat(seed.run(List.of("seed", "--file", "/missing/exists.json"), output))
                .isEqualTo(1);
        Path invalid = temporary.resolve("invalid.json");
        Files.writeString(invalid, "{ nao é json");
        assertThat(seed.run(List.of("seed", "--file", invalid.toString()), output)).isEqualTo(1);
        assertThat(text()).contains("Uso: seed").contains("Não foi possível ler o arquivo");
    }

    @Test
    void issueCodeReplacesHash() {
        seed.run(List.of("seed", "--file", EXAMPLE), output);
        String hashBefore =
                jdbc.queryForObject(
                        "select access_code_hash from teacher where email = 'clara@example.test'",
                        String.class);
        bytes.reset();

        int code = issueCode.run(List.of("issue-code", "--email", "clara@example.test"), output);

        assertThat(code).isZero();
        assertThat(text())
                .containsPattern("clara@example\\.test → [A-HJ-NP-Z2-9]{8} \\(válido até");
        String hashAfter =
                jdbc.queryForObject(
                        "select access_code_hash from teacher where email = 'clara@example.test'",
                        String.class);
        assertThat(hashAfter).isNotEqualTo(hashBefore).hasSize(64);
    }

    @Test
    void issueCodeForMissingTeacher() {
        assertThat(issueCode.run(List.of("issue-code"), output)).isEqualTo(2);
        assertThat(issueCode.run(List.of("issue-code", "--email", "x@y.z"), output)).isEqualTo(1);
        assertThat(text()).contains("Professor não encontrado");
    }
}
