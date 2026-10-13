package br.com.felipeblaft.reports.domain.telegram;

/** Fluxo em andamento na conversa do Telegram (data-model.md §5). */
public enum ConversationFlow {
    NENHUM,
    REGISTRO_AULA,
    SEMESTRAL,
    HISTORICO
}
