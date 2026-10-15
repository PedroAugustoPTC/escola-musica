package br.com.felipeblaft.reports.support;

import br.com.felipeblaft.reports.port.AudioNormalizationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Beans que substituem adaptadores reais nos testes de integração. */
@TestConfiguration(proxyBeanMethods = false)
public class ConfigTest {

    @Bean
    @Primary
    AdjustableClock adjustableClock() {
        return new AdjustableClock();
    }

    /**
     * O FFmpeg real é coberto por FfmpegNormalizacaoAudioAdaptadorIT; a suíte com IA real desliga o
     * dublê com {@code relatorios.teste.normalizacao-simulada=false}.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(
            name = "reports.test.simulated-normalization",
            havingValue = "true",
            matchIfMissing = true)
    AudioNormalizationPort audioNormalizationTest() {
        return new AudioNormalizationTest();
    }
}
