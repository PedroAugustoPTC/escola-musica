package br.com.felipeblaft.relatorios.configuracao;

import br.com.felipeblaft.relatorios.porta.LlmPorta;
import br.com.felipeblaft.relatorios.porta.TranscricaoPorta;
import br.com.felipeblaft.relatorios.saida.ia.LlmOpenAiCompativelAdaptador;
import br.com.felipeblaft.relatorios.saida.ia.TranscricaoOpenAiCompativelAdaptador;
import br.com.felipeblaft.relatorios.saida.ia.simulado.LlmSimuladoAdaptador;
import br.com.felipeblaft.relatorios.saida.ia.simulado.TranscricaoSimuladaAdaptador;
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
    LlmPorta llmPorta(PropriedadesAplicacao propriedades) {
        PropriedadesAplicacao.Provedores provedores = propriedades.provedores();
        if (provedores.simulado()) {
            LOG.info("provedores-ia modo=simulado");
            return new LlmSimuladoAdaptador();
        }
        LOG.info("provedores-ia modo=real llm={}", provedores.llm());
        return new LlmOpenAiCompativelAdaptador(provedores.llm());
    }

    @Bean
    TranscricaoPorta transcricaoPorta(PropriedadesAplicacao propriedades) {
        PropriedadesAplicacao.Provedores provedores = propriedades.provedores();
        if (provedores.simulado()) {
            return new TranscricaoSimuladaAdaptador();
        }
        LOG.info("provedores-ia modo=real transcricao={}", provedores.transcricao());
        return new TranscricaoOpenAiCompativelAdaptador(provedores.transcricao());
    }
}
