package br.com.felipeblaft.reports.port;

import java.nio.file.Path;

/**
 * Normalização do áudio (contracts/portas-provedores.md §2): extrai a trilha de áudio (inclusive de
 * vídeo), converte e mede a duração. Também apaga arquivos temporários (FR-018).
 */
public interface AudioNormalizationPort {

    /**
     * @throws UnsupportedFormatException sem trilha de áudio ou contêiner desconhecido
     * @throws AudioNotProcessableException duração menor que 1 s (VAZIO)
     */
    NormalizedAudio normalize(TemporaryFile original);

    /** Apaga o arquivo, se existir; nunca lança exceção. */
    void delete(Path path);
}
