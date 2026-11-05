package br.com.felipeblaft.reports.usecase.registry;

import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_A;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_B;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.adult;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.registerCommand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Depois da eliminação o aluno some de buscas, listas e "Meus alunos", em todo canal, e o CPF
 * antigo fica livre para novo cadastro (FR-024).
 */
class ErasedStudentVisibilityTest {

    private final RegistryScenario scenario = new RegistryScenario();
    private final Teacher clara = scenario.teacher("Clara Dias");
    private final Actor admin = ActorFixtures.administratorWeb();
    private final Actor claraWeb = ActorFixtures.teacherWeb(clara.id());
    private final Actor claraTelegram = ActorFixtures.teacherTelegram(clara.id());
    private final Student erased =
            scenario.student("Ana Souza", "1990-03-04", CPF_A, "Piano", clara);
    private final Student kept =
            scenario.student("Ana Mantida", "1990-03-04", CPF_B, "Piano", clara);

    private List<String> names(Actor actor, String search, StudentStatusFilter status) {
        return scenario
                .list
                .run(actor, new ListStudentsUseCase.Command(search, status, null))
                .items()
                .stream()
                .map(StudentView::name)
                .toList();
    }

    @Test
    void findVisibleAndSearchByIdNoLongerReturnTheErasedStudent() {
        scenario.students.erase(erased.id(), scenario.clock.now());

        assertThat(scenario.students.findVisible(StudentScope.all(), erased.id())).isEmpty();
        assertThat(scenario.students.findVisible(StudentScope.ofTeacher(clara.id()), erased.id()))
                .isEmpty();
        assertThat(scenario.students.searchById(erased.id())).isEmpty();
        assertThat(scenario.students.findVisible(StudentScope.all(), kept.id())).isPresent();
    }

    @Test
    void listsAndSearchesOfAdministratorAndTeacherSkipTheErasedStudent() {
        scenario.students.erase(erased.id(), scenario.clock.now());

        for (Actor actor : List.of(admin, claraWeb, claraTelegram)) {
            assertThat(names(actor, null, StudentStatusFilter.ALL)).containsExactly("Ana Mantida");
            assertThat(names(actor, "souza", StudentStatusFilter.ALL)).isEmpty();
            assertThat(names(actor, "ana", null)).containsExactly("Ana Mantida");
        }
        assertThat(scenario.students.listByTeacher(clara.id()))
                .extracting(Student::name)
                .containsExactly("Ana Mantida");
    }

    @Test
    void openingTheErasedStudentIsNotFoundForEveryRole() {
        scenario.students.erase(erased.id(), scenario.clock.now());

        for (Actor actor : List.of(admin, claraWeb, claraTelegram)) {
            DomainException refusal =
                    catchThrowableOfType(
                            DomainException.class,
                            () ->
                                    scenario.get.run(
                                            actor,
                                            new GetStudentUseCase.Command(erased.id(), "req-9")));
            assertThat(refusal.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Test
    void theOldCpfIsFreeForANewRegistration() {
        scenario.students.erase(erased.id(), scenario.clock.now());

        StudentView view = scenario.register.run(admin, registerCommand(adult("Ana Nova", CPF_A)));

        assertThat(view.name()).isEqualTo("Ana Nova");
        assertThat(view.id()).isNotEqualTo(erased.id());
    }

    @Test
    void assignmentsOfTheErasedStudentAreGone() {
        scenario.students.erase(erased.id(), scenario.clock.now());

        assertThat(scenario.assignments.listTeachersOfStudent(erased.id())).isEmpty();
        assertThat(scenario.assignments.studentsOf(clara.id())).containsExactly(kept.id());
    }
}
