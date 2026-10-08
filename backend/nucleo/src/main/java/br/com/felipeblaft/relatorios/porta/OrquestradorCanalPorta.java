package br.com.felipeblaft.relatorios.porta;

/**
 * Entrega de lotes de ações proativas ao orquestrador do canal. Qualquer falha de transporte vira
 * {@link ResultadoEntrega#recusado(String)}: a decisão de reenviar é do núcleo, nunca desta porta.
 */
public interface OrquestradorCanalPorta {

    ResultadoEntrega entregar(LoteEntrega lote);
}
