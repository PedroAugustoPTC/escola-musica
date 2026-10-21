package br.com.felipeblaft.reports.domain.channel;

/** Tipos de ação v1 que o orquestrador executa no chat. */
public enum ChannelActionType {
    MESSAGE,
    EDIT,
    DOCUMENT,
    CALLBACK_REPLY,
    STEP_MESSAGE,
    /** Apaga uma mensagem do chat (a que contém o CPF digitado no cadastro; transporte puro). */
    DELETE_MESSAGE
}
