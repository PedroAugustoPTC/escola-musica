package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Consulta programável: o teste define os relatórios em aberto e as últimas aulas. */
public class FakeOpenReportsQuery implements OpenReportsQueryPort {

    private final Map<UUID, List<OpenReport>> byTeacher = new HashMap<>();
    private final Map<UUID, Map<UUID, LocalDate>> lastLessons = new HashMap<>();

    public void add(UUID teacherId, OpenReport report) {
        byTeacher.computeIfAbsent(teacherId, k -> new ArrayList<>()).add(report);
    }

    public void defineLastLesson(UUID teacherId, UUID studentId, LocalDate data) {
        lastLessons.computeIfAbsent(teacherId, k -> new HashMap<>()).put(studentId, data);
    }

    @Override
    public List<OpenReport> listByTeacher(UUID teacherId) {
        return List.copyOf(byTeacher.getOrDefault(teacherId, List.of()));
    }

    @Override
    public Optional<OpenReport> searchByStudent(UUID teacherId, UUID studentId) {
        return listByTeacher(teacherId).stream()
                .filter(r -> r.studentId().equals(studentId))
                .filter(r -> r.type() == br.com.felipeblaft.reports.domain.common.ReportType.LESSON)
                .findFirst();
    }

    @Override
    public Map<UUID, LocalDate> lastLessonByStudent(UUID teacherId) {
        return Map.copyOf(lastLessons.getOrDefault(teacherId, Map.of()));
    }
}
