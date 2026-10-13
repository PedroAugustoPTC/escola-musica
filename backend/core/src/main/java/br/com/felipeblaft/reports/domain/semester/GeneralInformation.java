package br.com.felipeblaft.reports.domain.semester;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Seção 1 do semestral, preenchida pelo sistema (FR-044): nunca vem da IA nem pode ser alterada por
 * ela. A idade é calculada no fim do período ou hoje, o que vier antes.
 */
public record GeneralInformation(
        String nome,
        int idade,
        String instrumento,
        String professor,
        String periodoChave,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        int quantidadeAulasConsideradas) {

    public GeneralInformation {
        Objects.requireNonNull(nome);
        Objects.requireNonNull(professor);
        Objects.requireNonNull(periodoChave);
        Objects.requireNonNull(periodoInicio);
        Objects.requireNonNull(periodoFim);
    }

    public static GeneralInformation calculate(
            String nome,
            LocalDate birthDate,
            String instrumento,
            String professor,
            SemesterPeriod period,
            int lessonCount,
            LocalDate today) {
        LocalDate reference = period.end().isBefore(today) ? period.end() : today;
        int idade = Math.max(0, Period.between(birthDate, reference).getYears());
        return new GeneralInformation(
                nome,
                idade,
                instrumento,
                professor,
                period.key(),
                period.start(),
                period.end(),
                lessonCount);
    }
}
