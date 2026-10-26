package br.com.felipeblaft.reports.domain.telegram;

/** Fluxo em andamento na conversa do Telegram (data-model.md §5). */
public enum ConversationFlow {
    NONE,
    LESSON_REGISTRATION,
    SEMESTER,
    HISTORY,
    /** "Meus alunos": lista, cartão e cadastro guiado (feature 005). */
    STUDENT_REGISTRY
}
