package br.com.felipeblaft.relatorios.dominio.canal;

/** Tipos de ação v1 que o orquestrador executa no chat. */
public enum TipoAcaoCanal {
    MENSAGEM,
    EDICAO,
    DOCUMENTO,
    RESPOSTA_CALLBACK,
    MENSAGEM_ETAPA
}
