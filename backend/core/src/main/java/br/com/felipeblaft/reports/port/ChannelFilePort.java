package br.com.felipeblaft.reports.port;

/**
 * Obtém o arquivo guardado no canal e o copia para o diretório temporário, removendo a cópia do
 * canal (R-05, FR-018).
 */
public interface ChannelFilePort {

    TemporaryFile getFile(ChannelFileReference reference);
}
