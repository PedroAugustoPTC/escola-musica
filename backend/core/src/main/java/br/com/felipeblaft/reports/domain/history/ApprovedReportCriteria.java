package br.com.felipeblaft.reports.domain.history;

import br.com.felipeblaft.reports.domain.common.ReportType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Critério da consulta única de relatórios aprovados (data-model.md §2.1). Todos os campos são
 * opcionais; campo ausente não filtra. {@code from} e {@code to} são inclusivos e comparados com a
 * data de referência do relatório (aula: data da aula; semestral: início do período, FR-012).
 *
 * @param studentId identificador opaco do aluno
 * @param teacherId identificador opaco do professor autor
 * @param type tipo de relatório; ausente = os dois
 */
public record ApprovedReportCriteria(
        UUID studentId, UUID teacherId, ReportType type, LocalDate from, LocalDate to) {

    /** Critério do histórico do professor: os relatórios aprovados dele para um aluno. */
    public static ApprovedReportCriteria ofTeacherStudent(UUID teacherId, UUID studentId) {
        return new ApprovedReportCriteria(studentId, teacherId, null, null, null);
    }

    /** Sem filtro algum: a escola inteira. */
    public static ApprovedReportCriteria unfiltered() {
        return new ApprovedReportCriteria(null, null, null, null, null);
    }
}
