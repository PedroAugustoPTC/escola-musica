package br.com.felipeblaft.reports.domain.channel;

/** O que originou a ação: resposta a um update, evento proativo ou compensação de falha. */
public enum ChannelActionOrigin {
    UPDATE_REPLY,
    PROACTIVE,
    COMPENSATION
}
