package br.com.felipeblaft.reports.saida.audio;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
import br.com.felipeblaft.reports.port.ChannelFilePort;
import br.com.felipeblaft.reports.port.ChannelFileReference;
import br.com.felipeblaft.reports.port.ProviderUnavailableException;
import br.com.felipeblaft.reports.port.TemporaryFile;
import br.com.felipeblaft.reports.saida.telegram.MensageriaTelegramAdaptador;
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
public class ArquivoTelegramAdaptador implements ChannelFilePort {

    private final MensageriaTelegramAdaptador mensageria;
    private final Path temporarios;
    private final Path arquivosTelegram;

    public ArquivoTelegramAdaptador(
            MensageriaTelegramAdaptador mensageria, PropriedadesAplicacao propriedades) {
        this.mensageria = mensageria;
        this.temporarios = propriedades.audio().tempDir();
        this.arquivosTelegram = propriedades.telegram().arquivosDir();
    }

    @Override
    public TemporaryFile getFile(ChannelFileReference referencia) {
        JsonNode arquivo =
                mensageria
                        .chamar("getFile", Map.of("file_id", referencia.id()))
                        .orElseThrow(() -> new ProviderUnavailableException("telegram-getFile"));
        String caminho = arquivo.path("file_path").asString("");
        Path original =
                Path.of(caminho).isAbsolute()
                        ? Path.of(caminho)
                        : arquivosTelegram.resolve(caminho);
        if (caminho.isBlank() || !original.normalize().startsWith(arquivosTelegram.normalize())) {
            throw new ProviderUnavailableException("telegram-caminho-inesperado");
        }
        Path destino = temporarios.resolve("telegram-" + UUID.randomUUID() + extensao(original));
        try {
            Files.createDirectories(temporarios);
            Files.copy(original, destino, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(original);
            return new TemporaryFile(destino, Files.size(destino), referencia.mime());
        } catch (IOException erro) {
            try {
                Files.deleteIfExists(destino);
            } catch (IOException ignorada) {
                // a varredura periódica remove a sobra
            }
            throw new ProviderUnavailableException("telegram-arquivo-indisponivel");
        }
    }

    static String extensao(Path arquivo) {
        String nome = arquivo.getFileName().toString();
        int ponto = nome.lastIndexOf('.');
        return ponto >= 0 && nome.length() - ponto <= 6 ? nome.substring(ponto) : "";
    }
}
