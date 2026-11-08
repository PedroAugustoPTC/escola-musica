package br.com.felipeblaft.reports.config;

import br.com.felipeblaft.reports.domain.operations.PriceTable;
import br.com.felipeblaft.reports.outbound.ai.LlmOpenAiCompatibleAdapter;
import br.com.felipeblaft.reports.outbound.ai.TranscriptionOpenAiCompatibleAdapter;
import br.com.felipeblaft.reports.outbound.ai.simulated.SimulatedLlmAdapter;
import br.com.felipeblaft.reports.outbound.ai.simulated.SimulatedTranscriptionAdapter;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.LlmPort;
import br.com.felipeblaft.reports.port.TranscriptionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Escolhe os provedores de IA por {@code PROVEDORES_IA} (R-08): {@code real} usa as APIs
 * compatíveis com OpenAI configuradas por ambiente (URL, modelo, chave e preços); qualquer outro
 * valor usa os adaptadores simulados e determinísticos.
 */
@Configuration(proxyBeanMethods = false)
public class AiProvidersConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AiProvidersConfig.class);

    @Bean
    LlmPort llmPort(ApplicationProperties properties, PriceTable prices, ClockPort clock) {
        ApplicationProperties.Providers providers = properties.providers();
        if (providers.simulated()) {
            LOG.info("ai-providers mode=simulated");
            return new SimulatedLlmAdapter();
        }
        LOG.info("ai-providers mode=real llm={}", providers.llm());
        return new LlmOpenAiCompatibleAdapter(providers.llm(), prices, clock);
    }

    @Bean
    TranscriptionPort transcriptionPort(
            ApplicationProperties properties, PriceTable prices, ClockPort clock) {
        ApplicationProperties.Providers providers = properties.providers();
        if (providers.simulated()) {
            return new SimulatedTranscriptionAdapter();
        }
        LOG.info("ai-providers mode=real transcription={}", providers.transcription());
        return new TranscriptionOpenAiCompatibleAdapter(providers.transcription(), prices, clock);
    }
}
