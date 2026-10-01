package br.com.felipeblaft.relatorios.porta;

/** Arquivo sem trilha de áudio ou em contêiner desconhecido. */
public class FormatoNaoSuportadoExcecao extends RuntimeException {

    public FormatoNaoSuportadoExcecao() {
        super("FORMATO_NAO_SUPORTADO", null, false, false);
    }
}
