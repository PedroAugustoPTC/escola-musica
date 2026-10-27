package br.com.felipeblaft.reports.usecase.registry;

import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_A;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_B;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_C;
import static br.com.felipeblaft.reports.usecase.registry.RegistryScenario.CPF_D;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Lista e busca de alunos do administrador (US3). */
class ListStudentsUseCaseTest {

    private final RegistryScenario scenario = new RegistryScenario();
    private final Actor admin = ActorFixtures.administratorWeb();
    private final Teacher clara = scenario.teacher("Clara Dias");
    private final Teacher bruno = scenario.teacher("Bruno Lima");

    private PageResult<StudentView> run(
            String search, StudentStatusFilter status, PageRequest page) {
        return scenario.list.run(admin, new ListStudentsUseCase.Command(search, status, page));
    }

    private List<String> names(PageResult<StudentView> page) {
        return page.items().stream().map(StudentView::name).toList();
    }

    private void seed() {
        scenario.student("Ana Souza", "1990-03-04", CPF_A, "Piano", clara);
        scenario.student("Álvaro Prado", "1985-01-01", CPF_B, "Violão", bruno);
        scenario.student("Beatriz Luz", "2000-07-07", CPF_C, "Sax", clara, bruno);
        Student inactive = scenario.student("Carlos Inativo", "1999-09-09", CPF_D, "Piano");
        inactive.inactivate(scenario.clock.now());
    }

    @Test
    void administratorSeesTheWholeSchoolWithTheirTeachers() {
        seed();

        PageResult<StudentView> page = run(null, null, null);

        assertThat(names(page)).containsExactly("Álvaro Prado", "Ana Souza", "Beatriz Luz");
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(PageRequest.DEFAULT_SIZE);
        StudentView beatriz = page.items().get(2);
        assertThat(beatriz.teachers())
                .extracting(StudentView.TeacherRef::name)
                .containsExactly("Bruno Lima", "Clara Dias");
        assertThat(beatriz.canInactivate()).isNull();
        assertThat(beatriz.age()).isEqualTo(26);
    }

    @Test
    void searchesByNameWithoutCaseOrAccent() {
        seed();

        assertThat(names(run("alvaro", null, null))).containsExactly("Álvaro Prado");
        assertThat(names(run("  ANA ", null, null))).containsExactly("Ana Souza");
        assertThat(names(run("souza", null, null))).containsExactly("Ana Souza");
    }

    @Test
    void searchesByInstrumentWithoutCaseOrAccent() {
        seed();

        assertThat(names(run("violao", null, null))).containsExactly("Álvaro Prado");
        assertThat(names(run("PIANO", null, null))).containsExactly("Ana Souza");
        assertThat(names(run("sax", null, null))).containsExactly("Beatriz Luz");
    }

    @Test
    void searchWithNoMatchReturnsAnEmptyPage() {
        seed();

        PageResult<StudentView> page = run("zzz", null, null);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    void blankSearchMeansNoFilter() {
        seed();

        assertThat(names(run("   ", null, null))).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "529.982.247-25", "529982.24725", " 529-982-247.25 "})
    void cpfShapedSearchIsRefusedWithoutRepeatingTheTerm(String term) {
        seed();

        DomainException error =
                catchThrowableOfType(DomainException.class, () -> run(term, null, null));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.details().get("fields"))
                .isEqualTo(List.of(Map.of("field", "search", "code", "INVALID_DATA")));
        assertThat(error.details().toString() + error.getMessage()).doesNotContain("529");
    }

    @Test
    void numbersThatAreNotCpfShapedAreStillAValidSearch() {
        seed();

        assertThat(run("1234567890", null, null).items()).isEmpty();
        assertThat(run("123", null, null).items()).isEmpty();
    }

    @Test
    void tooLongSearchIsRefused() {
        DomainException error =
                catchThrowableOfType(DomainException.class, () -> run("a".repeat(101), null, null));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.details().get("fields"))
                .isEqualTo(List.of(Map.of("field", "search", "code", "INVALID_DATA")));
    }

    @Test
    void filtersBySituation() {
        seed();

        assertThat(names(run(null, StudentStatusFilter.ACTIVE, null))).hasSize(3);
        assertThat(names(run(null, StudentStatusFilter.INACTIVE, null)))
                .containsExactly("Carlos Inativo");
        assertThat(names(run(null, StudentStatusFilter.ALL, null))).hasSize(4);
    }

    @Test
    void paginatesAfterFiltering() {
        seed();

        PageResult<StudentView> first = run(null, StudentStatusFilter.ALL, new PageRequest(0, 3));
        PageResult<StudentView> second = run(null, StudentStatusFilter.ALL, new PageRequest(1, 3));
        PageResult<StudentView> beyond = run(null, StudentStatusFilter.ALL, new PageRequest(5, 3));

        assertThat(names(first)).containsExactly("Álvaro Prado", "Ana Souza", "Beatriz Luz");
        assertThat(names(second)).containsExactly("Carlos Inativo");
        assertThat(second.total()).isEqualTo(4);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.total()).isEqualTo(4);
    }

    @Test
    void studentWithoutTeachersAppearsForTheAdministrator() {
        scenario.student("Sem Professor", "1990-03-04", CPF_A, null);

        PageResult<StudentView> page = run(null, null, null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().teachers()).isEmpty();
    }

    @Test
    void guardianContactIsListedWhenPresent() {
        scenario.register.run(
                admin,
                RegistryScenario.registerCommand(RegistryScenario.minor("Matteo Mafra", CPF_A)));

        StudentView view = run(null, null, null).items().getFirst();

        assertThat(view.guardian().name()).isEqualTo("Maria Silva");
        assertThat(view.guardian().email()).isEqualTo("maria@example.com");
        assertThat(view.toString()).doesNotContain("Matteo");
    }

    @Test
    void doesNotRecordAuthorshipForReads() {
        seed();

        run(null, null, null);

        assertThat(scenario.recorder.isEmpty()).isTrue();
    }

    @Test
    void administratorWithProvisionalPasswordCannotList() {
        DomainException error =
                catchThrowableOfType(
                        DomainException.class,
                        () ->
                                scenario.list.run(
                                        ActorFixtures.administratorWithProvisionalPassword(),
                                        new ListStudentsUseCase.Command(null, null, null)));

        assertThat(error.code()).isEqualTo(ErrorCode.PASSWORD_CHANGE_REQUIRED);
    }

    @Test
    void commandTextRevealsNothing() {
        assertThat(new ListStudentsUseCase.Command("Ana", null, null).toString())
                .isEqualTo("ListStudentsUseCase.Command");
    }
}
