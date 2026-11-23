package br.com.felipeblaft.reports.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AudioSignatureTest {

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void acceptsKnownContainers() {
        assertThat(AudioSignature.isAudio(ascii("ID3\u0004\u0000"))).isTrue();
        assertThat(AudioSignature.isAudio(bytes(0xFF, 0xFB, 0x90, 0x00))).isTrue();
        assertThat(AudioSignature.isAudio(bytes(0xFF, 0xF1, 0x50, 0x80))).isTrue();
        assertThat(AudioSignature.isAudio(ascii("\u0000\u0000\u0000 ftypM4A "))).isTrue();
        assertThat(AudioSignature.isAudio(ascii("OggS\u0000\u0002"))).isTrue();
        assertThat(AudioSignature.isAudio(ascii("RIFF$\u0000\u0000\u0000WAVE"))).isTrue();
        assertThat(AudioSignature.isAudio(bytes(0x1A, 0x45, 0xDF, 0xA3, 0x01))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<!doctype html><script>",
                "RIFF$\u0000\u0000\u0000AVI ",
                "%PDF-1.7",
                "Og",
                "",
                "\u001AEß"
            })
    void rejectsOtherContent(String header) {
        assertThat(AudioSignature.isAudio(ascii(header))).isFalse();
    }

    @Test
    void rejectsSingleFfByte() {
        assertThat(AudioSignature.isAudio(bytes(0xFF))).isFalse();
        assertThat(AudioSignature.isAudio(bytes(0xFF, 0x10))).isFalse();
        assertThat(AudioSignature.isAudio(bytes(0x1A, 0x45, 0xDF, 0x00))).isFalse();
    }

    @Test
    void readsHeaderFromFile(@TempDir Path directory) throws Exception {
        Path audio = Files.write(directory.resolve("a.ogg"), ascii("OggS-rest"));
        Path html = Files.write(directory.resolve("b.mp3"), ascii("<html>"));

        assertThat(AudioSignature.isAudio(audio)).isTrue();
        assertThat(AudioSignature.isAudio(html)).isFalse();
        assertThatThrownBy(() -> AudioSignature.isAudio(directory.resolve("missing")))
                .isInstanceOf(UncheckedIOException.class);
    }
}
