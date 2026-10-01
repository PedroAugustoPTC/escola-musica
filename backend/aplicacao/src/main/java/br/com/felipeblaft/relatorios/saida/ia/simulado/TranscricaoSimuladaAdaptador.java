package br.com.felipeblaft.relatorios.saida.ia.simulado;

import br.com.felipeblaft.relatorios.porta.AudioNaoProcessavelExcecao;
import br.com.felipeblaft.relatorios.porta.AudioNormalizado;
import br.com.felipeblaft.relatorios.porta.ResultadoTranscricao;
import br.com.felipeblaft.relatorios.porta.TranscricaoPorta;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Transcrição simulada e determinística ({@code PROVEDORES_IA=simulado}). Áudios com menos de 2 s
 * são tratados como sem fala, para exercitar o caminho de "áudio não processável".
 */
public class TranscricaoSimuladaAdaptador implements TranscricaoPorta {

    public static final String PROVEDOR = "simulado/transcricao";
    static final double MINIMO_COM_FALA = 2.0;

    @Override
    public ResultadoTranscricao transcrever(AudioNormalizado audio) {
        if (audio.duracaoSegundos() < MINIMO_COM_FALA) {
            throw new AudioNaoProcessavelExcecao(AudioNaoProcessavelExcecao.Motivo.SEM_FALA);
        }
        try (InputStream entrada =
                TranscricaoSimuladaAdaptador.class
                        .getClassLoader()
                        .getResourceAsStream("ia/simulado/transcricao.txt")) {
            String texto = new String(entrada.readAllBytes(), StandardCharsets.UTF_8).strip();
            return new ResultadoTranscricao(
                    texto, audio.duracaoSegundos(), BigDecimal.ZERO, PROVEDOR);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }
}
