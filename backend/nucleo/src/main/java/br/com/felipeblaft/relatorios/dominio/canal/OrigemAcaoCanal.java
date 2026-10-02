package br.com.felipeblaft.relatorios.dominio.canal;

/** O que originou a ação: resposta a um update, evento proativo ou compensação de falha. */
public enum OrigemAcaoCanal {
    RESPOSTA_UPDATE,
    PROATIVA,
    COMPENSACAO
}
