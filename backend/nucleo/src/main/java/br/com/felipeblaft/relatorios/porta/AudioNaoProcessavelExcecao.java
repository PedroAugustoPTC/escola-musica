package br.com.felipeblaft.relatorios.porta;

/** Áudio sem fala, vazio ou recusado pelo provedor: nada é criado (FR-017). */
public class AudioNaoProcessavelExcecao extends RuntimeException {

    public enum Motivo {
        SEM_FALA,
        FORMATO,
        VAZIO
    }

    private final Motivo motivo;

    public AudioNaoProcessavelExcecao(Motivo motivo) {
        super(motivo.name(), null, false, false);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
