package br.com.felipeblaft.reports.domain.privacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prazos de retenção efetivos, em dias (FR-001, FR-002). A aplicação entrega os valores brutos,
 * indexados pelo nome da variável de ambiente; o núcleo decide o efetivo. Valor inválido nunca
 * impede a subida: vale o padrão e sobe um aviso.
 */
public record RetentionPolicy(
        PolicyValue transcriptionDays,
        PolicyValue unapprovedPdfDays,
        PolicyValue cancelledReportDays,
        PolicyValue auditTrailDays,
        PolicyValue runHistoryDays,
        List<String> warnings) {

    public static final int DEFAULT_TRANSCRIPTION_DAYS = 30;
    public static final int DEFAULT_UNAPPROVED_PDF_DAYS = 30;
    public static final int DEFAULT_CANCELLED_REPORT_DAYS = 90;
    public static final int DEFAULT_AUDIT_TRAIL_DAYS = 1826;
    private static final int MAXIMUM_DAYS = 36_500;

    public RetentionPolicy {
        warnings = List.copyOf(warnings);
    }

    public static RetentionPolicy from(Map<String, String> source) {
        List<String> warnings = new ArrayList<>();
        PolicyValue audit =
                PolicyInput.read(
                        source,
                        "RETENTION_AUDIT_TRAIL_DAYS",
                        DEFAULT_AUDIT_TRAIL_DAYS,
                        MAXIMUM_DAYS,
                        warnings);
        PolicyValue runHistory =
                PolicyInput.read(
                        source,
                        "RETENTION_RUN_HISTORY_DAYS",
                        audit.value(),
                        MAXIMUM_DAYS,
                        warnings);
        return new RetentionPolicy(
                PolicyInput.read(
                        source,
                        "RETENTION_TRANSCRIPTION_DAYS",
                        DEFAULT_TRANSCRIPTION_DAYS,
                        MAXIMUM_DAYS,
                        warnings),
                PolicyInput.read(
                        source,
                        "RETENTION_UNAPPROVED_PDF_DAYS",
                        DEFAULT_UNAPPROVED_PDF_DAYS,
                        MAXIMUM_DAYS,
                        warnings),
                PolicyInput.read(
                        source,
                        "RETENTION_CANCELLED_REPORT_DAYS",
                        DEFAULT_CANCELLED_REPORT_DAYS,
                        MAXIMUM_DAYS,
                        warnings),
                audit,
                runHistory,
                warnings);
    }
}
