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
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O professor só enxerga os alunos associados a ele, em todo caso de uso e canal (FR-025). */
class StudentVisibilityTest {

    private final RegistryScenario scenario = new RegistryScenario();
    private final Teacher clara = scenario.teacher("Clara Dias");
    private final Teacher bruno = scenario.teacher("Bruno Lima");
    private final Actor claraWeb = ActorFixtures.teacherWeb(clara.id());
    private final Actor claraTelegram = ActorFixtures.teacherTelegram(clara.id());
    private final Student mine = scenario.student("Ana Souza", "1990-03-04", CPF_A, "Piano", clara);
    private final Student alien =
            scenario.student("Ana Alheia", "1990-03-04", CPF_B, "Piano", bruno);
    private final Student orphan = scenario.student("Ana Sem Dono", "1990-03-04", CPF_C, "Piano");

    private List<String> names(Actor actor, String search, StudentStatusFilter status) {
        return scenario
                .list
                .run(actor, new ListStudentsUseCase.Command(search, status, null))
                .items()
                .stream()
                .map(StudentView::name)
                .toList();
    }

    private DomainException getRefusal(Actor actor, UUID id) {
        return catchThrowableOfType(
                DomainException.class,
                () -> scenario.get.run(actor, new GetStudentUseCase.Command(id, "req-9")));
    }

    @Test
    void listsOnlyTheirOwnStudentsOnBothChannels() {
        assertThat(names(claraWeb, null, null)).containsExactly("Ana Souza");
        assertThat(names(claraTelegram, null, null)).containsExactly("Ana Souza");
    }

    @Test
    void searchNeverReachesStudentsOfOthers() {
        assertThat(names(claraWeb, "ana", null)).containsExactly("Ana Souza");
        assertThat(names(claraWeb, "alheia", null)).isEmpty();
        assertThat(names(claraWeb, "sem dono", null)).isEmpty();
        assertThat(names(claraWeb, "piano", null)).containsExactly("Ana Souza");
    }

    @Test
    void cpfShapedSearchIsRefusedForTheTeacherToo() {
        DomainException error =
                catchThrowableOfType(
                        DomainException.class, () -> names(claraWeb, "529.982.247-25", null));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(error.details().toString()).doesNotContain("529");
    }

    @Test
    void inactiveStudentsOnlyAppearWhenFilteringBySituation() {
        Student inactive = scenario.student("Ana Parada", "1990-03-04", CPF_D, null, clara);
        inactive.inactivate(scenario.clock.now());

        assertThat(names(claraWeb, null, null)).containsExactly("Ana Souza");
        assertThat(names(claraWeb, null, StudentStatusFilter.ACTIVE)).containsExactly("Ana Souza");
        assertThat(names(claraWeb, null, StudentStatusFilter.INACTIVE))
                .containsExactly("Ana Parada");
        assertThat(names(claraWeb, null, StudentStatusFilter.ALL))
                .containsExactly("Ana Parada", "Ana Souza");
    }

    @Test
    void paginatesTheirOwnList() {
        scenario.student("Bia Luz", "1990-03-04", CPF_D, null, clara);

        var page =
                scenario.list.run(
                        claraTelegram,
                        new ListStudentsUseCase.Command(
                                null, StudentStatusFilter.ACTIVE, new PageRequest(1, 1)));

        assertThat(page.items()).extracting(StudentView::name).containsExactly("Bia Luz");
        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    void listOfATeacherWithoutStudentsIsEmpty() {
        assertThat(names(ActorFixtures.teacherWeb(UUID.randomUUID()), null, null)).isEmpty();
    }

    @Test
    void opensTheirOwnStudentWithoutOtherTeachers() {
        StudentView view =
                scenario.get.run(claraWeb, new GetStudentUseCase.Command(mine.id(), "req-9"));

        assertThat(view.name()).isEqualTo("Ana Souza");
        assertThat(view.teachers()).isEmpty();
        assertThat(view.canInactivate()).isTrue();
    }

    @Test
    void canInactivateOnlyWhenTheTeacherIsTheSoleActiveOne() {
        Teacher inactiveTeacher = scenario.teacher("Velho Mestre", false);
        scenario.assignments.assign(inactiveTeacher.id(), mine.id());
        assertThat(canInactivate(mine)).as("outro professor inativo não conta").isTrue();

        scenario.assignments.assign(bruno.id(), mine.id());
        assertThat(canInactivate(mine)).as("outro professor ativo").isFalse();
    }

    private boolean canInactivate(Student student) {
        return scenario.get
                .run(claraWeb, new GetStudentUseCase.Command(student.id(), "req-9"))
                .canInactivate();
    }

    @Test
    void anotherTeachersStudentIsIndistinguishableFromMissingOrOrphan() {
        DomainException alienError = getRefusal(claraWeb, alien.id());
        DomainException orphanError = getRefusal(claraWeb, orphan.id());
        DomainException missingError = getRefusal(claraWeb, UUID.randomUUID());
        DomainException telegramError = getRefusal(claraTelegram, alien.id());

        for (DomainException error :
                new DomainException[] {alienError, orphanError, missingError, telegramError}) {
            assertThat(error.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            assertThat(error.details()).isEqualTo(Map.of());
            assertThat(error.getMessage()).isEqualTo("RESOURCE_NOT_FOUND");
        }
        var records = scenario.recorder.independent();
        assertThat(records).hasSize(4);
        assertThat(records)
                .extracting(r -> r.operation() + r.errorCode() + r.result() + r.correlationId())
                .containsOnly("AUTHORIZATION_DENIEDRESOURCE_NOT_FOUNDDENIEDreq-9");
    }

    @Test
    void theSameMissingPathRunsForAlienAndMissingStudents() {
        int before = scenario.recorder.records().size();

        getRefusal(claraWeb, alien.id());
        int afterAlien = scenario.recorder.records().size();
        getRefusal(claraWeb, UUID.randomUUID());
        int afterMissing = scenario.recorder.records().size();

        assertThat(afterAlien - before).isEqualTo(afterMissing - afterAlien).isEqualTo(1);
    }

    @Test
    void administratorSeesWhatTheTeacherCannot() {
        Actor admin = ActorFixtures.administratorWeb();

        assertThat(names(admin, "ana", null))
                .containsExactly("Ana Alheia", "Ana Sem Dono", "Ana Souza");
        assertThat(
                        scenario.get
                                .run(admin, new GetStudentUseCase.Command(orphan.id(), "req-9"))
                                .name())
                .isEqualTo("Ana Sem Dono");
    }

    @Test
    void retractedAssociationCutsTheAccessImmediately() {
        scenario.assignments.delete(clara.id(), mine.id());

        assertThat(getRefusal(claraWeb, mine.id()).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(names(claraWeb, null, null)).isEmpty();
    }
}
