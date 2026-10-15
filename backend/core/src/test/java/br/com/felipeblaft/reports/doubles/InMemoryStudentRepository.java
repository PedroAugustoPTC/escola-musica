package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.port.StudentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryStudentRepository implements StudentRepository {

    private final Map<UUID, Student> data = new LinkedHashMap<>();
    private final InMemoryAssignmentRepository assignments;

    public InMemoryStudentRepository(InMemoryAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Override
    public Optional<Student> searchById(UUID id) {
        return Optional.ofNullable(data.get(id));
    }

    @Override
    public Optional<Student> searchByCpfHash(String cpfHash) {
        return data.values().stream().filter(a -> a.cpfHash().equals(cpfHash)).findFirst();
    }

    @Override
    public List<Student> listByTeacher(UUID teacherId) {
        return assignments.studentsOf(teacherId).stream()
                .map(data::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(Student::name))
                .toList();
    }

    @Override
    public void save(Student student) {
        data.put(student.id(), student);
    }

    public List<Student> all() {
        return new ArrayList<>(data.values());
    }
}
