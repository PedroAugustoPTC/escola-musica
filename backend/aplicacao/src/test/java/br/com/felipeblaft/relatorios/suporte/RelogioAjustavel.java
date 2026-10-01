package br.com.felipeblaft.relatorios.suporte;

import br.com.felipeblaft.relatorios.porta.RelogioPorta;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relógio fixo e ajustável para os testes de integração. */
public class RelogioAjustavel implements RelogioPorta {

    public static final Instant PADRAO = Instant.parse("2026-09-21T13:00:00Z");
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private volatile Instant agora = PADRAO;

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
