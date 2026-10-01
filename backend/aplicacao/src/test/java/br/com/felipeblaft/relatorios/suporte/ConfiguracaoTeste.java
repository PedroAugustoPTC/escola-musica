package br.com.felipeblaft.relatorios.suporte;

import br.com.felipeblaft.relatorios.porta.NormalizacaoAudioPorta;
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

    /** O FFmpeg real é coberto por FfmpegNormalizacaoAudioAdaptadorIT. */
    @Bean
    @Primary
    NormalizacaoAudioPorta normalizacaoAudioTeste() {
        return new NormalizacaoAudioTeste();
    }
}
