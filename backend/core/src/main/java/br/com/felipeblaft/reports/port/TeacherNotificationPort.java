package br.com.felipeblaft.reports.port;

import br.com.felipeblaft.reports.domain.job.ProcessingJob;

/**
 * Avisos ativos ao professor (FR-021): etapas e resultado no canal de origem e, para trabalhos da
 * Web com Telegram vinculado, um aviso curto no bot. A Web acompanha por consulta e não precisa de
 * envio.
 */
public interface TeacherNotificationPort {

    void notifyStep(ProcessingJob job);

    void notifyResult(ProcessingJob job);

    void notifyDelay(ProcessingJob job);
}
