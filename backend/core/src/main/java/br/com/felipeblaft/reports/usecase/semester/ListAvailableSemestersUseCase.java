package br.com.felipeblaft.reports.usecase.semester;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.port.ClockPort;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.service.AuthorizationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Semestres disponíveis para o semestral (FR-041): do semestre da primeira aula do aluno com o
 * professor (ou do cadastro, o que vier antes) até o corrente, do mais recente ao mais antigo, com
 * o mais recente pré-selecionado e o corrente marcado como "em andamento".
 */
public class ListAvailableSemestersUseCase {

    public record Semester(SemesterPeriod period, boolean inProgress, boolean preSelected) {}

    private final AuthorizationService authorization;
    private final StudentRepository students;
    private final LessonReportRepository lessons;
    private final ClockPort clock;

    public ListAvailableSemestersUseCase(
            AuthorizationService authorization,
            StudentRepository students,
            LessonReportRepository lessons,
            ClockPort clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.students = Objects.requireNonNull(students);
        this.lessons = Objects.requireNonNull(lessons);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<Semester> run(UUID teacherId, UUID studentId) {
        authorization.requireAssignment(teacherId, studentId);
        Student student = students.searchById(studentId).orElseThrow();
        LocalDate today = clock.todayInSchoolTimezone();
        LocalDate registry = student.createdAt().atZone(clock.schoolTimezone()).toLocalDate();
        LocalDate since =
                lessons.lessonFirstDate(teacherId, studentId)
                        .filter(first -> first.isBefore(registry))
                        .orElse(registry);
        List<Semester> semesters = new ArrayList<>();
        List<SemesterPeriod> periods = SemesterPeriod.available(since, today);
        for (int i = 0; i < periods.size(); i++) {
            SemesterPeriod period = periods.get(i);
            semesters.add(new Semester(period, period.inProgress(today), i == 0));
        }
        return semesters;
    }
}
