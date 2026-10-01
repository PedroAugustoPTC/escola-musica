package br.com.felipeblaft.relatorios.porta;

import java.util.Optional;

/** Armazenamento dos PDFs por chave interna, nunca exposta ao cliente (R-11). */
public interface ArmazenamentoDocumentoPorta {

    void salvar(String chave, byte[] conteudo);

    Optional<byte[]> abrir(String chave);

    boolean existe(String chave);
}
