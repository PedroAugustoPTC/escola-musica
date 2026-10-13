package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.port.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relógio controlado pelo teste, no fuso da escola. */
public class FixedClock implements ClockPort {

    public static final ZoneId TIMEZONE = ZoneId.of("America/Sao_Paulo");

    private Instant now;

    public FixedClock(Instant now) {
        this.now = now;
    }

    public FixedClock() {
        this(Instant.parse("2026-09-21T13:00:00Z"));
    }

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
