package br.com.felipeblaft.reports.domain.channel;

/** Tipos de ação v1 que o orquestrador executa no chat. */
public enum ChannelActionType {
    MENSAGEM,
    EDICAO,
    DOCUMENTO,
    RESPOSTA_CALLBACK,
    MENSAGEM_ETAPA
}
