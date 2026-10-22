package br.com.felipeblaft.reports.usecase.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.StudentSearch.ResultType;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.report.OpenReport;
import br.com.felipeblaft.reports.usecase.registry.SituationScenario;
import org.junit.jupiter.api.Test;

/**
 * Início, busca e resumo com professor inativo e com aluno inativo (R-23, FR-022): relatórios
 * congelados ficam preservados e fora de listas e ações; reativar devolve; aluno inativo sai das
 * listas padrão mas o professor associado ainda vê o relatório já em aberto.
 */
class HomeInactiveTeacherTest {

    private final SituationScenario c = new SituationScenario();
    private final GetTeacherHomeUseCase home = new GetTeacherHomeUseCase(c.students, c.open);
    private final SearchStudentsUseCase search =
            new SearchStudentsUseCase(c.students, c.open, c.eligibility);
    private final GetStudentSummaryUseCase summary =
            new GetStudentSummaryUseCase(
                    c.students, c.open, c.assignmentAuthorization, c.eligibility);
    private final Teacher clara = c.teacher("Clara Souza");
    private final Student matteo = c.student("Matteo Mafra", clara);

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    private void inactivate(Teacher teacher) {
        teacher.inactivate(c.clock.now());
        c.teachers.save(teacher);
    }

    private void reactivate(Teacher teacher) {
        teacher.reactivate(c.clock.now());
        c.teachers.save(teacher);
    }

    @Test
    void inactiveTeacherIsRefusedEverywhereAndReportsStayPreserved() {
        OpenReport report = c.openReport(clara, matteo);
        inactivate(clara);

        assertThatThrownBy(() -> home.run(clara))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TEACHER_INACTIVE));
        assertThatThrownBy(() -> search.run(clara.id(), ""))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TEACHER_INACTIVE));
        assertThatThrownBy(() -> search.exactMatch(clara.id(), "matteo"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TEACHER_INACTIVE));
        assertThatThrownBy(() -> summary.run(clara.id(), matteo.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TEACHER_INACTIVE));
        // nada foi apagado nem alterado
        assertThat(c.open.listByTeacher(clara.id())).containsExactly(report);
        assertThat(c.assignments.exists(clara.id(), matteo.id())).isTrue();
    }

    @Test
    void reactivationReturnsTheReportsAndTheStudents() {
        OpenReport report = c.openReport(clara, matteo);
        inactivate(clara);
        reactivate(clara);

        GetTeacherHomeUseCase.Home back = home.run(clara);

        assertThat(back.openReports()).containsExactly(report);
        assertThat(back.students()).extracting(StudentSummary::name).containsExactly("Matteo Mafra");
        assertThat(search.run(clara.id(), "matteo").students()).hasSize(1);
        assertThat(summary.run(clara.id(), matteo.id()).hasOpenReport()).isTrue();
    }

    @Test
    void unknownTeacherIsNotFound() {
        assertThatThrownBy(() -> search.run(java.util.UUID.randomUUID(), ""))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void inactiveStudentLeavesTheDefaultListsButNotTheOpenReport() {
        Student olavo = c.student("Olavo Torrente", clara);
        OpenReport report = c.openReport(clara, matteo);
        c.inactivateDirectly(matteo);

        GetTeacherHomeUseCase.Home result = home.run(clara);

        assertThat(result.totalStudents()).isEqualTo(1);
        assertThat(result.students()).extracting(StudentSummary::name).containsExactly("Olavo Torrente");
        // o relatório já em aberto continua à vista do professor associado
        assertThat(result.openReports()).containsExactly(report);
        assertThat(search.run(clara.id(), "").students())
                .extracting(StudentSummary::name)
                .containsExactly("Olavo Torrente");
        SearchStudentsUseCase.Result none = search.run(clara.id(), "matteo");
        assertThat(none.type()).isEqualTo(ResultType.NONE);
        assertThat(none.students()).extracting(StudentSummary::id).containsExactly(olavo.id());
        assertThat(search.exactMatch(clara.id(), "matteo")).isEmpty();
        // o resumo do aluno associado continua acessível
        assertThat(summary.run(clara.id(), matteo.id()).hasOpenReport()).isTrue();
    }

    @Test
    void reactivatedStudentReturnsToTheLists() {
        c.inactivateDirectly(matteo);
        assertThat(home.run(clara).withoutStudents()).isTrue();

        matteo.reactivate(c.clock.now());
        c.students.save(matteo);

        assertThat(home.run(clara).students()).hasSize(1);
        assertThat(search.exactMatch(clara.id(), "matteo")).isPresent();
    }
}
