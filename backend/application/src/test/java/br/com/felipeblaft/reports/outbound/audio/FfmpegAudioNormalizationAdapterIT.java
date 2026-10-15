package br.com.felipeblaft.reports.outbound.audio;

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
 * ignorado; no contêiner {@code tests-backend} ele roda.
 */
class FfmpegAudioNormalizationAdapterIT {

    private static boolean ffmpegAvailable;

    @TempDir Path samples;
    @TempDir Path output;

    private FfmpegAudioNormalizationAdapter adapter;

    @BeforeAll
    static void verifyFfmpeg() {
        try {
            Process process =
                    new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            ffmpegAvailable = process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException error) {
            ffmpegAvailable = false;
        }
    }

    @BeforeEach
    void prepare() {
        assumeThat(ffmpegAvailable).as("ffmpeg instalado").isTrue();
        adapter = new FfmpegAudioNormalizationAdapter(output);
    }

    private Path generate(String name, double seconds, boolean comVideo) throws Exception {
        Path file = samples.resolve(name);
        List<String> command =
                new ArrayList<>(List.of("ffmpeg", "-nostdin", "-y", "-loglevel", "error"));
        if (comVideo) {
            command.addAll(List.of("-f", "lavfi", "-i", "color=c=black:s=64x64:d=" + seconds));
        }
        command.addAll(List.of("-f", "lavfi", "-i", "sine=frequency=440:duration=" + seconds));
        if (comVideo) {
            command.addAll(List.of("-c:v", "mpeg4", "-c:a", "aac", "-shortest"));
        }
        command.add(file.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        return file;
    }

    private static TemporaryFile temporary(Path file, String mime) throws IOException {
        return new TemporaryFile(file, Files.size(file), mime);
    }

    private static String probe(Path file, String inputs) throws Exception {
        Process process =
                new ProcessBuilder(
                                "ffprobe",
                                "-v",
                                "error",
                                "-select_streams",
                                "a:0",
                                "-show_entries",
                                inputs,
                                "-of",
                                "default=noprint_wrappers=1:nokey=1",
                                file.toString())
                        .redirectErrorStream(true)
                        .start();
        String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor(20, TimeUnit.SECONDS);
        return text.strip();
    }

    @Test
    void oggViraOpusMono() throws Exception {
        Path original = generate("lesson.ogg", 3, false);

        NormalizedAudio normalized = adapter.normalize(temporary(original, "audio/ogg"));

        assertThat(normalized.path()).startsWith(output).hasExtension("ogg");
        assertThat(normalized.durationSeconds()).isBetween(2.5, 3.5);
        // o Opus sempre informa 48 kHz na decodificação; o que importa é o codec e o canal único
        assertThat(probe(normalized.path(), "stream=codec_name")).isEqualTo("opus");
        assertThat(probe(normalized.path(), "stream=channels")).isEqualTo("1");
    }

    @Test
    void mp4VideoOnlyHasExtractedAudioTrack() throws Exception {
        Path original = generate("lesson.mp4", 3, true);

        NormalizedAudio normalized = adapter.normalize(temporary(original, "video/mp4"));

        assertThat(normalized.durationSeconds()).isBetween(2.5, 3.5);
        assertThat(probe(normalized.path(), "stream=codec_name")).isEqualTo("opus");
    }

    @Test
    void textRenamedToOggIsNotSupportedAndLeavesNoLeftover() throws Exception {
        Path fakeFile = samples.resolve("false.ogg");
        Files.writeString(fakeFile, "isto não é áudio");

        assertThatThrownBy(() -> adapter.normalize(temporary(fakeFile, "audio/ogg")))
                .isInstanceOf(UnsupportedFormatException.class);
        try (var files = Files.list(output)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void audioWithLessThanOneSecondIsEmpty() throws Exception {
        Path shortAudio = generate("short.ogg", 0.4, false);

        assertThatThrownBy(() -> adapter.normalize(temporary(shortAudio, "audio/ogg")))
                .isInstanceOfSatisfying(
                        AudioNotProcessableException.class,
                        e ->
                                assertThat(e.reason())
                                        .isEqualTo(AudioNotProcessableException.Reason.EMPTY));
    }

    @Test
    void deleteRemovesFile() throws Exception {
        Path original = generate("lesson2.ogg", 2, false);
        NormalizedAudio normalized = adapter.normalize(temporary(original, "audio/ogg"));

        adapter.delete(normalized.path());

        assertThat(normalized.path()).doesNotExist();
    }
}
