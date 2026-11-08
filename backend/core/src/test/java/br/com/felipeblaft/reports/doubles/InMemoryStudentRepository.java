package br.com.felipeblaft.reports.doubles;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.port.StudentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Alunos em memória. O escopo do professor vem das associações do dublê de associações: aluno
 * alheio, sem professor ou inexistente produzem o mesmo vazio (R-14), e a unicidade do CPF vale
 * pelo resumo.
 */
public class InMemoryStudentRepository implements StudentRepository {

    private final Map<UUID, Student> data = new LinkedHashMap<>();
    private final Map<UUID, Instant> erasedAt = new LinkedHashMap<>();
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
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Student::name))
                .toList();
    }

    @Override
    public Optional<Student> findVisible(StudentScope scope, UUID id) {
        Optional<Student> student = Optional.ofNullable(data.get(id));
        if (student.isEmpty() || scope.unrestricted()) {
            return student;
        }
        return assignments.exists(scope.teacherId(), id) ? student : Optional.empty();
    }

    @Override
    public List<Student> list(StudentScope scope, StudentStatusFilter status) {
        Collection<Student> universe =
                scope.unrestricted()
                        ? data.values()
                        : assignments.studentsOf(scope.teacherId()).stream()
                                .map(data::get)
                                .filter(Objects::nonNull)
                                .toList();
        return universe.stream()
                .filter(student -> status.matches(student.active()))
                .sorted(
                        Comparator.comparing(
                                        (Student student) ->
                                                StudentSearch.normalize(student.name()))
                                .thenComparing(Student::name))
                .toList();
    }

    @Override
    public boolean insertIfAbsent(Student student) {
        if (searchByCpfHash(student.cpfHash()).isPresent()) {
            return false;
        }
        data.put(student.id(), student);
        return true;
    }

    @Override
    public void save(Student student) {
        data.put(student.id(), student);
    }

    /**
     * Elimina o aluno: some de toda consulta e o CPF antigo fica livre (FR-024). O dublê guarda só
     * o instante, como o banco guarda {@code erased_at}.
     */
    @Override
    public boolean erase(UUID id, Instant now) {
        if (!data.containsKey(id)) {
            return false;
        }
        data.remove(id);
        erasedAt.put(id, now);
        assignments.deleteAllByStudent(id);
        return true;
    }

    public boolean wasErased(UUID id) {
        return erasedAt.containsKey(id);
    }

    public List<Student> all() {
        return new ArrayList<>(data.values());
    }
}
