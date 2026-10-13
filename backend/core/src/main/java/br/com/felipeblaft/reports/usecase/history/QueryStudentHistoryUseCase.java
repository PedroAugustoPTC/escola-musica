package br.com.felipeblaft.reports.usecase.history;

import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.semester.SemesterPeriod;
import br.com.felipeblaft.reports.port.LessonReportRepository;
import br.com.felipeblaft.reports.port.SemesterReportRepository;
import br.com.felipeblaft.reports.usecase.home.GetStudentSummaryUseCase;
import br.com.felipeblaft.reports.usecase.home.StudentSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Histórico do aluno (FR-047): relatórios de aula e semestrais aprovados do professor, do mais
 * recente ao mais antigo — aula pela data da aula, semestral pelo início do período, empate pela
 * aprovação. Igual nos dois canais.
 */
public class QueryStudentHistoryUseCase {

    /** Item do histórico; {@code dataAula} só na aula e {@code periodo} só no semestral. */
    public record HistoryItem(
            ReportType type,
            UUID reportId,
            LocalDate lessonDate,
            SemesterPeriod period,
            Instant approvedAt) {

        LocalDate reference() {
            return type == ReportType.AULA ? lessonDate : period.start();
        }
    }

    public record History(StudentSummary student, List<HistoryItem> items) {}

    static final Comparator<HistoryItem> NEWEST_FIRST =
            Comparator.comparing(HistoryItem::reference)
                    .thenComparing(HistoryItem::approvedAt)
                    .reversed();

    private final GetStudentSummaryUseCase studentSummary;
    private final LessonReportRepository lessons;
    private final SemesterReportRepository semester;

    public QueryStudentHistoryUseCase(
            GetStudentSummaryUseCase studentSummary,
            LessonReportRepository lessons,
            SemesterReportRepository semester) {
        this.studentSummary = Objects.requireNonNull(studentSummary);
        this.lessons = Objects.requireNonNull(lessons);
        this.semester = Objects.requireNonNull(semester);
    }

    public History run(UUID teacherId, UUID studentId) {
        StudentSummary student = studentSummary.run(teacherId, studentId);
        List<HistoryItem> items = new ArrayList<>();
        lessons.listApproved(teacherId, studentId)
                .forEach(
                        a ->
                                items.add(
                                        new HistoryItem(
                                                ReportType.AULA,
                                                a.reportId(),
                                                a.lessonDate(),
                                                null,
                                                a.approvedAt())));
        semester.listApproved(teacherId, studentId)
                .forEach(
                        s ->
                                items.add(
                                        new HistoryItem(
                                                ReportType.SEMESTRAL,
                                                s.id(),
                                                null,
                                                s.period(),
                                                s.approvedAt())));
        items.sort(NEWEST_FIRST);
        return new History(student, List.copyOf(items));
    }
}
