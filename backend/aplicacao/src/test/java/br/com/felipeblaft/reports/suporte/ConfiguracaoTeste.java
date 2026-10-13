package br.com.felipeblaft.reports.suporte;

import br.com.felipeblaft.reports.port.AudioNormalizationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Beans que substituem adaptadores reais nos testes de integração. */
@TestConfiguration(proxyBeanMethods = false)
public class ConfiguracaoTeste {

    @Bean
    @Primary
    RelogioAjustavel relogioAjustavel() {
        return new RelogioAjustavel();
    }

    /**
     * O FFmpeg real é coberto por FfmpegNormalizacaoAudioAdaptadorIT; a suíte com IA real desliga o
     * dublê com {@code relatorios.teste.normalizacao-simulada=false}.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(
            name = "relatorios.teste.normalizacao-simulada",
            havingValue = "true",
            matchIfMissing = true)
    AudioNormalizationPort normalizacaoAudioTeste() {
        return new NormalizacaoAudioTeste();
    }
}
