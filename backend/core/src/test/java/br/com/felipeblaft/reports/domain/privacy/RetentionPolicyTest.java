package br.com.felipeblaft.reports.domain.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RetentionPolicyTest {

    @Test
    @DisplayName(
            "sem configuração, valem os padrões de 30, 30, 90 e 1826 dias, todos de origem DEFAULT")
    void absentValuesUseDefaults() {
        RetentionPolicy policy = RetentionPolicy.from(Map.of());

        assertThat(policy.transcriptionDays().value()).isEqualTo(30);
        assertThat(policy.unapprovedPdfDays().value()).isEqualTo(30);
        assertThat(policy.cancelledReportDays().value()).isEqualTo(90);
        assertThat(policy.auditTrailDays().value()).isEqualTo(1826);
        assertThat(policy.runHistoryDays().value()).isEqualTo(1826);
        assertThat(policy.transcriptionDays().origin()).isEqualTo(PolicyOrigin.DEFAULT);
        assertThat(policy.warnings()).isEmpty();
    }

    @Test
    @DisplayName("valor válido é configurado e a origem do campo passa a CONFIGURED")
    void validValueIsConfigured() {
        RetentionPolicy policy =
                RetentionPolicy.from(
                        Map.of(
                                "RETENTION_TRANSCRIPTION_DAYS",
                                "45",
                                "RETENTION_AUDIT_TRAIL_DAYS",
                                "400"));

        assertThat(policy.transcriptionDays().value()).isEqualTo(45);
        assertThat(policy.transcriptionDays().origin()).isEqualTo(PolicyOrigin.CONFIGURED);
        assertThat(policy.auditTrailDays().value()).isEqualTo(400);
        assertThat(policy.unapprovedPdfDays().origin()).isEqualTo(PolicyOrigin.DEFAULT);
    }

    @Test
    @DisplayName("o histórico das execuções segue o prazo da trilha quando não é configurado")
    void runHistoryFollowsAuditTrail() {
        RetentionPolicy policy = RetentionPolicy.from(Map.of("RETENTION_AUDIT_TRAIL_DAYS", "500"));

        assertThat(policy.runHistoryDays().value()).isEqualTo(500);
        assertThat(policy.runHistoryDays().origin()).isEqualTo(PolicyOrigin.DEFAULT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "abc", "1.5", "99999999999999"})
    @DisplayName("zero, negativo ou não numérico volta ao padrão e avisa citando só a variável")
    void invalidValueFallsBackWithWarning(String raw) {
        RetentionPolicy policy =
                RetentionPolicy.from(Map.of("RETENTION_CANCELLED_REPORT_DAYS", raw));

        assertThat(policy.cancelledReportDays().value()).isEqualTo(90);
        assertThat(policy.cancelledReportDays().origin()).isEqualTo(PolicyOrigin.DEFAULT);
        assertThat(policy.warnings()).hasSize(1);
        assertThat(policy.warnings().get(0)).contains("RETENTION_CANCELLED_REPORT_DAYS");
        if (raw.length() > 2) {
            assertThat(policy.warnings().get(0)).doesNotContain(raw);
        }
    }

    @Test
    @DisplayName("valor em branco ou nulo conta como ausente, sem aviso")
    void blankIsAbsent() {
        Map<String, String> source = new HashMap<>();
        source.put("RETENTION_TRANSCRIPTION_DAYS", "  ");
        source.put("RETENTION_UNAPPROVED_PDF_DAYS", null);

        assertThat(RetentionPolicy.from(source).warnings()).isEmpty();
    }
}
