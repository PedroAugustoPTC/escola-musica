package br.com.felipeblaft.reports.saida.audio;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.reports.port.AudioNormalizationPort;
import br.com.felipeblaft.reports.port.AudioNotProcessableException;
import br.com.felipeblaft.reports.port.NormalizedAudio;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.port.UnsupportedFormatException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Normalização com FFmpeg (R-07): extrai só a trilha de áudio (inclusive de vídeo MP4), converte
 * para Ogg/Opus mono 16 kHz 24 kbit/s no diretório temporário ({@code tmpfs}) e mede a duração com
 * ffprobe. Sem trilha de áudio ou contêiner desconhecido → formato não suportado; menos de 1 s →
 * áudio vazio.
 */
@Component
public class FfmpegNormalizacaoAudioAdaptador implements AudioNormalizationPort {

    private static final Logger LOG =
            LoggerFactory.getLogger(FfmpegNormalizacaoAudioAdaptador.class);
    static final double DURACAO_MINIMA = 1.0;
    static final long TIMEOUT_SEGUNDOS = 300;

    private final Path diretorio;

    @Autowired
    public FfmpegNormalizacaoAudioAdaptador(PropriedadesAplicacao propriedades) {
        this(propriedades.audio().tempDir());
    }

    public FfmpegNormalizacaoAudioAdaptador(Path diretorio) {
        this.diretorio = diretorio;
    }

    @Override
    public NormalizedAudio normalize(TemporaryFile original) {
        Path saida = diretorio.resolve("normalizado-" + UUID.randomUUID() + ".ogg");
        try {
            Files.createDirectories(diretorio);
            int codigo =
                    executar(
                            List.of(
                                    "ffmpeg",
                                    "-nostdin",
                                    "-y",
                                    "-hide_banner",
                                    "-loglevel",
                                    "error",
                                    "-i",
                                    original.path().toString(),
                                    "-vn",
                                    "-ac",
                                    "1",
                                    "-ar",
                                    "16000",
                                    "-c:a",
                                    "libopus",
                                    "-b:a",
                                    "24k",
                                    saida.toString()));
            if (codigo != 0 || !Files.exists(saida) || Files.size(saida) == 0) {
                delete(saida);
                throw new UnsupportedFormatException();
            }
            double duracao = duracao(saida);
            if (duracao < DURACAO_MINIMA) {
                delete(saida);
                throw new AudioNotProcessableException(AudioNotProcessableException.Reason.VAZIO);
            }
            return new NormalizedAudio(saida, duracao);
        } catch (IOException erro) {
            delete(saida);
            throw new UnsupportedFormatException();
        }
    }

    double duracao(Path arquivo) throws IOException {
        Process processo =
                new ProcessBuilder(
                                "ffprobe",
                                "-v",
                                "error",
                                "-show_entries",
                                "format=duration",
                                "-of",
                                "default=noprint_wrappers=1:nokey=1",
                                arquivo.toString())
                        .redirectErrorStream(true)
                        .start();
        String texto =
                new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        .strip();
        aguardar(processo);
        try {
            return Double.parseDouble(texto);
        } catch (NumberFormatException erro) {
            return 0;
        }
    }

    private static int executar(List<String> comando) throws IOException {
        Process processo =
                new ProcessBuilder(comando)
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
        return aguardar(processo);
    }

    private static int aguardar(Process processo) throws IOException {
        try {
            if (!processo.waitFor(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)) {
                processo.destroyForcibly();
                LOG.warn("ffmpeg-timeout");
                return -1;
            }
            return processo.exitValue();
        } catch (InterruptedException erro) {
            processo.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrompido", erro);
        }
    }

    @Override
    public void delete(Path caminho) {
        if (caminho == null) {
            return;
        }
        try {
            Files.deleteIfExists(caminho);
        } catch (IOException erro) {
            LOG.warn("audio-nao-apagado tipo={}", erro.getClass().getSimpleName());
        }
    }
}
