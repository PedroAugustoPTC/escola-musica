package br.com.felipeblaft.reports.inbound.telegram;

import java.util.List;

/**
 * Ação de saída no chat, produzida pelo interpretador e executada depois de o estado da conversa
 * ter sido gravado. Textos já vêm escapados para {@code parse_mode=HTML}.
 */
public sealed interface TelegramOutput {

    record Button(String label, String data) {}

    record Keyboard(List<List<Button>> rows) {

        public static Keyboard of(Button... buttons) {
            return new Keyboard(java.util.Arrays.stream(buttons).map(List::of).toList());
        }

        public static final Keyboard NONE = new Keyboard(List.of());

        public boolean empty() {
            return rows.isEmpty();
        }
    }

    record Message(long chatId, String text, Keyboard keyboard) implements TelegramOutput {}

    /** Edita uma mensagem já enviada (etapas do processamento na mesma mensagem). */
    record Edit(long chatId, long messageId, String text, Keyboard keyboard)
            implements TelegramOutput {}

    /**
     * PDF para o chat. O envio direto usa os bytes em {@code conteudo}; a rota interna do canal usa
     * só a {@code referencia} (o orquestrador obtém os bytes por identificador opaco) e, se o PDF
     * ficar indisponível, a {@code compensacao}: aviso curto com o botão para pedi-lo de novo
     * (FR-007c).
     */
    record Document(
            long chatId,
            byte[] content,
            String fileName,
            String caption,
            DocumentReference reference,
            Message compensation)
            implements TelegramOutput {

        public Document(
                long chatId,
                byte[] content,
                String fileName,
                String caption,
                DocumentReference reference) {
            this(chatId, content, fileName, caption, reference, null);
        }
    }

    /**
     * Mensagem de etapa do processamento (sequência 5.2, passo 4): enviada (ou editada no lugar, se
     * {@code editarMensagemId} vier) e registrada no trabalho para que o worker a edite a cada
     * etapa.
     */
    record StepMessage(long chatId, Long editMessageId, String text, java.util.UUID jobId)
            implements TelegramOutput {}

    /**
     * Apaga uma mensagem do professor (a que trouxe um CPF, FR-036). Ação só de transporte: sai
     * sempre <em>antes</em> da próxima pergunta. Carrega apenas os identificadores, nunca o
     * conteúdo.
     */
    record DeleteMessage(long chatId, long messageId) implements TelegramOutput {}

    /** Confirma o toque num botão (tira o indicador de carregamento do Telegram). */
    record CallbackReply(String callbackId) implements TelegramOutput {}
}
