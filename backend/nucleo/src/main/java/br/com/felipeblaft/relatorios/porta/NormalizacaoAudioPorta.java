package br.com.felipeblaft.relatorios.porta;

import java.nio.file.Path;

/**
 * Normalização do áudio (contracts/portas-provedores.md §2): extrai a trilha de áudio (inclusive de
 * vídeo), converte e mede a duração. Também apaga arquivos temporários (FR-018).
 */
public interface NormalizacaoAudioPorta {

    /**
     * @throws FormatoNaoSuportadoExcecao sem trilha de áudio ou contêiner desconhecido
     * @throws AudioNaoProcessavelExcecao duração menor que 1 s (VAZIO)
     */
    AudioNormalizado normalizar(ArquivoTemporario original);

    /** Apaga o arquivo, se existir; nunca lança exceção. */
    void apagar(Path caminho);
}
