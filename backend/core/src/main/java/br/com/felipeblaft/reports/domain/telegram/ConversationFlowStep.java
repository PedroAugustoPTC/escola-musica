package br.com.felipeblaft.reports.domain.telegram;

/** Etapa dentro do fluxo da conversa (contracts/telegram-conversa.md §4). */
public enum ConversationFlowStep {
    NONE,
    /** Lista de alunos apresentada; as opções ficam nos dados do fluxo. */
    CHOOSING_STUDENT,
    /** Aluno selecionado; esperando o áudio da aula. */
    AWAITING_AUDIO,
    /** Áudio recebido; esperando a confirmação do aluno (FR-040). */
    CONFIRMING_AUDIO_STUDENT,
    /** Aviso "Você tem um relatório em aberto" com continuar ou descartar. */
    OPEN_REPORT_NOTICE,
    CHOOSING_SEMESTER,
    /** Prévia do semestral com relatórios de aula em aberto: gerar mesmo assim ou voltar. */
    CONFIRMING_SEMESTER_GENERATION,
    CHOOSING_HISTORY_ITEM,

    // ---- "Meus alunos" (feature 005, telegram-student-registry.md §2; até 40 caracteres)
    /** Lista paginada: toque em aluno, página, filtro ou texto de busca. */
    REGISTRY_LIST,
    /** Cartão do aluno: Editar, Inativar/Reativar ou Voltar. */
    REGISTRY_CARD,
    REGISTRY_ASK_NAME,
    REGISTRY_ASK_BIRTH_DATE,
    REGISTRY_ASK_CPF,
    REGISTRY_ASK_GUARDIAN_NAME,
    REGISTRY_ASK_GUARDIAN_PHONE,
    REGISTRY_ASK_GUARDIAN_EMAIL,
    REGISTRY_ASK_INSTRUMENT,
    /** Escolha do campo a editar (botões). */
    REGISTRY_EDIT_FIELD,
    /** Resumo mostrado: "sim"/Confirmar ou Cancelar. */
    REGISTRY_CONFIRM
}
