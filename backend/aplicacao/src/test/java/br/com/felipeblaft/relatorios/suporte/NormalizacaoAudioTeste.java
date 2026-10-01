package br.com.felipeblaft.relatorios.suporte;

import br.com.felipeblaft.relatorios.porta.ArquivoTemporario;
import br.com.felipeblaft.relatorios.porta.AudioNormalizado;
import br.com.felipeblaft.relatorios.porta.FormatoNaoSuportadoExcecao;
import br.com.felipeblaft.relatorios.porta.NormalizacaoAudioPorta;
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
public class NormalizacaoAudioTeste implements NormalizacaoAudioPorta {

    public static final String SILENCIO = "SILENCIO";
    public static final String INVALIDO = "INVALIDO";
    public static final byte[] AUDIO_COM_FALA =
            "AUDIO-DE-AULA-FICTICIO".getBytes(StandardCharsets.UTF_8);

    @Override
    public AudioNormalizado normalizar(ArquivoTemporario original) {
        try {
            String inicio =
                    new String(Files.readAllBytes(original.caminho()), StandardCharsets.UTF_8);
            if (inicio.startsWith(INVALIDO)) {
                throw new FormatoNaoSuportadoExcecao();
            }
            Path normalizado =
                    original.caminho().resolveSibling("normalizado-" + UUID.randomUUID() + ".ogg");
            Files.copy(original.caminho(), normalizado);
            return new AudioNormalizado(normalizado, inicio.startsWith(SILENCIO) ? 1.5 : 30);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    @Override
    public void apagar(Path caminho) {
        try {
            Files.deleteIfExists(caminho);
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }
}
