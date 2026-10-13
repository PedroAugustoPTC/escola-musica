package br.com.felipeblaft.reports.saida.ia.simulado;

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
public class TranscricaoSimuladaAdaptador implements TranscriptionPort {

    public static final String PROVEDOR = "simulado/transcricao";
    static final double MINIMO_COM_FALA = 2.0;

    @Override
    public TranscriptionResult transcribe(NormalizedAudio audio) {
        if (audio.durationSeconds() < MINIMO_COM_FALA) {
            throw new AudioNotProcessableException(AudioNotProcessableException.Reason.SEM_FALA);
        }
        try (InputStream entrada =
                TranscricaoSimuladaAdaptador.class
                        .getClassLoader()
                        .getResourceAsStream("ia/simulado/transcricao.txt")) {
            String texto = new String(entrada.readAllBytes(), StandardCharsets.UTF_8).strip();
            return new TranscriptionResult(
                    texto, audio.durationSeconds(), BigDecimal.ZERO, PROVEDOR);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }
}
