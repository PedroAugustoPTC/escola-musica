package br.com.felipeblaft.reports.usecase.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.Channel;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentSearch.ResultType;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.domain.report.ReportStatus;
import br.com.felipeblaft.reports.doubles.AccessScenario;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ObterInicioProfessorCasoDeUso, BuscarAlunosCasoDeUso e ObterResumoAlunoCasoDeUso. */
class HomeUseCasesTest {

    private final AccessScenario c = new AccessScenario();
    private final GetTeacherHomeUseCase start = new GetTeacherHomeUseCase(c.students, c.open);
    private final SearchStudentsUseCase search = new SearchStudentsUseCase(c.students, c.open);
    private final GetStudentSummaryUseCase summary =
            new GetStudentSummaryUseCase(c.students, c.open, c.authorization);
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");
    private final Teacher bruno = c.teacher("Bruno Lima", "bruno@example.test", "BRUN2XPA");

    private OpenReport openReport(Student student) {
        return new OpenReport(
                ReportType.LESSON,
                UUID.randomUUID(),
                null,
                student.id(),
                student.name(),
                ReportStatus.AWAITING_REVIEW,
                null,
                null,
                Channel.TELEGRAM,
                c.clock.now());
    }

    @Test
    void homeWithGreetingTotalUpToFiveStudentsAndOpenReports() {
        Student matteo = c.student(clara, "Matteo Mafra", "Piano");
        for (String name : new String[] {"Ana", "Beatriz", "Caio", "Davi", "Olavo"}) {
            c.student(clara, name, "Violão");
        }
        c.student(bruno, "Helena", "Flauta");
        c.open.add(clara.id(), openReport(matteo));
        c.open.defineLastLesson(clara.id(), matteo.id(), LocalDate.of(2026, 9, 21));

        GetTeacherHomeUseCase.Home result = start.run(clara);

        assertThat(result.teacher().firstName()).isEqualTo("Clara");
        assertThat(result.totalStudents()).isEqualTo(6);
        assertThat(result.withoutStudents()).isFalse();
        assertThat(result.students())
                .hasSize(5)
                .extracting(StudentSummary::name)
                .doesNotContain("Olavo");
        assertThat(result.openReports()).hasSize(1);
        StudentSummary first = search.run(clara.id(), "matteo").students().getFirst();
        assertThat(first.hasOpenReport()).isTrue();
        assertThat(first.lastLessonAt()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(first.instrument()).isEqualTo("Piano");
    }

    @Test
    void withoutStudents() {
        GetTeacherHomeUseCase.Home result = start.run(clara);
        assertThat(result.withoutStudents()).isTrue();
        assertThat(result.students()).isEmpty();
    }

    @Test
    void searchAllFoundAndNone() {
        c.student(clara, "Matteo Mafra", "Piano");
        c.student(clara, "Olavo Torrente", "Violão");
        c.student(bruno, "Olívia Prado", "Flauta");

        assertThat(search.run(clara.id(), "").type()).isEqualTo(ResultType.ALL);
        SearchStudentsUseCase.Result found = search.run(clara.id(), "ola");
        assertThat(found.type()).isEqualTo(ResultType.FOUND);
        assertThat(found.students())
                .extracting(StudentSummary::name)
                .containsExactly("Olavo Torrente");
        SearchStudentsUseCase.Result none = search.run(clara.id(), "Olívia");
        assertThat(none.type()).isEqualTo(ResultType.NONE);
        assertThat(none.students())
                .extracting(StudentSummary::name)
                .containsExactly("Matteo Mafra", "Olavo Torrente");
    }

    @Test
    void exactMatchOnlyAmongTeachersStudents() {
        c.student(clara, "Olavo Torrente", "Violão");
        c.student(bruno, "Helena Prado", "Flauta");
        assertThat(search.exactMatch(clara.id(), "olavo"))
                .map(StudentSummary::name)
                .contains("Olavo Torrente");
        assertThat(search.exactMatch(clara.id(), "helena")).isEmpty();
    }

    @Test
    void summaryRequiresAssignment() {
        Student matteo = c.student(clara, "Matteo Mafra", "Piano");
        Student helena = c.student(bruno, "Helena Prado", "Flauta");
        c.open.add(clara.id(), openReport(matteo));

        StudentSummary result = summary.run(clara.id(), matteo.id());
        assertThat(result.name()).isEqualTo("Matteo Mafra");
        assertThat(result.hasOpenReport()).isTrue();

        assertThatThrownBy(() -> summary.run(clara.id(), helena.id()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.STUDENT_NOT_ASSIGNED);
    }

    @Test
    void assignedStudentButRemovedFromRegistry() {
        UUID ghost = UUID.randomUUID();
        c.assignments.assign(clara.id(), ghost);
        assertThatThrownBy(() -> summary.run(clara.id(), ghost))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void openReportExposesDetailsWithoutText() {
        Student matteo = c.student(clara, "Matteo Mafra", "Piano");
        assertThat(openReport(matteo).details())
                .containsEntry("originChannel", Channel.TELEGRAM)
                .containsEntry("studentId", matteo.id())
                .containsKeys("reportId", "jobId", "status", "step", "periodKey", "updatedAt");
        assertThat(ReportStatus.AWAITING_ANSWERS.isOpen()).isTrue();
        assertThat(ReportStatus.APPROVED.isOpen()).isFalse();
        assertThat(ReportStatus.CANCELLED.isOpen()).isFalse();
    }
}
