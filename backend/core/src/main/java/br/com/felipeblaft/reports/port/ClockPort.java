package br.com.felipeblaft.reports.port;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Tempo corrente e fuso da escola (FR-016: a data da aula é a de hoje no fuso da escola). */
public interface ClockPort {

    Instant now();

    ZoneId schoolTimezone();

    default LocalDate todayInSchoolTimezone() {
        return dateInSchoolTimezone(now());
    }

    default LocalDate dateInSchoolTimezone(Instant instant) {
        return instant.atZone(schoolTimezone()).toLocalDate();
    }
}
