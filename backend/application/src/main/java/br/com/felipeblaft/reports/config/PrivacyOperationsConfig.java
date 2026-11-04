package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.config.ApplicationProperties.Alerts;
import br.com.felipeblaft.reports.config.ApplicationProperties.Costs;
import br.com.felipeblaft.reports.config.ApplicationProperties.PriceProperty;
import br.com.felipeblaft.reports.config.ApplicationProperties.Privacy;
import br.com.felipeblaft.reports.config.ApplicationProperties.Providers;
import br.com.felipeblaft.reports.config.ApplicationProperties.Retention;
import br.com.felipeblaft.reports.domain.operations.AlertThresholds;
import br.com.felipeblaft.reports.domain.operations.PriceEntry;
import br.com.felipeblaft.reports.domain.operations.PriceTable;
import br.com.felipeblaft.reports.domain.operations.PriceUnit;
import br.com.felipeblaft.reports.domain.privacy.PrivacyOfficer;
import br.com.felipeblaft.reports.domain.privacy.RetentionPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Monta os parâmetros da feature 007 a partir das propriedades brutas (FR-001, FR-002, FR-052,
 * FR-055). Quem decide o efetivo é o núcleo; aqui só se entrega o texto e se registram os avisos.
 * Valor inválido nunca impede a subida e o aviso cita a variável, jamais o valor.
 */
@Configuration(proxyBeanMethods = false)
public class PrivacyOperationsConfig {

    private static final Logger LOG = LoggerFactory.getLogger(PrivacyOperationsConfig.class);

    @Bean
    RetentionPolicy retentionPolicy(ApplicationProperties properties) {
        return retentionPolicyOf(properties.retention());
    }

    @Bean
    AlertThresholds alertThresholds(ApplicationProperties properties) {
        return alertThresholdsOf(properties.alerts());
    }

    @Bean
    PriceTable priceTable(ApplicationProperties properties) {
        return priceTableOf(properties.costs(), legacyPrices(properties.providers()));
    }

    @Bean
    PrivacyOfficer privacyOfficer(ApplicationProperties properties) {
        return privacyOfficerOf(properties.privacy());
    }

    static RetentionPolicy retentionPolicyOf(Retention retention) {
        Map<String, String> raw = new HashMap<>();
        if (retention != null) {
            raw.put("RETENTION_TRANSCRIPTION_DAYS", retention.transcriptionDays());
            raw.put("RETENTION_UNAPPROVED_PDF_DAYS", retention.unapprovedPdfDays());
            raw.put("RETENTION_CANCELLED_REPORT_DAYS", retention.cancelledReportDays());
            raw.put("RETENTION_AUDIT_TRAIL_DAYS", retention.auditTrailDays());
            raw.put("RETENTION_RUN_HISTORY_DAYS", retention.runHistoryDays());
        }
        RetentionPolicy policy = RetentionPolicy.from(raw);
        policy.warnings().forEach(LOG::warn);
        return policy;
    }

    static AlertThresholds alertThresholdsOf(Alerts alerts) {
        Map<String, String> raw = new HashMap<>();
        if (alerts != null) {
            raw.put("ALERT_PROVIDER_CONSECUTIVE_FAILURES", alerts.providerConsecutiveFailures());
            raw.put("ALERT_PROVIDER_FAILURE_RATE_PERCENT", alerts.providerFailureRatePercent());
            raw.put("ALERT_PROVIDER_WINDOW_MINUTES", alerts.providerWindowMinutes());
            raw.put("ALERT_QUEUE_MAX_AGE_MINUTES", alerts.queueMaxAgeMinutes());
            raw.put("ALERT_QUEUE_MAX_ITEMS", alerts.queueMaxItems());
            raw.put("ALERT_STORAGE_QUIET_MINUTES", alerts.storageQuietMinutes());
            raw.put("ALERT_EVALUATION_SECONDS", alerts.evaluationSeconds());
        }
        AlertThresholds thresholds = AlertThresholds.from(raw);
        thresholds.warnings().forEach(LOG::warn);
        return thresholds;
    }

    static PriceTable priceTableOf(Costs costs, List<PriceEntry> legacy) {
        List<PriceEntry> configured = new ArrayList<>();
        BigDecimal infrastructure = BigDecimal.ZERO;
        LocalDate infrastructureFrom = null;
        if (costs != null) {
            List<PriceProperty> prices = costs.prices() == null ? List.of() : costs.prices();
            for (int index = 0; index < prices.size(); index++) {
                PriceEntry entry = parse(prices.get(index), index);
                if (entry != null) {
                    configured.add(entry);
                }
            }
            infrastructure =
                    decimalOrZero(
                            costs.infrastructureMonthlyUsd(), "COSTS_INFRASTRUCTURE_MONTHLY_USD");
            infrastructureFrom =
                    dateOrNull(
                            costs.infrastructureEffectiveFrom(),
                            "COSTS_INFRASTRUCTURE_EFFECTIVE_FROM");
        }
        PriceTable table = PriceTable.from(configured, legacy, infrastructure, infrastructureFrom);
        table.warnings().forEach(LOG::warn);
        return table;
    }

    static PrivacyOfficer privacyOfficerOf(Privacy privacy) {
        PrivacyOfficer officer =
                privacy == null
                        ? new PrivacyOfficer("", "")
                        : new PrivacyOfficer(privacy.officerName(), privacy.officerContact());
        if (!officer.defined()) {
            LOG.warn(
                    "Responsável pela LGPD não definido: configure PRIVACY_OFFICER_NAME e"
                            + " PRIVACY_OFFICER_CONTACT; as telas mostram \"a definir pela escola\".");
        }
        return officer;
    }

    private static PriceEntry parse(PriceProperty price, int index) {
        String where = "COSTS_PRICES_" + index;
        try {
            if (price == null || isBlank(price.provider())) {
                throw new IllegalArgumentException("provider");
            }
            return new PriceEntry(
                    price.provider().trim(),
                    PriceUnit.valueOf(price.unit() == null ? "" : price.unit().trim()),
                    new BigDecimal(price.valueUsd() == null ? "" : price.valueUsd().trim()),
                    LocalDate.parse(
                            price.effectiveFrom() == null ? "" : price.effectiveFrom().trim()));
        } catch (IllegalArgumentException | DateTimeParseException invalid) {
            LOG.warn("Preço configurado inválido em {}; a entrada foi ignorada.", where);
            return null;
        }
    }

    private static BigDecimal decimalOrZero(String raw, String variable) {
        if (isBlank(raw)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException invalid) {
            LOG.warn("Variável {} inválida; usando zero.", variable);
            return BigDecimal.ZERO;
        }
    }

    private static LocalDate dateOrNull(String raw, String variable) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException invalid) {
            LOG.warn("Variável {} inválida; usando a vigência desde sempre.", variable);
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Propriedades de preço anteriores à feature 007, como entradas válidas desde 1970-01-01. */
    private static List<PriceEntry> legacyPrices(Providers providers) {
        List<PriceEntry> legacy = new ArrayList<>();
        if (providers == null) {
            return legacy;
        }
        if (providers.transcription() != null
                && providers.transcription().priceUsdPerHour() != null) {
            legacy.add(
                    new PriceEntry(
                            String.valueOf(providers.transcription().model()),
                            PriceUnit.AUDIO_HOUR,
                            providers.transcription().priceUsdPerHour(),
                            LocalDate.EPOCH));
        }
        if (providers.llm() != null) {
            if (providers.llm().priceUsdPerMillionInput() != null) {
                legacy.add(
                        new PriceEntry(
                                String.valueOf(providers.llm().model()),
                                PriceUnit.INPUT_MILLION_TOKENS,
                                providers.llm().priceUsdPerMillionInput(),
                                LocalDate.EPOCH));
            }
            if (providers.llm().priceUsdPerMillionOutput() != null) {
                legacy.add(
                        new PriceEntry(
                                String.valueOf(providers.llm().model()),
                                PriceUnit.OUTPUT_MILLION_TOKENS,
                                providers.llm().priceUsdPerMillionOutput(),
                                LocalDate.EPOCH));
            }
        }
        return legacy;
    }
}
