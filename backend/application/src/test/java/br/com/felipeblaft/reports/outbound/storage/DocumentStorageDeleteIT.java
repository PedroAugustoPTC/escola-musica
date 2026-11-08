package br.com.felipeblaft.reports.outbound.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code delete} do armazenamento (FR-012): remove, é idempotente e transforma falha de E/S. */
class DocumentStorageDeleteIT {

    @TempDir Path root;

    @Test
    void deleteRemovesTheFile() {
        FileSystemDocumentStorageAdapter storage = new FileSystemDocumentStorageAdapter(root);
        storage.save("report/official.pdf", new byte[] {1, 2, 3});
        assertThat(storage.exists("report/official.pdf")).isTrue();

        storage.delete("report/official.pdf");

        assertThat(storage.exists("report/official.pdf")).isFalse();
    }

    @Test
    void missingKeyIsNotAnError() {
        FileSystemDocumentStorageAdapter storage = new FileSystemDocumentStorageAdapter(root);

        assertThatCode(() -> storage.delete("report/absent.pdf")).doesNotThrowAnyException();
    }

    @Test
    void invalidKeyIsRejected() {
        FileSystemDocumentStorageAdapter storage = new FileSystemDocumentStorageAdapter(root);

        assertThatThrownBy(() -> storage.delete("../outside.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ioFailureBecomesAnException() throws Exception {
        FileSystemDocumentStorageAdapter storage = new FileSystemDocumentStorageAdapter(root);
        // um diretório não vazio no lugar do arquivo: a remoção falha com erro de E/S
        Path directory = root.resolve("report/blocked.pdf");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("inside"), "x");

        assertThatThrownBy(() -> storage.delete("report/blocked.pdf"))
                .isInstanceOf(UncheckedIOException.class);
    }
}
