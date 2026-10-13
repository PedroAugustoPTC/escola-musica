package br.com.felipeblaft.reports.usecase.home;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Monta os resumos de alunos com última aula e marca "Em aberto" (FR-035). */
record StudentSummaries(Map<UUID, LocalDate> lastLessons, Set<UUID> studentsWithOpenReports) {

    static StudentSummaries load(
            OpenReportsQueryPort query, UUID teacherId, List<OpenReport> open) {
        return new StudentSummaries(
                query.lastLessonByStudent(teacherId),
                open.stream().map(OpenReport::studentId).collect(Collectors.toSet()));
    }

    StudentSummary summary(Student student) {
        return StudentSummary.of(
                student,
                lastLessons.get(student.id()),
                studentsWithOpenReports.contains(student.id()));
    }

    List<StudentSummary> summaries(List<Student> students) {
        return students.stream().map(this::summary).toList();
    }
}
