package br.com.felipeblaft.reports.configuracao;

import br.com.felipeblaft.reports.port.LlmPort;
import br.com.felipeblaft.reports.port.TranscriptionPort;
import br.com.felipeblaft.reports.saida.ia.LlmOpenAiCompativelAdaptador;
import br.com.felipeblaft.reports.saida.ia.TranscricaoOpenAiCompativelAdaptador;
import br.com.felipeblaft.reports.saida.ia.simulado.LlmSimuladoAdaptador;
import br.com.felipeblaft.reports.saida.ia.simulado.TranscricaoSimuladaAdaptador;
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
public class ProvedoresIaConfiguracao {

    private static final Logger LOG = LoggerFactory.getLogger(ProvedoresIaConfiguracao.class);

    @Bean
    LlmPort llmPorta(PropriedadesAplicacao propriedades) {
        PropriedadesAplicacao.Provedores provedores = propriedades.provedores();
        if (provedores.simulado()) {
            LOG.info("provedores-ia modo=simulado");
            return new LlmSimuladoAdaptador();
        }
        LOG.info("provedores-ia modo=real llm={}", provedores.llm());
        return new LlmOpenAiCompativelAdaptador(provedores.llm());
    }

    @Bean
    TranscriptionPort transcricaoPorta(PropriedadesAplicacao propriedades) {
        PropriedadesAplicacao.Provedores provedores = propriedades.provedores();
        if (provedores.simulado()) {
            return new TranscricaoSimuladaAdaptador();
        }
        LOG.info("provedores-ia modo=real transcricao={}", provedores.transcricao());
        return new TranscricaoOpenAiCompativelAdaptador(provedores.transcricao());
    }
}
