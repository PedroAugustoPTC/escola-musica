package br.com.felipeblaft.reports.inbound.web;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.port.TemporaryFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Recebe o áudio da Web (FR-014): confere o limite de tamanho (413 {@code AUDIO_MUITO_GRANDE}) e a
 * lista de tipos aceitos (415 {@code FORMATO_NAO_SUPORTADO}) e grava o arquivo em streaming no
 * diretório temporário, de onde o processamento o apaga (FR-018).
 */
@Component
public class WebAudioReception {

    static final Set<String> ACCEPTED_TYPES =
            Set.of(
                    "audio/mpeg",
                    "audio/mp3",
                    "audio/mp4",
                    "audio/m4a",
                    "audio/x-m4a",
                    "audio/aac",
                    "audio/ogg",
                    "audio/opus",
                    "audio/wav",
                    "audio/x-wav",
                    "audio/wave",
                    "audio/vnd.wave",
                    "audio/webm",
                    "video/mp4",
                    "video/webm");

    static final Map<String, String> EXTENSIONS =
            Map.of(
                    ".mp3", "audio/mpeg",
                    ".m4a", "audio/mp4",
                    ".ogg", "audio/ogg",
                    ".oga", "audio/ogg",
                    ".opus", "audio/ogg",
                    ".wav", "audio/wav",
                    ".webm", "audio/webm",
                    ".mp4", "video/mp4");

    private final Path directory;
    private final long limitBytes;
    private final int limitMb;

    public WebAudioReception(ApplicationProperties properties) {
        this.directory = properties.audio().tempDir();
        this.limitBytes = properties.audio().limitBytes();
        this.limitMb = properties.audio().limitMb();
    }

    public TemporaryFile receive(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainException(ErrorCode.DADOS_INVALIDOS);
        }
        if (file.getSize() > limitBytes) {
            throw new DomainException(ErrorCode.AUDIO_MUITO_GRANDE, Map.of("limiteMb", limitMb));
        }
        String mime = type(file.getContentType(), file.getOriginalFilename());
        Path destination = directory.resolve("web-" + UUID.randomUUID() + extension(mime));
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return new TemporaryFile(destination, Files.size(destination), mime);
        } catch (IOException error) {
            delete(destination);
            throw new UncheckedIOException(error);
        }
    }

    /** Tipo declarado (sem parâmetros como {@code codecs}); sem tipo útil, vale a extensão. */
    static String type(String declared, String originalName) {
        String type =
                declared == null ? "" : declared.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        if (ACCEPTED_TYPES.contains(type)) {
            return type;
        }
        if (type.isEmpty() || type.equals("application/octet-stream")) {
            String name = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
            int point = name.lastIndexOf('.');
            String byExtension = point >= 0 ? EXTENSIONS.get(name.substring(point)) : null;
            if (byExtension != null) {
                return byExtension;
            }
        }
        throw new DomainException(ErrorCode.FORMATO_NAO_SUPORTADO);
    }

    private static String extension(String mime) {
        return EXTENSIONS.entrySet().stream()
                .filter(e -> e.getValue().equals(mime))
                .map(Map.Entry::getKey)
                .sorted()
                .findFirst()
                .orElse(".bin");
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // a varredura periódica remove a sobra
        }
    }
}
