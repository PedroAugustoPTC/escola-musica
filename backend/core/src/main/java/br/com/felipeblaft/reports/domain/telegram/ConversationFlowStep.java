package br.com.felipeblaft.reports.domain.telegram;

/** Etapa dentro do fluxo da conversa (contracts/telegram-conversa.md §4). */
public enum ConversationFlowStep {
    NENHUMA,
    /** Lista de alunos apresentada; as opções ficam nos dados do fluxo. */
    ESCOLHENDO_ALUNO,
    /** Aluno selecionado; esperando o áudio da aula. */
    AGUARDANDO_AUDIO,
    /** Áudio recebido; esperando a confirmação do aluno (FR-040). */
    CONFIRMANDO_ALUNO_DO_AUDIO,
    /** Aviso "Você tem um relatório em aberto" com continuar ou descartar. */
    AVISO_RELATORIO_EM_ABERTO,
    ESCOLHENDO_SEMESTRE,
    /** Prévia do semestral com relatórios de aula em aberto: gerar mesmo assim ou voltar. */
    CONFIRMANDO_GERACAO_SEMESTRAL,
    ESCOLHENDO_ITEM_HISTORICO
}
