package br.com.felipeblaft.reports.config;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Propriedades da aplicação (prefixo {@code reports}), lidas do ambiente via application.yaml. */
@ConfigurationProperties(prefix = "reports")
public record ApplicationProperties(
        School school,
        Audio audio,
        Session session,
        Access access,
        Code code,
        Security security,
        Documents documents,
        Telegram telegram,
        TelegramChannel telegramChannel,
        Providers providers,
        Jobs jobs,
        Administrator administrator,
        Retention retention,
        Alerts alerts,
        Costs costs,
        Privacy privacy) {

    public record School(ZoneId timezone) {}

    public record Audio(int limitMb, Path tempDir) {

        public long limitBytes() {
            return (long) limitMb * 1024 * 1024;
        }
    }

    public record Session(int hours, boolean cookieSecure) {}

    /**
     * Bloqueio por tentativas. {@code cpfProbe} limita as recusas por CPF de aluno alheio e {@code
     * cpfLookup} limita as consultas de CPF digitado pelo professor (feature 005, FR-024).
     */
    public record Access(
            int maxAttempts, int lockMinutes, CpfProbe cpfProbe, CpfLookup cpfLookup) {}

    public record CpfProbe(int maxRefusals, int windowMinutes, int lockMinutes) {}

    public record CpfLookup(int maxLookups, int windowMinutes, int lockMinutes) {}

    public record Code(int validityDays) {}

    public record Security(String cpfEncryptionKey, String hmacSecret, String authHmacSecret) {

        @Override
        public String toString() {
            return "Security[***]";
        }
    }

    public record Documents(Path dir) {}

    public record Telegram(
            boolean enabled,
            String apiUrl,
            String token,
            String webhookSecret,
            Path filesDir,
            int pendingAudioMinutes,
            int studentDraftMinutes) {

        @Override
        public String toString() {
            return "Telegram[enabled=" + enabled + ", apiUrl=" + apiUrl + "]";
        }
    }

    /**
     * API interna do canal Telegram (feature 002): tokens de serviço, entrega ao orquestrador,
     * retentativa e retenção da outbox (data-model.md §4).
     */
    public record TelegramChannel(
            String currentToken,
            String nextToken,
            String orchestratorUrl,
            String orchestratorToken,
            int connectionTimeoutMs,
            int responseTimeoutMs,
            int retryInitialSeconds,
            int retryCeilingSeconds,
            int deadlineHours,
            int confirmationMaxSeconds,
            int leaseSeconds,
            long dispatchIntervalMs,
            int batchMaxPerCycle,
            int payloadRetentionHours,
            int recordRetentionDays) {

        public boolean configuredOrchestrator() {
            return orchestratorUrl != null && !orchestratorUrl.isBlank();
        }

        @Override
        public String toString() {
            return "TelegramChannel[***]";
        }
    }

    public record Providers(String mode, Transcription transcription, Llm llm) {

        public boolean simulated() {
            return !"real".equalsIgnoreCase(mode);
        }
    }

    public record Transcription(String url, String model, String key, BigDecimal priceUsdPerHour) {

        @Override
        public String toString() {
            return "Transcription[url=" + url + ", model=" + model + "]";
        }
    }

    public record Llm(
            String url,
            String model,
            String key,
            BigDecimal priceUsdPerMillionInput,
            BigDecimal priceUsdPerMillionOutput) {

        @Override
        public String toString() {
            return "Llm[url=" + url + ", model=" + model + "]";
        }
    }

    public record Jobs(int workers, long intervalMs) {}

    /**
     * Acesso do administrador (feature 005): sessão fixa, senha provisória, custo do Argon2id e
     * primeiro administrador lido do ambiente (R-04, R-16).
     */
    public record Administrator(
            int sessionHours,
            int provisionalPasswordDays,
            PasswordHashing password,
            Bootstrap bootstrap) {}

    /** Parâmetros do Argon2id; paralelismo, sal e tamanho da saída são fixos no adaptador. */
    public record PasswordHashing(int memoryKib, int iterations) {}

    /** Primeiro administrador; sem valor padrão. A senha nunca vai a log. */
    public record Bootstrap(String name, String email, String password) {

        @Override
        public String toString() {
            return "Bootstrap[***]";
        }
    }

    /**
     * Retenção (feature 007, FR-001): prazos em dias e horário da rotina. Os valores chegam BRUTOS
     * (texto): o núcleo decide o efetivo e valor inválido vira o padrão com aviso, nunca falha de
     * subida.
     */
    public record Retention(
            String transcriptionDays,
            String unapprovedPdfDays,
            String cancelledReportDays,
            String auditTrailDays,
            String runHistoryDays,
            String cron) {}

    /** Limites dos alertas operacionais (FR-055), também brutos. */
    public record Alerts(
            String providerConsecutiveFailures,
            String providerFailureRatePercent,
            String providerWindowMinutes,
            String queueMaxAgeMinutes,
            String queueMaxItems,
            String storageQuietMinutes,
            String evaluationSeconds) {}

    /** Preços por provedor com vigência e custo fixo mensal de infraestrutura (FR-052). */
    public record Costs(
            java.util.List<PriceProperty> prices,
            String infrastructureMonthlyUsd,
            String infrastructureEffectiveFrom) {}

    /** Uma entrada da tabela de preços, ainda como texto. */
    public record PriceProperty(
            String provider, String unit, String valueUsd, String effectiveFrom) {}

    /** Responsável pela LGPD mostrado nas telas (D11). */
    public record Privacy(String officerName, String officerContact) {}
}
