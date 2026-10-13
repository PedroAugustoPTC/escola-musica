package br.com.felipeblaft.reports.port;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Arquivo de áudio no diretório temporário ({@code tmpfs}); apagado após a transcrição (FR-018).
 */
public record TemporaryFile(Path path, long size, String mime) implements AudioInput {

    public TemporaryFile {
        Objects.requireNonNull(path);
    }
}
