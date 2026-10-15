package br.com.felipeblaft.reports.support;

import br.com.felipeblaft.reports.port.AudioNormalizationPort;
import br.com.felipeblaft.reports.port.NormalizedAudio;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.port.UnsupportedFormatException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Normalização sem FFmpeg para os testes de integração dos fluxos: o conteúdo do arquivo decide o
 * resultado. {@link #SILENCE} vira um áudio de 1,5 s (a transcrição simulada responde "sem fala"),
 * {@link #INVALID} é formato não suportado; qualquer outro conteúdo vira 30 s de áudio.
 */
public class AudioNormalizationTest implements AudioNormalizationPort {

    public static final String SILENCE = "SILENCE";
    public static final String INVALID = "INVALID";
    public static final byte[] AUDIO_WITH_SPEECH =
            "FICTITIOUS-LESSON-AUDIO".getBytes(StandardCharsets.UTF_8);

    @Override
    public NormalizedAudio normalize(TemporaryFile original) {
        try {
            String start = new String(Files.readAllBytes(original.path()), StandardCharsets.UTF_8);
            if (start.startsWith(INVALID)) {
                throw new UnsupportedFormatException();
            }
            Path normalized =
                    original.path().resolveSibling("normalized-" + UUID.randomUUID() + ".ogg");
            Files.copy(original.path(), normalized);
            return new NormalizedAudio(normalized, start.startsWith(SILENCE) ? 1.5 : 30);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @Override
    public void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
