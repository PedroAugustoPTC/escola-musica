package br.com.felipeblaft.reports.support;

import java.util.List;

/**
 * Tabela única "resposta de erro do Telegram → resultado" (contracts/canal-telegram-interno.md §4,
 * feature 004, contrato dos workflows §4.2). É a fonte compartilhada do executor simulado e do
 * workflow do n8n: {@code SimulatedActionsExecutorVerificationTest} confere o executor simulado
 * contra ela e {@code N8nFailureHandlingIT} confere o n8n real, de modo que os dois não divergem.
 */
public final class ClassificationCases {

    /**
     * @param status status HTTP da resposta de erro (nunca 2xx)
     * @param description texto de {@code description} da API de bots; {@code null} se não houver
     * @param reasonCode código de motivo esperado; {@code null} = a ação conta como executada
     */
    public record Case(int status, String description, String reasonCode) {

        public boolean executed() {
            return reasonCode == null;
        }
    }

    private ClassificationCases() {}

    public static List<Case> all() {
        return List.of(
                new Case(
                        400,
                        "Bad Request: message is not modified: specified new message content and reply markup are exactly the same as a current content and reply markup of the message",
                        null),
                new Case(400, "Bad Request: message to edit not found", "MESSAGE_NOT_EDITABLE"),
                new Case(400, "Bad Request: message can't be edited", "MESSAGE_NOT_EDITABLE"),
                new Case(400, "Bad Request: MESSAGE_ID_INVALID", "MESSAGE_NOT_EDITABLE"),
                new Case(403, "Forbidden: bot was blocked by the user", "CHAT_UNAVAILABLE"),
                new Case(400, "Bad Request: chat not found", "CHAT_UNAVAILABLE"),
                new Case(429, "Too Many Requests: retry after 5", "RATE_LIMITED"),
                new Case(500, "Internal Server Error", "TELEGRAM_ERROR"),
                new Case(502, null, "TELEGRAM_ERROR"),
                new Case(400, "Bad Request: unexpected new problem", "TELEGRAM_ERROR"));
    }
}
