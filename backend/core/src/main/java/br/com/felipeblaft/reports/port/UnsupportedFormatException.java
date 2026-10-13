package br.com.felipeblaft.reports.port;

/** Arquivo sem trilha de áudio ou em contêiner desconhecido. */
public class UnsupportedFormatException extends RuntimeException {

    public UnsupportedFormatException() {
        super("FORMATO_NAO_SUPORTADO", null, false, false);
    }
}
