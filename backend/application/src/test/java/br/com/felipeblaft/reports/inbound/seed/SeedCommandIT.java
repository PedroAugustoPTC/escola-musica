package br.com.felipeblaft.reports.inbound.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.felipeblaft.reports.config.MessageCatalog;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.support.IntegrationBase;
import br.com.felipeblaft.reports.usecase.seed.LoadInitialRegistryUseCase;
import br.com.felipeblaft.reports.usecase.seed.SeedFile;
import br.com.felipeblaft.reports.usecase.seed.SeedResult;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.Count;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.IssuedCode;
import br.com.felipeblaft.reports.usecase.seed.SeedResult.SeedError;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Comando {@code seed} (argumentos, leitura do arquivo e saída), com a carga inicial substituída
 * por um dublê. O comportamento com os casos de uso reais e o Postgres está em {@link
 * SeedCommandFullStackIT} (reativado no Fecho, S5).
 */
@ActiveProfiles("seed")
class SeedCommandIT extends IntegrationBase {

    private static final String EXAMPLE = Path.of("../../infra/seed/example.json").toString();

    @Autowired SeedCommand seed;
    @Autowired MessageCatalog catalog;

    @MockitoBean LoadInitialRegistryUseCase useCase;

    @TempDir Path temporary;

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    private String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static SeedResult success(boolean simulation) {
        return new SeedResult(
                simulation,
                new Count(2, 0, 0),
                new Count(4, 1, 0),
                4,
                List.of(),
                simulation
                        ? List.of()
                        : List.of(
                                new IssuedCode(
                                        "clara@example.test",
                                        "K7QM2XPA",
                                        Instant.parse("2026-09-28T13:00:00Z"))));
    }

    @Test
    void exampleFileCarriesTheGuardianContactOfEveryMinorToTheUseCase() {
        when(useCase.run(any(), eq(false))).thenReturn(success(false));

        int code = seed.run(List.of("seed", "--file", EXAMPLE), output);

        assertThat(code).isZero();
        ArgumentCaptor<SeedFile> file = ArgumentCaptor.forClass(SeedFile.class);
        verify(useCase).run(file.capture(), eq(false));
        assertThat(file.getValue().teachers()).hasSize(2);
        assertThat(file.getValue().students()).hasSize(4);
        assertThat(file.getValue().students())
                .filteredOn(student -> student.guardianName() != null)
                .hasSize(3)
                .allSatisfy(
                        student -> {
                            assertThat(student.guardianPhone()).matches("\\(\\d{2}\\) 9\\d{4}-\\d{4}");
                            assertThat(student.guardianEmail()).endsWith("@example.test");
                        });
    }

    @Test
    void successPrintsCountsAndTheCodeOnceWithoutAnyCpf() {
        when(useCase.run(any(), eq(false))).thenReturn(success(false));

        seed.run(List.of("seed", "--file=" + EXAMPLE), output);

        assertThat(text())
                .contains("Carga concluída.")
                .contains("Professores: 2 criados, 0 atualizados, 0 inalterados")
                .contains("Alunos: 4 criados, 1 atualizados, 0 inalterados")
                .contains("Associações criadas: 4")
                .containsPattern("clara@example\\.test → K7QM2XPA \\(válido até 28/09/2026 10:00\\)")
                .doesNotContain("529.982.247-25", "52998224725");
    }

    @Test
    void simulateIsForwardedAndAnnounced() {
        when(useCase.run(any(), eq(true))).thenReturn(success(true));

        int code = seed.run(List.of("seed", "--file", EXAMPLE, "--simulate"), output);

        assertThat(code).isZero();
        verify(useCase).run(any(), eq(true));
        assertThat(text())
                .contains("Simulação concluída: nada foi gravado.")
                .doesNotContain("→");
    }

    @Test
    void refusedFileListsSectionIndexFieldAndMessageNeverTheValue() {
        when(useCase.run(any(), anyBoolean()))
                .thenReturn(
                        new SeedResult(
                                false,
                                new Count(0, 0, 0),
                                new Count(0, 0, 0),
                                0,
                                List.of(
                                        new SeedError("students", 1, "cpf", ErrorCode.INVALID_CPF),
                                        new SeedError(
                                                "students",
                                                2,
                                                "record",
                                                ErrorCode.VALIDATION_FAILED),
                                        new SeedError(
                                                "teachers",
                                                0,
                                                "email",
                                                ErrorCode.DUPLICATE_EMAIL_IN_FILE)),
                                List.of()));

        int code = seed.run(List.of("seed", "--file", EXAMPLE), output);

        assertThat(code).isEqualTo(1);
        assertThat(text())
                .contains("Carga recusada: nada foi gravado.")
                .contains("students[1].cpf: INVALID_CPF — O CPF informado não é válido.")
                .contains("students[2].record: VALIDATION_FAILED")
                .contains("teachers[0].email: DUPLICATE_EMAIL_IN_FILE")
                .doesNotContain("111.111", "529.982");
    }

    @Test
    void errorCodeWithoutCatalogTextPrintsOnlyTheCode() {
        ErrorCode withoutText =
                Arrays.stream(ErrorCode.values())
                        .filter(code -> !catalog.contains(code.messageKey()))
                        .findFirst()
                        .orElse(null);
        assumeTrue(withoutText != null, "todo código tem texto no catálogo");
        when(useCase.run(any(), anyBoolean()))
                .thenReturn(
                        new SeedResult(
                                false,
                                new Count(0, 0, 0),
                                new Count(0, 0, 0),
                                0,
                                List.of(new SeedError("students", 0, "x", withoutText)),
                                List.of()));

        assertThat(seed.run(List.of("seed", "--file", EXAMPLE), output)).isEqualTo(1);
        assertThat(text()).contains("students[0].x: " + withoutText.name()).doesNotContain("—");
    }

    @Test
    void invalidArgumentsAndFileNeverReachTheUseCase() throws Exception {
        assertThat(seed.run(List.of("seed"), output)).isEqualTo(2);
        assertThat(seed.run(List.of("seed", "--file", "/missing/exists.json"), output))
                .isEqualTo(1);
        Path invalid = temporary.resolve("invalid.json");
        Files.writeString(invalid, "{ nao é json");
        assertThat(seed.run(List.of("seed", "--file", invalid.toString()), output)).isEqualTo(1);

        assertThat(text()).contains("Uso: seed").contains("Não foi possível ler o arquivo");
        verify(useCase, never()).run(any(), anyBoolean());
    }
}
