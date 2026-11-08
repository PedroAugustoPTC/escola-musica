package br.com.felipeblaft.reports.inbound.seed;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.privacy.ConsentTermVersion;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ConsentTermRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.support.ScenarioIT;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * Comando {@code consent-load} (contrato {@code consent-load.md}, FR-036): linhas válidas, inválidas
 * e repetidas; resumo só com número da linha e código; caminho dentro de {@code /work} recusado;
 * evento {@code CONSENT_RECORD} com origem {@code INITIAL_LOAD}; contagem final de pendentes; nada
 * do arquivo em log.
 */
@ActiveProfiles("seed")
class ConsentLoadCommandIT extends IntegrationBase {

    private static final String HEADER =
            "student_id;consenting_name;relationship;consented_on;term_version;collection_method";
    private static final String SENTINEL_NAME = "Sentinel Consenting Person";

    @Autowired ConsentLoadCommand command;
    @Autowired ScenarioIT scenario;
    @Autowired ConsentTermRepository terms;

    @TempDir Path directory;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    private Student first;
    private Student second;

    @BeforeEach
    void prepare() {
        Teacher clara = scenario.clara();
        first = scenario.studentWithoutConsent(clara, "Matteo Ferreira", "Piano");
        second = scenario.studentWithoutConsent(clara, "Lia Souza", "Violin");
        terms.save(
                new ConsentTermVersion(
                        UUID.randomUUID(),
                        1,
                        LocalDate.of(2026, 1, 1),
                        "Institutional text",
                        false,
                        UUID.randomUUID(),
                        clock.now()));
    }

    private Path file(String... lines) throws IOException {
        Path path = directory.resolve("consents.csv");
        Files.write(path, List.of(lines), StandardCharsets.UTF_8);
        return path;
    }

    private String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private int run(Path path) {
        return command.run(List.of("consent-load", "--file", path.toString()), output);
    }

    @Test
    void loadsValidRowsAndReportsOnlyTheLineNumberAndCodeOfTheInvalidOnes() throws Exception {
        Path path =
                file(
                        HEADER,
                        first.id() + ";" + SENTINEL_NAME + ";MOTHER;2026-03-01;1;SIGNED_PAPER",
                        UUID.randomUUID() + ";" + SENTINEL_NAME + ";MOTHER;2026-03-01;1;DIGITAL",
                        second.id() + ";" + SENTINEL_NAME + ";AUNT;2026-03-01;1;DIGITAL",
                        "not-an-id;" + SENTINEL_NAME + ";MOTHER;2026-03-01;1;DIGITAL");

        int code;
        try (LogCapture logs = LogCapture.start()) {
            code = run(path);
            assertThat(logs.text()).doesNotContain(SENTINEL_NAME);
        }

        assertThat(code).isEqualTo(1);
        assertThat(text())
                .contains("aceitos: 1", "recusados: 3")
                .contains("linha 3: RESOURCE_NOT_FOUND")
                .contains("linha 4: VALIDATION_FAILED")
                .contains("linha 5: INVALID_DATA")
                .doesNotContain(SENTINEL_NAME)
                .doesNotContain("AUNT");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from student_consent where student_id = ?",
                                Integer.class,
                                first.id()))
                .isEqualTo(1);
    }

    @Test
    void recordsTheEventWithTheInitialLoadOriginAndNoAuthor() throws Exception {
        run(file(HEADER, first.id() + ";" + SENTINEL_NAME + ";FATHER;2026-03-01;1;DIGITAL"));

        var event =
                jdbc.queryForMap(
                        "select origin, author_role, author_id, operation, target_type"
                                + " from authorship_record where operation = 'CONSENT_RECORD'");
        assertThat(event.get("origin")).isEqualTo("INITIAL_LOAD");
        assertThat(event.get("author_role")).isNull();
        assertThat(event.get("author_id")).isNull();
        assertThat(event.get("target_type")).isEqualTo("CONSENT");
    }

    @Test
    void repeatingTheSameRowIsCountedAsAlreadyRecordedAndCreatesNoDuplicate() throws Exception {
        Path path =
                file(HEADER, first.id() + ";" + SENTINEL_NAME + ";MOTHER;2026-03-01;1;DIGITAL");
        run(path);
        bytes.reset();

        int code = run(path);

        assertThat(code).isZero();
        assertThat(text()).contains("aceitos: 0", "já registrados: 1");
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from student_consent", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void printsHowManyMinorsStillLackAValidConsent() throws Exception {
        run(file(HEADER, first.id() + ";" + SENTINEL_NAME + ";MOTHER;2026-03-01;1;DIGITAL"));

        assertThat(text()).contains("ainda sem consentimento válido: 1");
    }

    @Test
    void refusesAPathInsideTheRepositoryVolumeAndAWrongHeader() throws Exception {
        assertThat(command.run(List.of("consent-load", "--file", "/work/consents.csv"), output))
                .isEqualTo(2);
        assertThat(text()).contains("não pode ficar dentro do repositório");

        bytes.reset();
        assertThat(run(file("wrong;header", "x;y"))).isEqualTo(2);
        assertThat(text()).contains("Cabeçalho inválido");
    }
}
