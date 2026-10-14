package br.com.felipeblaft.reports.outbound.security;

import br.com.felipeblaft.reports.config.ApplicationProperties;
import br.com.felipeblaft.reports.port.ClockPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Relógio do sistema com o fuso da escola ({@code ESCOLA_FUSO_HORARIO}). */
@Component
public class SystemClockAdapter implements ClockPort {

    private final Clock clock = Clock.systemUTC();
    private final ZoneId timezone;

    public SystemClockAdapter(ApplicationProperties properties) {
        this.timezone = properties.school().timezone();
    }

    @Override
    public Instant now() {
        return clock.instant();
    }

    @Override
    public ZoneId schoolTimezone() {
        return timezone;
    }
}
