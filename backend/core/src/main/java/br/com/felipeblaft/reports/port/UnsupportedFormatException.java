package br.com.felipeblaft.reports.port;

/** Arquivo sem trilha de áudio ou em contêiner desconhecido. */
public class UnsupportedFormatException extends RuntimeException {

    public UnsupportedFormatException() {
        super("UNSUPPORTED_FORMAT", null, false, false);
    }
}
