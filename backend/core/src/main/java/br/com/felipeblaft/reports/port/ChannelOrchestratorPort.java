package br.com.felipeblaft.reports.port;

/**
 * Entrega de lotes de ações proativas ao orquestrador do canal. Qualquer falha de transporte vira
 * {@link DeliveryResult#refused(String)}: a decisão de reenviar é do núcleo, nunca desta porta.
 */
public interface ChannelOrchestratorPort {

    DeliveryResult deliver(DeliveryBatch batch);
}
