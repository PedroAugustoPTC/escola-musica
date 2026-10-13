package br.com.felipeblaft.reports.entrada.web;

import br.com.felipeblaft.reports.configuracao.PropriedadesAplicacao;
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
public class RecepcaoAudioWeb {

    static final Set<String> TIPOS_ACEITOS =
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

    static final Map<String, String> EXTENSOES =
            Map.of(
                    ".mp3", "audio/mpeg",
                    ".m4a", "audio/mp4",
                    ".ogg", "audio/ogg",
                    ".oga", "audio/ogg",
                    ".opus", "audio/ogg",
                    ".wav", "audio/wav",
                    ".webm", "audio/webm",
                    ".mp4", "video/mp4");

    private final Path diretorio;
    private final long limiteBytes;
    private final int limiteMb;

    public RecepcaoAudioWeb(PropriedadesAplicacao propriedades) {
        this.diretorio = propriedades.audio().tempDir();
        this.limiteBytes = propriedades.audio().limiteBytes();
        this.limiteMb = propriedades.audio().limiteMb();
    }

    public TemporaryFile receber(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new DomainException(ErrorCode.DADOS_INVALIDOS);
        }
        if (arquivo.getSize() > limiteBytes) {
            throw new DomainException(ErrorCode.AUDIO_MUITO_GRANDE, Map.of("limiteMb", limiteMb));
        }
        String mime = tipo(arquivo.getContentType(), arquivo.getOriginalFilename());
        Path destino = diretorio.resolve("web-" + UUID.randomUUID() + extensao(mime));
        try {
            Files.createDirectories(diretorio);
            try (InputStream entrada = arquivo.getInputStream()) {
                Files.copy(entrada, destino, StandardCopyOption.REPLACE_EXISTING);
            }
            return new TemporaryFile(destino, Files.size(destino), mime);
        } catch (IOException erro) {
            apagar(destino);
            throw new UncheckedIOException(erro);
        }
    }

    /** Tipo declarado (sem parâmetros como {@code codecs}); sem tipo útil, vale a extensão. */
    static String tipo(String declarado, String nomeOriginal) {
        String tipo =
                declarado == null
                        ? ""
                        : declarado.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        if (TIPOS_ACEITOS.contains(tipo)) {
            return tipo;
        }
        if (tipo.isEmpty() || tipo.equals("application/octet-stream")) {
            String nome = nomeOriginal == null ? "" : nomeOriginal.toLowerCase(Locale.ROOT);
            int ponto = nome.lastIndexOf('.');
            String porExtensao = ponto >= 0 ? EXTENSOES.get(nome.substring(ponto)) : null;
            if (porExtensao != null) {
                return porExtensao;
            }
        }
        throw new DomainException(ErrorCode.FORMATO_NAO_SUPORTADO);
    }

    private static String extensao(String mime) {
        return EXTENSOES.entrySet().stream()
                .filter(e -> e.getValue().equals(mime))
                .map(Map.Entry::getKey)
                .sorted()
                .findFirst()
                .orElse(".bin");
    }

    private static void apagar(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException ignorada) {
            // a varredura periódica remove a sobra
        }
    }
}
