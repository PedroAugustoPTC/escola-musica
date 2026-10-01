package br.com.felipeblaft.relatorios.porta;

/**
 * Obtém o arquivo guardado no canal e o copia para o diretório temporário, removendo a cópia do
 * canal (R-05, FR-018).
 */
public interface ArquivoCanalPorta {

    ArquivoTemporario obterArquivo(ReferenciaArquivoCanal referencia);
}
