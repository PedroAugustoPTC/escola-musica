package br.com.felipeblaft.reports.suporte;

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
 * resultado. {@link #SILENCIO} vira um áudio de 1,5 s (a transcrição simulada responde "sem fala"),
 * {@link #INVALIDO} é formato não suportado; qualquer outro conteúdo vira 30 s de áudio.
 */
public class NormalizacaoAudioTeste implements AudioNormalizationPort {

    public static final String SILENCIO = "SILENCIO";
    public static final String INVALIDO = "INVALIDO";
    public static final byte[] AUDIO_COM_FALA =
            "AUDIO-DE-AULA-FICTICIO".getBytes(StandardCharsets.UTF_8);

    @Override
    public NormalizedAudio normalize(TemporaryFile original) {
        try {
            String inicio = new String(Files.readAllBytes(original.path()), StandardCharsets.UTF_8);
            if (inicio.startsWith(INVALIDO)) {
                throw new UnsupportedFormatException();
            }
            Path normalizado =
                    original.path().resolveSibling("normalizado-" + UUID.randomUUID() + ".ogg");
            Files.copy(original.path(), normalizado);
            return new NormalizedAudio(normalizado, inicio.startsWith(SILENCIO) ? 1.5 : 30);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    @Override
    public void delete(Path caminho) {
        try {
            Files.deleteIfExists(caminho);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }
}
