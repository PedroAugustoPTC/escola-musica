package br.com.felipeblaft.reports.usecase.seed;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Princípio IV: a representação textual dos tipos da carga inicial nunca traz dado pessoal. */
class SeedTypesToStringTest {

    private static final String CPF = "52998224725";
    private static final String MASKED_CPF = "529.982.247-25";
    private static final String STUDENT = "Matteo Souza";
    private static final String GUARDIAN = "Helena Souza";
    private static final String TEACHER = "Clara Dias";
    private static final String EMAIL = "clara@example.test";

    @Test
    void seedFileAndItsEntriesNeverRevealPersonalData() {
        SeedFile.SeedTeacher teacher = new SeedFile.SeedTeacher(EMAIL, TEACHER, true);
        SeedFile.SeedStudent student =
                new SeedFile.SeedStudent(
                        MASKED_CPF, STUDENT, "2014-05-10", GUARDIAN, "Violão", List.of(EMAIL));
        SeedFile file = new SeedFile(List.of(teacher), List.of(student));

        for (String text : List.of(teacher.toString(), student.toString(), file.toString())) {
            assertThat(text)
                    .doesNotContain(
                            CPF, MASKED_CPF, STUDENT, GUARDIAN, TEACHER, EMAIL, "2014-05-10");
        }
        assertThat(file.toString()).contains("teachers=1", "students=1");
    }

    @Test
    void issuedCodeHidesTheCodeAndTheEmail() {
        SeedResult.IssuedCode issued =
                new SeedResult.IssuedCode(EMAIL, "K7QM2XPA", Instant.parse("2026-10-20T12:00:00Z"));

        assertThat(issued.toString()).doesNotContain(EMAIL, "K7QM2XPA");
    }

    @Test
    void seedResultNeverRevealsTheIssuedCodesOrErrorValues() {
        SeedResult result =
                new SeedResult(
                        false,
                        new SeedResult.Count(1, 0, 0),
                        new SeedResult.Count(1, 0, 0),
                        1,
                        List.of(
                                new SeedResult.SeedError(
                                        "students", 0, "cpf", ErrorCode.RESOURCE_NOT_FOUND)),
                        List.of(
                                new SeedResult.IssuedCode(
                                        EMAIL, "K7QM2XPA", Instant.parse("2026-10-20T12:00:00Z"))));

        assertThat(result.toString()).doesNotContain(EMAIL, "K7QM2XPA", CPF);
    }
}
