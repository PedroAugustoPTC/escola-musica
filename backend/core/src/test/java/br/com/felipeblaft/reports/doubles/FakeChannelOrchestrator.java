package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.port.ChannelOrchestratorPort;
import br.com.felipeblaft.reports.port.DeliveryBatch;
import br.com.felipeblaft.reports.port.DeliveryResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Orquestrador programável: aceita por padrão, recusa conforme a fila de respostas e registra os
 * lotes.
 */
public class FakeChannelOrchestrator implements ChannelOrchestratorPort {

    public final List<DeliveryBatch> received = new ArrayList<>();
    private final Deque<DeliveryResult> scheduledResults = new ArrayDeque<>();

    /**
     * Executado durante a entrega, antes da resposta: simula a confirmação chegando no meio do
     * HTTP.
     */
    public Runnable duringDelivery = () -> {};

    public void refuseNext(String code, int times) {
        for (int i = 0; i < times; i++) {
            scheduledResults.add(DeliveryResult.refused(code));
        }
    }

    @Override
    public DeliveryResult deliver(DeliveryBatch batch) {
        received.add(batch);
        duringDelivery.run();
        DeliveryResult scheduled = scheduledResults.poll();
        return scheduled == null ? DeliveryResult.accept() : scheduled;
    }
}
