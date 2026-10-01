package br.com.felipeblaft.relatorios.dominio.comum;

/**
 * Códigos estáveis de erro devolvidos pelo núcleo (R-18). O núcleo nunca devolve texto: cada canal
 * busca a mensagem no catálogo (contracts/mensagens.md) pela {@link #chaveMensagem()}.
 */
public enum CodigoErro {
    // Acesso
    CODIGO_INVALIDO,
    CODIGO_EXPIRADO,
    BLOQUEADO_TEMPORARIAMENTE,
    TELEGRAM_JA_VINCULADO,
    PROFESSOR_JA_VINCULADO,
    SESSAO_EXPIRADA,
    NAO_AUTENTICADO,

    // Autorização
    ALUNO_NAO_ASSOCIADO,
    RECURSO_NAO_ENCONTRADO,

    // Registro de aula e processamento
    PROCESSAMENTO_EM_ANDAMENTO,
    AUDIO_MUITO_GRANDE,
    FORMATO_NAO_SUPORTADO,
    AUDIO_NAO_PROCESSAVEL,

    // Perguntas, conferência e alteração
    RESPOSTA_VAZIA,
    ALTERACAO_VAZIA,
    VERSAO_DESATUALIZADA,

    // Aprovação, cancelamento e retomada
    RELATORIO_JA_APROVADO,
    RELATORIO_CANCELADO("RELATORIO_CANCELADO_ERRO"),
    ACAO_NAO_PERMITIDA_NA_ETAPA,
    RELATORIO_EM_ABERTO,

    // Semestral
    SEM_AULAS_APROVADAS_NO_PERIODO,
    HA_RELATORIOS_EM_ABERTO,
    SEMESTRAL_JA_APROVADO,

    // Carga inicial (contracts/carga-inicial.md)
    EMAIL_INVALIDO,
    EMAIL_DUPLICADO_NO_ARQUIVO,
    NOME_OBRIGATORIO,
    CPF_INVALIDO,
    CPF_DUPLICADO_NO_ARQUIVO,
    DATA_NASCIMENTO_INVALIDA,
    RESPONSAVEL_OBRIGATORIO,
    PROFESSOR_INEXISTENTE,

    // Genéricos
    DADOS_INVALIDOS,
    ERRO_INTERNO,
    SERVICO_INDISPONIVEL;

    private final String chaveMensagem;

    CodigoErro() {
        this.chaveMensagem = null;
    }

    CodigoErro(String chaveMensagem) {
        this.chaveMensagem = chaveMensagem;
    }

    /** Chave do texto no catálogo de mensagens; normalmente igual ao próprio código. */
    public String chaveMensagem() {
        return chaveMensagem != null ? chaveMensagem : name();
    }
}
