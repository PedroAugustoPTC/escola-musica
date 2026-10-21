package br.com.felipeblaft.reports.architecture.fixture;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.port.StudentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lê aluno sem passar pelo escopo e chama a leitura com escopo direto no repositório (apenas
 * autoteste do ArchUnit).
 */
public class ScopelessStudentReaderFixture {

    private final StudentRepository students;

    public ScopelessStudentReaderFixture(StudentRepository students) {
        this.students = students;
    }

    public Optional<Student> byId(UUID id) {
        return students.searchById(id);
    }

    public List<Student> ofTeacher(UUID teacherId) {
        return students.listByTeacher(teacherId);
    }

    public Optional<Student> visible(StudentScope scope, UUID id) {
        return students.findVisible(scope, id);
    }

    public List<Student> all(StudentScope scope) {
        return students.list(scope, StudentStatusFilter.ACTIVE);
    }
}
