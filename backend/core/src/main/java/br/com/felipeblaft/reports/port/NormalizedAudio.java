package br.com.felipeblaft.reports.port;

import java.nio.file.Path;
import java.util.Objects;

/** Áudio normalizado (Ogg/Opus mono 16 kHz) pronto para a transcrição, com a duração medida. */
public record NormalizedAudio(Path path, double durationSeconds) {

    public static final String LANGUAGE = "pt";

    public NormalizedAudio {
        Objects.requireNonNull(path);
    }
}
