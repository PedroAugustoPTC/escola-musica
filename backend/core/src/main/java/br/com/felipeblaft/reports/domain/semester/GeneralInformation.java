package br.com.felipeblaft.reports.domain.semester;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Seção 1 do semestral, preenchida pelo sistema (FR-044): nunca vem da IA nem pode ser alterada por
 * ela. A idade é calculada no fim do período ou hoje, o que vier antes.
 */
public record GeneralInformation(
        String name,
        int age,
        String instrument,
        String teacher,
        String periodKey,
        LocalDate periodStart,
        LocalDate periodEnd,
        int consideredLessonCount) {

    public GeneralInformation {
        Objects.requireNonNull(name);
        Objects.requireNonNull(teacher);
        Objects.requireNonNull(periodKey);
        Objects.requireNonNull(periodStart);
        Objects.requireNonNull(periodEnd);
    }

    public static GeneralInformation calculate(
            String name,
            LocalDate birthDate,
            String instrument,
            String teacher,
            SemesterPeriod period,
            int lessonCount,
            LocalDate today) {
        LocalDate reference = period.end().isBefore(today) ? period.end() : today;
        int age = Math.max(0, Period.between(birthDate, reference).getYears());
        return new GeneralInformation(
                name,
                age,
                instrument,
                teacher,
                period.key(),
                period.start(),
                period.end(),
                lessonCount);
    }
}
