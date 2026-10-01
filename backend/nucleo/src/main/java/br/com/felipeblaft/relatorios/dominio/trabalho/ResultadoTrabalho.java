package br.com.felipeblaft.relatorios.dominio.trabalho;

/** Código estável do resultado de um trabalho, entregue aos dois canais (FR-021). */
public enum ResultadoTrabalho {
    PRONTO_PARA_CONFERENCIA(true),
    PERGUNTAS_PENDENTES(true),
    NOVA_VERSAO(true),
    SEM_ALTERACAO_DE_CONTEUDO(true),
    PEDIDO_DE_ACAO_IGNORADO(true),
    VERSAO_DESATUALIZADA(true),
    AUDIO_NAO_PROCESSAVEL(false),
    FALHA_ESTRUTURACAO(false),
    FALHA_ALTERACAO(false),
    FALHA_RESPOSTA(false),
    FALHA_CONSOLIDACAO(false),
    DESCARTADO(false);

    private final boolean sucesso;

    ResultadoTrabalho(boolean sucesso) {
        this.sucesso = sucesso;
    }

    /** O trabalho terminou normalmente (mesmo sem nova versão). */
    public boolean sucesso() {
        return sucesso;
    }
}
