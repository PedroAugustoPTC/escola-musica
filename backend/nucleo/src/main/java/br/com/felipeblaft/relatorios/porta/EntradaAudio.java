package br.com.felipeblaft.relatorios.porta;

/**
 * Áudio enviado pelo professor: arquivo já recebido no diretório temporário (Web) ou referência ao
 * arquivo guardado no canal, baixado só pelo processamento (Telegram, R-05).
 */
public sealed interface EntradaAudio permits ArquivoTemporario, ReferenciaArquivoCanal {

    /** Tamanho declarado em bytes (0 se desconhecido). */
    long tamanho();
}
