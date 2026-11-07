package br.com.felipeblaft.reports.port;

import java.time.Instant;
import java.util.List;

/**
 * Sinais que a apuração de alertas lê (FR-055): as chamadas de IA recentes e a fila de entrega do
 * canal. Só números e nomes de componente, nunca dado pessoal.
 */
public interface OperationalSignalQueryPort {

    /**
     * Situação de um provedor numa janela.
     *
     * @param provider nome opaco do provedor
     * @param total chamadas na janela
     * @param failures chamadas com falha na janela
     * @param trailingFailures falhas seguidas, contadas da chamada mais recente para trás
     */
    record ProviderWindow(String provider, int total, int failures, int trailingFailures) {}

    /** Fila de entrega: itens esperando e o momento do mais antigo (nulo se não há). */
    record QueueSnapshot(int pendingCount, Instant oldestPendingAt) {}

    /** Provedores com chamada desde o instante dado. */
    List<ProviderWindow> providerWindows(Instant since);

    QueueSnapshot queueSnapshot();
}
