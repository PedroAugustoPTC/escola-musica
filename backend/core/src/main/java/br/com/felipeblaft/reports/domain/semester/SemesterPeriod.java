package br.com.felipeblaft.reports.domain.semester;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semestre letivo (data-model.md §9): chave {@code AAAA-S}; o 1º vai de 01/01 a 30/06 e o 2º de
 * 01/07 a 31/12.
 */
public record SemesterPeriod(int year, int semester) implements Comparable<SemesterPeriod> {

    private static final Pattern KEY = Pattern.compile("^(\\d{4})-([12])$");

    public SemesterPeriod {
        if (semester != 1 && semester != 2) {
            throw new IllegalArgumentException("semestre deve ser 1 ou 2");
        }
    }

    /** Período a partir da chave; chave inválida é {@code DADOS_INVALIDOS}. */
    public static SemesterPeriod of(String key) {
        Matcher parts = key == null ? null : KEY.matcher(key);
        if (parts == null || !parts.matches()) {
            throw new DomainException(ErrorCode.INVALID_DATA);
        }
        return new SemesterPeriod(
                Integer.parseInt(parts.group(1)), Integer.parseInt(parts.group(2)));
    }

    public static SemesterPeriod containing(LocalDate data) {
        return new SemesterPeriod(data.getYear(), data.getMonthValue() <= 6 ? 1 : 2);
    }

    /**
     * Semestres disponíveis (FR-041): do semestre de {@code desde} até o semestre de {@code hoje},
     * do mais recente ao mais antigo.
     */
    public static List<SemesterPeriod> available(LocalDate since, LocalDate today) {
        SemesterPeriod current = containing(today);
        SemesterPeriod first = containing(since.isAfter(today) ? today : since);
        List<SemesterPeriod> periods = new ArrayList<>();
        for (SemesterPeriod p = current; p.compareTo(first) >= 0; p = p.previous()) {
            periods.add(p);
        }
        return periods;
    }

    public String key() {
        return year + "-" + semester;
    }

    public LocalDate start() {
        return semester == 1 ? LocalDate.of(year, 1, 1) : LocalDate.of(year, 7, 1);
    }

    public LocalDate end() {
        return semester == 1 ? LocalDate.of(year, 6, 30) : LocalDate.of(year, 12, 31);
    }

    public boolean contains(LocalDate data) {
        return !data.isBefore(start()) && !data.isAfter(end());
    }

    /** O semestre ainda não terminou na data de hoje. */
    public boolean inProgress(LocalDate today) {
        return contains(today);
    }

    public SemesterPeriod previous() {
        return semester == 2 ? new SemesterPeriod(year, 1) : new SemesterPeriod(year - 1, 2);
    }

    @Override
    public int compareTo(SemesterPeriod other) {
        return year != other.year
                ? Integer.compare(year, other.year)
                : Integer.compare(semester, other.semester);
    }
}
