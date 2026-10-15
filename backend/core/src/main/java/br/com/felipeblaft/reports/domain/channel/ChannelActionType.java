package br.com.felipeblaft.reports.domain.channel;

/** Tipos de ação v1 que o orquestrador executa no chat. */
public enum ChannelActionType {
    MESSAGE,
    EDIT,
    DOCUMENT,
    CALLBACK_REPLY,
    STEP_MESSAGE
}
