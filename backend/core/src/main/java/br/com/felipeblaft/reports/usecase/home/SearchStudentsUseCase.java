package br.com.felipeblaft.reports.usecase.home;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.service.ReportEligibilityService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Busca aproximada entre os alunos associados (FR-013, R-17), igual nos dois canais: vazia → todos;
 * com correspondências → a lista; sem nenhuma → {@code NENHUM} com todos os alunos do professor.
 */
public class SearchStudentsUseCase {

    public record Result(StudentSearch.ResultType type, List<StudentSummary> students) {}

    private final StudentRepository students;
    private final OpenReportsQueryPort query;

    private final ReportEligibilityService eligibility;

    public SearchStudentsUseCase(
            StudentRepository students,
            OpenReportsQueryPort query,
            ReportEligibilityService eligibility) {
        this.eligibility = Objects.requireNonNull(eligibility);
        this.students = Objects.requireNonNull(students);
        this.query = Objects.requireNonNull(query);
    }

    public Result run(UUID teacherId, String search) {
        List<Student> all = students.listByTeacher(teacherId);
        StudentSearch.Result result = StudentSearch.search(all, search);
        StudentSummaries summaries =
                StudentSummaries.load(query, teacherId, query.listByTeacher(teacherId));
        return new Result(result.type(), summaries.summaries(result.students()));
    }

    /** Correspondência exata a um único aluno (usada pelo bot durante a conferência). */
    public Optional<StudentSummary> exactMatch(UUID teacherId, String text) {
        List<Student> all = students.listByTeacher(teacherId);
        return StudentSearch.exactMatch(all, text)
                .map(
                        student ->
                                new StudentSummary(
                                        student.id(),
                                        student.name(),
                                        student.instrument(),
                                        null,
                                        false));
    }
}
