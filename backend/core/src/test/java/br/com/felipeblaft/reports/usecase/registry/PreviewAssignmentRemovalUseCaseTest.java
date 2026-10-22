package br.com.felipeblaft.reports.usecase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import br.com.felipeblaft.reports.usecase.registry.PreviewAssignmentRemovalUseCase.Command;
import org.junit.jupiter.api.Test;

/** Prévia da retirada (FR-029): conta os relatórios em aberto do professor com o aluno. */
class PreviewAssignmentRemovalUseCaseTest {

    private final SituationScenario c = new SituationScenario();
    private final Teacher clara = c.teacher("Clara Souza");
    private final Teacher bruno = c.teacher("Bruno Lima");

    private static ErrorCode codeOf(Throwable thrown) {
        return ((DomainException) thrown).code();
    }

    @Test
    void countsOnlyOpenReportsOfThatTeacherWithThatStudent() {
        Student matteo = c.student("Matteo Mafra", clara);
        Student olavo = c.student("Olavo Torrente", clara);
        c.assignments.assign(bruno.id(), matteo.id());
        c.openReport(clara, matteo);
        c.openReport(clara, matteo);
        c.openReport(clara, olavo);
        c.openReport(bruno, matteo);

        PreviewAssignmentRemovalUseCase.Result result =
                c.preview.run(c.administrator(), new Command(matteo.id(), clara.id()));

        assertThat(result.frozenReports()).isEqualTo(2);
    }

    @Test
    void zeroWhenThereIsNothingOpenAndItWritesNothing() {
        Student matteo = c.student("Matteo Mafra", clara);

        assertThat(c.preview.run(c.administrator(), new Command(matteo.id(), clara.id())))
                .isEqualTo(new PreviewAssignmentRemovalUseCase.Result(0));
        assertThat(c.recorder.isEmpty()).isTrue();
        assertThat(c.assignments.exists(clara.id(), matteo.id())).isTrue();
    }

    @Test
    void absentAssociationIsNotFound() {
        Student matteo = c.student("Matteo Mafra", clara);

        assertThatThrownBy(
                        () ->
                                c.preview.run(
                                        c.administrator(), new Command(matteo.id(), bruno.id())))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void onlyTheAdministratorMayPreview() {
        Student matteo = c.student("Matteo Mafra", clara);
        Command command = new Command(matteo.id(), clara.id());

        for (Actor actor :
                new Actor[] {
                    Actor.teacher(clara.id(), ActionOrigin.WEB),
                    Actor.teacher(clara.id(), ActionOrigin.TELEGRAM),
                    ActorFixtures.initialLoad()
                }) {
            assertThatThrownBy(() -> c.preview.run(actor, command))
                    .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
        assertThatThrownBy(
                        () ->
                                c.preview.run(
                                        ActorFixtures.administratorWithProvisionalPassword(),
                                        command))
                .satisfies(
                        e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PASSWORD_CHANGE_REQUIRED));
    }
}
