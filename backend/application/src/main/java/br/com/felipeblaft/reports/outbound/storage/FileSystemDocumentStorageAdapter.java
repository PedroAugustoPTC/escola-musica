package br.com.felipeblaft.reports.outbound.storage;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.port.DocumentStoragePort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * PDFs no volume {@code documentos} (R-11). A chave é interna e só aceita segmentos simples, sem
 * {@code ..}; a escrita vai para um arquivo temporário no mesmo diretório e é movida atomicamente.
 */
@Component
public class FileSystemDocumentStorageAdapter implements DocumentStoragePort {

    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9-]+(/[a-zA-Z0-9._-]+)*");

    private final Path root;

    @Autowired
    public FileSystemDocumentStorageAdapter(ApplicationProperties properties) {
        this(properties.documents().dir());
    }

    public FileSystemDocumentStorageAdapter(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void save(String key, byte[] content) {
        Path destination = path(key);
        Path temporary = destination.resolveSibling(".tmp-" + UUID.randomUUID());
        try {
            Files.createDirectories(destination.getParent());
            Files.write(temporary, content);
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            deleteSilently(temporary);
            throw new UncheckedIOException(error);
        }
    }

    @Override
    public Optional<byte[]> open(String key) {
        try {
            return Optional.of(Files.readAllBytes(path(key)));
        } catch (NoSuchFileException error) {
            return Optional.empty();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(path(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    Path path(String key) {
        if (key == null || !VALID_KEY.matcher(key).matches() || key.contains("..")) {
            throw new IllegalArgumentException("chave de documento inválida");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("chave de documento inválida");
        }
        return path;
    }

    private static void deleteSilently(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // sobra temporária não exposta; não há o que fazer além de seguir
        }
    }
}
