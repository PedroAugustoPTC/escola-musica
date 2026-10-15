package br.com.felipeblaft.reports.support;

import br.com.felipeblaft.reports.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relógio fixo e ajustável para os testes de integração. */
public class AdjustableClock implements ClockPort {

    public static final Instant DEFAULT = Instant.parse("2026-09-21T13:00:00Z");
    public static final ZoneId TIMEZONE = ZoneId.of("America/Sao_Paulo");

    private volatile Instant now = DEFAULT;

    public void define(Instant instant) {
        this.now = instant;
    }

    public void advance(Duration duration) {
        this.now = now.plus(duration);
    }

    @Override
    public Instant now() {
        return now;
    }

    @Override
    public ZoneId schoolTimezone() {
        return TIMEZONE;
    }
}
