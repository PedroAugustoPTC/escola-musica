package br.com.felipeblaft.reports.saida.armazenamento;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
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
public class SistemaArquivosArmazenamentoDocumentoAdaptador implements DocumentStoragePort {

    private static final Pattern CHAVE_VALIDA = Pattern.compile("[a-z0-9-]+(/[a-zA-Z0-9._-]+)*");

    private final Path raiz;

    @Autowired
    public SistemaArquivosArmazenamentoDocumentoAdaptador(PropriedadesAplicacao propriedades) {
        this(propriedades.documentos().dir());
    }

    public SistemaArquivosArmazenamentoDocumentoAdaptador(Path raiz) {
        this.raiz = raiz.toAbsolutePath().normalize();
    }

    @Override
    public void save(String chave, byte[] conteudo) {
        Path destino = caminho(chave);
        Path temporario = destino.resolveSibling(".tmp-" + UUID.randomUUID());
        try {
            Files.createDirectories(destino.getParent());
            Files.write(temporario, conteudo);
            try {
                Files.move(
                        temporario,
                        destino,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException erro) {
                Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException erro) {
            apagarSilenciosamente(temporario);
            throw new UncheckedIOException(erro);
        }
    }

    @Override
    public Optional<byte[]> open(String chave) {
        try {
            return Optional.of(Files.readAllBytes(caminho(chave)));
        } catch (NoSuchFileException erro) {
            return Optional.empty();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }

    @Override
    public boolean exists(String chave) {
        return Files.isRegularFile(caminho(chave));
    }

    Path caminho(String chave) {
        if (chave == null || !CHAVE_VALIDA.matcher(chave).matches() || chave.contains("..")) {
            throw new IllegalArgumentException("chave de documento inválida");
        }
        Path caminho = raiz.resolve(chave).normalize();
        if (!caminho.startsWith(raiz)) {
            throw new IllegalArgumentException("chave de documento inválida");
        }
        return caminho;
    }

    private static void apagarSilenciosamente(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException ignorada) {
            // sobra temporária não exposta; não há o que fazer além de seguir
        }
    }
}
