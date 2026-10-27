package br.com.felipeblaft.reports.usecase.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lista, busca e abertura de professores: o código de acesso nunca sai. */
class GetAndListTeachersUseCaseTest {

    private final TeacherScenario c = new TeacherScenario();
    private final ListTeachersUseCase list =
            new ListTeachersUseCase(c.actorAuthorization, c.teachers, c.links);
    private final GetTeacherUseCase get =
            new GetTeacherUseCase(c.actorAuthorization, c.teachers, c.links);
    private final Teacher clara = c.teacher("Clara Souza", "clara@example.test", "K7QM2XPA");
    private final Teacher bruno = c.teacher("Bruno Lima", "bruno@example.test", "BRUN2XPA");
    private final Teacher aline = c.teacher("Aline Dias", "aline@example.test", "ALNE2XPA");

    private ListTeachersUseCase.Command listing(String search, Boolean active) {
        return new ListTeachersUseCase.Command(search, active, PageRequest.first());
    }

    private static DomainException failure(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        return (DomainException)
                assertThatThrownBy(action).isInstanceOf(DomainException.class).actual();
    }

    @Test
    void listsAlphabeticallyWithSituationAndTelegramLink() {
        bruno.inactivate(c.clock.now());
        c.link(clara, 111L, 900L);

        PageResult<TeacherView> page =
                list.run(ActorFixtures.administratorWeb(), listing(null, null));

        assertThat(page.items())
                .extracting(TeacherView::name)
                .containsExactly("Aline Dias", "Bruno Lima", "Clara Souza");
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items().get(1).active()).isFalse();
        assertThat(page.items().get(2).telegramLinked()).isTrue();
        assertThat(page.items().get(0).telegramLinked()).isFalse();
        assertThat(aline.active()).isTrue();
    }

    @Test
    void filtersBySituationAndSearchesByNameOrEmailIgnoringCase() {
        bruno.inactivate(c.clock.now());

        assertThat(list.run(ActorFixtures.administratorWeb(), listing(null, false)).items())
                .extracting(TeacherView::name)
                .containsExactly("Bruno Lima");
        assertThat(list.run(ActorFixtures.administratorWeb(), listing("CLARA", null)).items())
                .extracting(TeacherView::name)
                .containsExactly("Clara Souza");
        assertThat(
                        list.run(ActorFixtures.administratorWeb(), listing("aline@EXAMPLE", true))
                                .items())
                .extracting(TeacherView::name)
                .containsExactly("Aline Dias");
    }

    @Test
    void blankSearchListsEverythingAndTooLongSearchIsRefused() {
        assertThat(list.run(ActorFixtures.administratorWeb(), listing("   ", null)).total())
                .isEqualTo(3);

        DomainException error =
                failure(
                        () ->
                                list.run(
                                        ActorFixtures.administratorWeb(),
                                        listing("x".repeat(101), null)));

        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void pagesTheResult() {
        PageResult<TeacherView> page =
                list.run(
                        ActorFixtures.administratorWeb(),
                        new ListTeachersUseCase.Command(null, null, new PageRequest(1, 2)));

        assertThat(page.items()).extracting(TeacherView::name).containsExactly("Clara Souza");
        assertThat(page.total()).isEqualTo(3);
    }

    @Test
    void viewNeverCarriesTheAccessCode() {
        TeacherView view =
                list.run(ActorFixtures.administratorWeb(), listing("clara", null))
                        .items()
                        .getFirst();

        assertThat(view.toString()).doesNotContain("K7QM2XPA").doesNotContain("Clara");
        assertThat(
                        Arrays.stream(TeacherView.class.getRecordComponents())
                                .map(RecordComponent::getName))
                .doesNotContain("accessCode", "accessCodeHash", "code");
        assertThat(listing("clara", null).toString()).doesNotContain("clara");
    }

    @Test
    void getOpensOneTeacherAndUnknownIsNotFound() {
        c.link(bruno, 222L, 901L);

        TeacherView view =
                get.run(
                        ActorFixtures.administratorWeb(),
                        new GetTeacherUseCase.Command(bruno.id()));

        assertThat(view.id()).isEqualTo(bruno.id());
        assertThat(view.telegramLinked()).isTrue();
        assertThat(
                        failure(
                                        () ->
                                                get.run(
                                                        ActorFixtures.administratorWeb(),
                                                        new GetTeacherUseCase.Command(
                                                                UUID.randomUUID())))
                                .code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void teachersCannotListOrOpenTeachers() {
        for (Actor actor :
                List.of(
                        ActorFixtures.teacherWeb(clara.id()),
                        ActorFixtures.teacherTelegram(clara.id()))) {
            assertThat(failure(() -> list.run(actor, listing(null, null))).code())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            assertThat(
                            failure(() -> get.run(actor, new GetTeacherUseCase.Command(bruno.id())))
                                    .code())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
