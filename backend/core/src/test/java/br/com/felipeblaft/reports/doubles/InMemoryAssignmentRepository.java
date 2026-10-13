package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InMemoryAssignmentRepository implements AssignmentRepository {

    private final List<TeacherStudent> data = new ArrayList<>();

    @Override
    public boolean exists(UUID teacherId, UUID studentId) {
        return data.stream()
                .anyMatch(a -> a.teacherId().equals(teacherId) && a.studentId().equals(studentId));
    }

    @Override
    public void save(TeacherStudent assignment) {
        data.add(assignment);
    }

    public void assign(UUID teacherId, UUID studentId) {
        data.add(TeacherStudent.create(teacherId, studentId, java.time.Instant.EPOCH));
    }

    public List<UUID> studentsOf(UUID teacherId) {
        return data.stream()
                .filter(a -> a.teacherId().equals(teacherId))
                .map(TeacherStudent::studentId)
                .toList();
    }

    public List<TeacherStudent> all() {
        return List.copyOf(data);
    }
}
