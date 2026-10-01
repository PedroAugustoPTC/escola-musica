package br.com.felipeblaft.relatorios.porta;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Arquivo de áudio no diretório temporário ({@code tmpfs}); apagado após a transcrição (FR-018).
 */
public record ArquivoTemporario(Path caminho, long tamanho, String mime) implements EntradaAudio {

    public ArquivoTemporario {
        Objects.requireNonNull(caminho);
    }
}
