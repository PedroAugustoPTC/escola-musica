package br.com.felipeblaft.reports.port;

import java.util.Optional;

/** Armazenamento dos PDFs por chave interna, nunca exposta ao cliente (R-11). */
public interface DocumentStoragePort {

    void save(String key, byte[] content);

    Optional<byte[]> open(String key);

    boolean exists(String key);
}
