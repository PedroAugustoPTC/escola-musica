package br.com.felipeblaft.reports.config;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades da aplicação (prefixo {@code relatorios}), lidas do ambiente via application.yaml.
 */
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
        Jobs jobs) {

    public record School(ZoneId timezone) {}

    public record Audio(int limitMb, Path tempDir) {

        public long limitBytes() {
            return (long) limitMb * 1024 * 1024;
        }
    }

    public record Session(int hours, boolean cookieSecure) {}

    public record Access(int maxAttempts, int lockMinutes) {}

    public record Code(int validityDays) {}

    public record Security(String cpfEncryptionKey, String hmacSecret) {

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
            int pendingAudioMinutes) {

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
}
