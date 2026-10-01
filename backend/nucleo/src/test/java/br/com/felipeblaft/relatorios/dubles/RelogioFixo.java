package br.com.felipeblaft.relatorios.dubles;

import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relógio controlado pelo teste, no fuso da escola. */
public class RelogioFixo implements RelogioPorta {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private Instant agora;

    public RelogioFixo(Instant agora) {
        this.agora = agora;
    }

    public RelogioFixo() {
        this(Instant.parse("2026-09-21T13:00:00Z"));
    }

    public void definir(Instant instante) {
        this.agora = instante;
    }

    public void avancar(Duration duracao) {
        this.agora = agora.plus(duracao);
    }

    @Override
    public Instant agora() {
        return agora;
    }

    @Override
    public ZoneId fusoDaEscola() {
        return FUSO;
    }
}
