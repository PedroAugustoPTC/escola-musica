package br.com.felipeblaft.relatorios.porta;

import java.nio.file.Path;
import java.util.Objects;

/** Áudio normalizado (Ogg/Opus mono 16 kHz) pronto para a transcrição, com a duração medida. */
public record AudioNormalizado(Path caminho, double duracaoSegundos) {

    public static final String IDIOMA = "pt";

    public AudioNormalizado {
        Objects.requireNonNull(caminho);
    }
}
