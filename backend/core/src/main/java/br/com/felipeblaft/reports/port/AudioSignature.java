package br.com.felipeblaft.reports.port;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Confere pela assinatura (primeiros bytes) que o arquivo é de um contêiner de áudio aceito, sem
 * confiar no tipo declarado: MP3 (ID3 ou quadro MPEG), AAC (ADTS), MP4/M4A ({@code ftyp}), Ogg,
 * WAV (RIFF/WAVE) e WebM (EBML). Não decodifica o áudio; isso fica com o FFmpeg.
 */
public final class AudioSignature {

    private static final int HEADER = 12;

    private AudioSignature() {}

    public static boolean isAudio(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            return isAudio(input.readNBytes(HEADER));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    static boolean isAudio(byte[] h) {
        return starts(h, 0, "ID3")
                || (h.length >= 2 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0)
                || starts(h, 4, "ftyp")
                || starts(h, 0, "OggS")
                || (starts(h, 0, "RIFF") && starts(h, 8, "WAVE"))
                || (h.length >= 4
                        && (h[0] & 0xFF) == 0x1A
                        && (h[1] & 0xFF) == 0x45
                        && (h[2] & 0xFF) == 0xDF
                        && (h[3] & 0xFF) == 0xA3);
    }

    private static boolean starts(byte[] header, int offset, String ascii) {
        byte[] expected = ascii.getBytes(StandardCharsets.US_ASCII);
        if (header.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (header[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
