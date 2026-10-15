package br.com.felipeblaft.reports.usecase.home;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.port.OpenReportsQueryPort;
import br.com.felipeblaft.reports.port.StudentRepository;
import java.util.List;
import java.util.Objects;

/**
 * Início dos dois canais (FR-012): saudação, relatórios em aberto, total de alunos associados e os
 * primeiros alunos. Sem alunos, o canal mostra {@code SEM_ALUNOS}.
 */
public class GetTeacherHomeUseCase {

    public static final int FIRST_STUDENTS = 5;

    public record Home(
            Teacher teacher,
            int totalStudents,
            List<StudentSummary> students,
            List<OpenReport> openReports) {

        public boolean withoutStudents() {
            return totalStudents == 0;
        }
    }

    private final StudentRepository students;
    private final OpenReportsQueryPort query;

    public GetTeacherHomeUseCase(StudentRepository students, OpenReportsQueryPort query) {
        this.students = Objects.requireNonNull(students);
        this.query = Objects.requireNonNull(query);
    }

    public Home run(Teacher teacher) {
        List<Student> all = students.listByTeacher(teacher.id());
        List<OpenReport> open = query.listByTeacher(teacher.id());
        StudentSummaries summaries = StudentSummaries.load(query, teacher.id(), open);
        List<StudentSummary> first =
                summaries.summaries(all.subList(0, Math.min(FIRST_STUDENTS, all.size())));
        return new Home(teacher, all.size(), first, open);
    }
}
