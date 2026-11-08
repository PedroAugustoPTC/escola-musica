package br.com.felipeblaft.reports.domain.privacy;

import java.util.Map;

/**
 * Alcance de uma eliminação, só em números (FR-021). Nunca carrega nome, CPF nem texto.
 *
 * @param lessonReports quantidade de relatórios de aula por situação
 * @param semesterReports quantidade de relatórios semestrais por situação
 */
public record ErasureScope(
        Map<String, Integer> lessonReports,
        Map<String, Integer> semesterReports,
        int pdfs,
        int assignments,
        int consents) {

    public ErasureScope {
        lessonReports = lessonReports == null ? Map.of() : Map.copyOf(lessonReports);
        semesterReports = semesterReports == null ? Map.of() : Map.copyOf(semesterReports);
    }

    public int totalLessonReports() {
        return lessonReports.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int totalSemesterReports() {
        return semesterReports.values().stream().mapToInt(Integer::intValue).sum();
    }
}
