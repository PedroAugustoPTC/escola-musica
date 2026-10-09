package br.com.felipeblaft.relatorios.saida.fila;

import br.com.felipeblaft.relatorios.casodeuso.canal.LimparAcoesCanalCasoDeUso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limpeza diária da outbox (R-11), na janela da varredura de acesso: zera o conteúdo das ações
 * finalizadas depois da retenção, apaga os registros antigos e os documentos vencidos.
 */
@Component
@Profile("!carga")
public class LimpezaAcoesCanalTarefa {

    private static final Logger LOG = LoggerFactory.getLogger(LimpezaAcoesCanalTarefa.class);

    private final LimparAcoesCanalCasoDeUso limpar;

    public LimpezaAcoesCanalTarefa(LimparAcoesCanalCasoDeUso limpar) {
        this.limpar = limpar;
    }

    @Scheduled(cron = "0 23 3 * * *", zone = "America/Sao_Paulo")
    public void limpar() {
        LimparAcoesCanalCasoDeUso.Resultado resultado = limpar.executar();
        LOG.info(
                "limpeza-canal expiradas={} conteudos={} registros={} documentos={}",
                resultado.acoesExpiradas(),
                resultado.payloadsZerados(),
                resultado.registrosRemovidos(),
                resultado.documentosRemovidos());
    }
}
