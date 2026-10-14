package br.com.felipeblaft.reports.outbound.audio;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.outbound.telegram.TelegramMessagingAdapter;
import br.com.felipeblaft.reports.port.ChannelFilePort;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.TemporaryFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Obtém o arquivo do Telegram pelo servidor local da API de bots (R-05): no modo {@code --local},
 * {@code getFile} devolve o caminho absoluto no volume compartilhado; o arquivo é copiado para o
 * diretório temporário e o original é removido (FR-018).
 */
@Component
public class TelegramFileAdapter implements ChannelFilePort {

    private final TelegramMessagingAdapter messaging;
    private final Path temporaries;
    private final Path filesTelegram;

    public TelegramFileAdapter(
            TelegramMessagingAdapter messaging, ApplicationProperties properties) {
        this.messaging = messaging;
        this.temporaries = properties.audio().tempDir();
        this.filesTelegram = properties.telegram().filesDir();
    }

    @Override
    public TemporaryFile getFile(ChannelFileReference reference) {
        JsonNode file =
                messaging
                        .call("getFile", Map.of("file_id", reference.id()))
                        .orElseThrow(() -> new ProviderUnavailableException("telegram-getFile"));
        String path = file.path("file_path").asString("");
        Path original = Path.of(path).isAbsolute() ? Path.of(path) : filesTelegram.resolve(path);
        if (path.isBlank() || !original.normalize().startsWith(filesTelegram.normalize())) {
            throw new ProviderUnavailableException("telegram-caminho-inesperado");
        }
        Path destination =
                temporaries.resolve("telegram-" + UUID.randomUUID() + extension(original));
        try {
            Files.createDirectories(temporaries);
            Files.copy(original, destination, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(original);
            return new TemporaryFile(destination, Files.size(destination), reference.mime());
        } catch (IOException error) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {
                // a varredura periódica remove a sobra
            }
            throw new ProviderUnavailableException("telegram-arquivo-indisponivel");
        }
    }

    static String extension(Path file) {
        String name = file.getFileName().toString();
        int point = name.lastIndexOf('.');
        return point >= 0 && name.length() - point <= 6 ? name.substring(point) : "";
    }
}
