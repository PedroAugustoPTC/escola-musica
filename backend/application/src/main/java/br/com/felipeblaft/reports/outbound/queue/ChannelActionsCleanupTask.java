package br.com.felipeblaft.reports.outbound.queue;

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
@Profile("!seed")
public class ChannelActionsCleanupTask {

    private static final Logger LOG = LoggerFactory.getLogger(ChannelActionsCleanupTask.class);

    private final CleanChannelActionsUseCase clean;

    public ChannelActionsCleanupTask(CleanChannelActionsUseCase clean) {
        this.clean = clean;
    }

    @Scheduled(cron = "0 23 3 * * *", zone = "America/Sao_Paulo")
    public void clean() {
        CleanChannelActionsUseCase.Result result = clean.run();
        LOG.info(
                "limpeza-canal expiradas={} conteudos={} registros={} documentos={}",
                result.expiredActions(),
                result.resetPayloads(),
                result.removedRegistrations(),
                result.removedDocuments());
    }
}
