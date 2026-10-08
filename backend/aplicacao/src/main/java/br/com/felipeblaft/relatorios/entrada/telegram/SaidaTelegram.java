package br.com.felipeblaft.relatorios.entrada.telegram;

import java.util.List;

/**
 * Ação de saída no chat, produzida pelo interpretador e executada depois de o estado da conversa
 * ter sido gravado. Textos já vêm escapados para {@code parse_mode=HTML}.
 */
public sealed interface SaidaTelegram {

    record Botao(String rotulo, String dados) {}

    record Teclado(List<List<Botao>> linhas) {

        public static Teclado de(Botao... botoes) {
            return new Teclado(java.util.Arrays.stream(botoes).map(List::of).toList());
        }

        public static final Teclado NENHUM = new Teclado(List.of());

        public boolean vazio() {
            return linhas.isEmpty();
        }
    }

    record Mensagem(long chatId, String texto, Teclado teclado) implements SaidaTelegram {}

    /** Edita uma mensagem já enviada (etapas do processamento na mesma mensagem). */
    record Edicao(long chatId, long mensagemId, String texto, Teclado teclado)
            implements SaidaTelegram {}

    /**
     * PDF para o chat. O envio direto usa os bytes em {@code conteudo}; a rota interna do canal usa
     * só a {@code referencia} (o orquestrador obtém os bytes por identificador opaco).
     */
    record Documento(
            long chatId,
            byte[] conteudo,
            String nomeArquivo,
            String legenda,
            ReferenciaDocumento referencia)
            implements SaidaTelegram {}

    /**
     * Mensagem de etapa do processamento (sequência 5.2, passo 4): enviada (ou editada no lugar, se
     * {@code editarMensagemId} vier) e registrada no trabalho para que o worker a edite a cada
     * etapa.
     */
    record MensagemEtapa(
            long chatId, Long editarMensagemId, String texto, java.util.UUID trabalhoId)
            implements SaidaTelegram {}

    /** Confirma o toque num botão (tira o indicador de carregamento do Telegram). */
    record RespostaCallback(String callbackId) implements SaidaTelegram {}
}
