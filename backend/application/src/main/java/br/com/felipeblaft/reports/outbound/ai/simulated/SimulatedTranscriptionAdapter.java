package br.com.felipeblaft.reports.outbound.ai.simulated;

import br.com.felipeblaft.reports.port.AudioNotProcessableException;
import br.com.felipeblaft.reports.port.NormalizedAudio;
import br.com.felipeblaft.reports.port.TranscriptionPort;
import br.com.felipeblaft.reports.port.TranscriptionResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Transcrição simulada e determinística ({@code PROVEDORES_IA=simulado}). Áudios com menos de 2 s
 * são tratados como sem fala, para exercitar o caminho de "áudio não processável".
 */
public class SimulatedTranscriptionAdapter implements TranscriptionPort {

    public static final String PROVIDER = "simulated/transcription";
    static final double MINIMUM_WITH_SPEECH = 2.0;

    @Override
    public TranscriptionResult transcribe(NormalizedAudio audio) {
        if (audio.durationSeconds() < MINIMUM_WITH_SPEECH) {
            throw new AudioNotProcessableException(AudioNotProcessableException.Reason.NO_SPEECH);
        }
        try (InputStream input =
                SimulatedTranscriptionAdapter.class
                        .getClassLoader()
                        .getResourceAsStream("ai/simulated/transcription.txt")) {
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
            return new TranscriptionResult(
                    text, audio.durationSeconds(), BigDecimal.ZERO, PROVIDER);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
