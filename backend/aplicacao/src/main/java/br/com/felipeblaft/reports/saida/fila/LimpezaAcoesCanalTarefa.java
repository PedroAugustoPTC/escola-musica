package br.com.felipeblaft.reports.saida.fila;

import br.com.felipeblaft.reports.usecase.channel.CleanChannelActionsUseCase;
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

    private final CleanChannelActionsUseCase limpar;

    public LimpezaAcoesCanalTarefa(CleanChannelActionsUseCase limpar) {
        this.limpar = limpar;
    }

    @Scheduled(cron = "0 23 3 * * *", zone = "America/Sao_Paulo")
    public void limpar() {
        CleanChannelActionsUseCase.Result resultado = limpar.run();
        LOG.info(
                "limpeza-canal expiradas={} conteudos={} registros={} documentos={}",
                resultado.expiredActions(),
                resultado.resetPayloads(),
                resultado.removedRegistrations(),
                resultado.removedDocuments());
    }
}
