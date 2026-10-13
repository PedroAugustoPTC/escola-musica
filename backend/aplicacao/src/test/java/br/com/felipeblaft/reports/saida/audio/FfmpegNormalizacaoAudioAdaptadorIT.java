package br.com.felipeblaft.reports.saida.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import br.com.felipeblaft.reports.port.AudioNotProcessableException;
import br.com.felipeblaft.reports.port.NormalizedAudio;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.port.UnsupportedFormatException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Normalização com o FFmpeg de verdade (R-07). As amostras são geradas pelo próprio FFmpeg no
 * diretório temporário do teste (tom sintético, sem voz de ninguém). Sem FFmpeg instalado o teste é
 * ignorado; no contêiner {@code testes-backend} ele roda.
 */
class FfmpegNormalizacaoAudioAdaptadorIT {

    private static boolean ffmpegDisponivel;

    @TempDir Path amostras;
    @TempDir Path saida;

    private FfmpegNormalizacaoAudioAdaptador adaptador;

    @BeforeAll
    static void verificarFfmpeg() {
        try {
            Process processo =
                    new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            processo.getInputStream().readAllBytes();
            ffmpegDisponivel = processo.waitFor(20, TimeUnit.SECONDS) && processo.exitValue() == 0;
        } catch (IOException | InterruptedException erro) {
            ffmpegDisponivel = false;
        }
    }

    @BeforeEach
    void preparar() {
        assumeThat(ffmpegDisponivel).as("ffmpeg instalado").isTrue();
        adaptador = new FfmpegNormalizacaoAudioAdaptador(saida);
    }

    private Path gerar(String nome, double segundos, boolean comVideo) throws Exception {
        Path arquivo = amostras.resolve(nome);
        List<String> comando =
                new ArrayList<>(List.of("ffmpeg", "-nostdin", "-y", "-loglevel", "error"));
        if (comVideo) {
            comando.addAll(List.of("-f", "lavfi", "-i", "color=c=black:s=64x64:d=" + segundos));
        }
        comando.addAll(List.of("-f", "lavfi", "-i", "sine=frequency=440:duration=" + segundos));
        if (comVideo) {
            comando.addAll(List.of("-c:v", "mpeg4", "-c:a", "aac", "-shortest"));
        }
        comando.add(arquivo.toString());
        Process processo = new ProcessBuilder(comando).redirectErrorStream(true).start();
        processo.getInputStream().readAllBytes();
        assertThat(processo.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(processo.exitValue()).isZero();
        return arquivo;
    }

    private static TemporaryFile temporario(Path arquivo, String mime) throws IOException {
        return new TemporaryFile(arquivo, Files.size(arquivo), mime);
    }

    private static String probe(Path arquivo, String entradas) throws Exception {
        Process processo =
                new ProcessBuilder(
                                "ffprobe",
                                "-v",
                                "error",
                                "-select_streams",
                                "a:0",
                                "-show_entries",
                                entradas,
                                "-of",
                                "default=noprint_wrappers=1:nokey=1",
                                arquivo.toString())
                        .redirectErrorStream(true)
                        .start();
        String texto = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        processo.waitFor(20, TimeUnit.SECONDS);
        return texto.strip();
    }

    @Test
    void oggViraOpusMono() throws Exception {
        Path original = gerar("aula.ogg", 3, false);

        NormalizedAudio normalizado = adaptador.normalize(temporario(original, "audio/ogg"));

        assertThat(normalizado.path()).startsWith(saida).hasExtension("ogg");
        assertThat(normalizado.durationSeconds()).isBetween(2.5, 3.5);
        // o Opus sempre informa 48 kHz na decodificação; o que importa é o codec e o canal único
        assertThat(probe(normalizado.path(), "stream=codec_name")).isEqualTo("opus");
        assertThat(probe(normalizado.path(), "stream=channels")).isEqualTo("1");
    }

    @Test
    void videoMp4TemSoATrilhaDeAudioExtraida() throws Exception {
        Path original = gerar("aula.mp4", 3, true);

        NormalizedAudio normalizado = adaptador.normalize(temporario(original, "video/mp4"));

        assertThat(normalizado.durationSeconds()).isBetween(2.5, 3.5);
        assertThat(probe(normalizado.path(), "stream=codec_name")).isEqualTo("opus");
    }

    @Test
    void textoRenomeadoParaOggNaoESuportadoENaoDeixaSobra() throws Exception {
        Path falso = amostras.resolve("falso.ogg");
        Files.writeString(falso, "isto não é áudio");

        assertThatThrownBy(() -> adaptador.normalize(temporario(falso, "audio/ogg")))
                .isInstanceOf(UnsupportedFormatException.class);
        try (var arquivos = Files.list(saida)) {
            assertThat(arquivos).isEmpty();
        }
    }

    @Test
    void audioComMenosDeUmSegundoEVazio() throws Exception {
        Path curto = gerar("curto.ogg", 0.4, false);

        assertThatThrownBy(() -> adaptador.normalize(temporario(curto, "audio/ogg")))
                .isInstanceOfSatisfying(
                        AudioNotProcessableException.class,
                        e ->
                                assertThat(e.reason())
                                        .isEqualTo(AudioNotProcessableException.Reason.VAZIO));
    }

    @Test
    void apagarRemoveOArquivo() throws Exception {
        Path original = gerar("aula2.ogg", 2, false);
        NormalizedAudio normalizado = adaptador.normalize(temporario(original, "audio/ogg"));

        adaptador.delete(normalizado.path());

        assertThat(normalizado.path()).doesNotExist();
    }
}
