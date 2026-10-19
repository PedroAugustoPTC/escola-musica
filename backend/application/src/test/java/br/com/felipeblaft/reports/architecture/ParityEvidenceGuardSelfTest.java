package br.com.felipeblaft.reports.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Autoteste do guardião da evidência de paridade: fixtures completas passam e cada fixture
 * incompleta é reprovada pela regra certa (FR-029).
 */
class ParityEvidenceGuardSelfTest {

    private static String fixture(String name) throws IOException {
        try (InputStream stream =
                ParityEvidenceGuardSelfTest.class.getResourceAsStream("/parity-evidence/" + name)) {
            assertThat(stream).as("fixture %s", name).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest(name = "{0} é aceita")
    @MethodSource("accepted")
    @DisplayName("evidência completa ou dispensa formal passam")
    void acceptsCompleteOrWaived(String name) throws IOException {
        assertThat(ParityEvidenceGuard.check(fixture(name))).isEmpty();
    }

    static Stream<String> accepted() {
        return Stream.of(
                "valid-evidence.md", "valid-evidence-isolated-duplicate.md", "valid-waiver.md");
    }

    @ParameterizedTest(name = "{0} reprova por {1}")
    @MethodSource("rejected")
    @DisplayName("evidência incompleta é reprovada pela regra certa")
    void rejectsIncomplete(String name, String rule) throws IOException {
        List<String> violations = ParityEvidenceGuard.check(fixture(name));

        assertThat(violations).isNotEmpty();
        assertThat(violations).anySatisfy(v -> assertThat(v).contains(rule));
    }

    static Stream<Arguments> rejected() {
        return Stream.of(
                Arguments.of("evidence-short-observation.md", "observationDays"),
                Arguments.of("evidence-signal-not-zero.md", "updatesWithoutReply"),
                Arguments.of("evidence-lost-callback.md", "lostCallbackConfirmations"),
                Arguments.of(
                        "evidence-definitive-failure.md",
                        "proactiveDefinitiveFailuresFromTransport"),
                Arguments.of("evidence-duplicate-without-action.md", "duplicateReplies"),
                Arguments.of("evidence-two-duplicates.md", "duplicateReplies"),
                Arguments.of("evidence-teacher-report.md", "teacherReports"),
                Arguments.of("evidence-empty-suite.md", "suiteRun"),
                Arguments.of("evidence-without-approver.md", "approvedBy"),
                Arguments.of("evidence-without-approval-date.md", "approvedAt"),
                Arguments.of("evidence-not-recorded.md", "observationDays"),
                Arguments.of("waiver-without-reason.md", "reason"),
                Arguments.of("waiver-without-author.md", "waivedBy"),
                Arguments.of("waiver-without-date.md", "waivedAt"),
                Arguments.of("without-mode.md", "mode"),
                Arguments.of("unknown-mode.md", "mode"));
    }

    @Test
    @DisplayName("arquivo ausente reprova")
    void missingFileIsRejected() {
        assertThat(ParityEvidenceGuard.check(null))
                .anySatisfy(v -> assertThat(v).contains("não existe"));
    }
}
