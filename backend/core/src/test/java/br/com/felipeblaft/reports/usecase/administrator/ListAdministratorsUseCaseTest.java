package br.com.felipeblaft.reports.usecase.administrator;

import static br.com.felipeblaft.reports.usecase.administrator.AdministratorScenario.failWith;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.doubles.ActorFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListAdministratorsUseCaseTest {

    private final AdministratorScenario c = new AdministratorScenario();
    private final ListAdministratorsUseCase useCase =
            new ListAdministratorsUseCase(c.authorization, c.administrators);
    private final Administrator ana = c.activated("Ana Souza", "ana@school.test");
    private final Administrator bia = c.pending("Bia Lima", "bia@school.test", "KQMXPTRWHNCV");
    private final Administrator caio = inactive("Caio Dias", "caio@school.test");

    private Administrator inactive(String name, String email) {
        Administrator administrator = c.activated(name, email).inactivate(c.clock.now());
        c.administrators.save(administrator);
        return administrator;
    }

    private PageResult<AdministratorView> list(String search, Boolean active) {
        return useCase.run(
                ActorFixtures.administratorWeb(ana.id()),
                new ListAdministratorsUseCase.Command(search, active, PageRequest.first()));
    }

    @Test
    void showsPendingActivationAndActivatedWithTheDate() {
        List<AdministratorView> items = list(null, null).items();

        assertThat(items)
                .extracting(AdministratorView::name)
                .containsExactly("Ana Souza", "Bia Lima", "Caio Dias");
        AdministratorView anaView = items.get(0);
        assertThat(anaView.activationState())
                .isEqualTo(AdministratorView.ActivationState.ACTIVATED);
        assertThat(anaView.activatedAt()).isEqualTo(ana.activatedAt());
        assertThat(anaView.active()).isTrue();
        AdministratorView biaView = items.get(1);
        assertThat(biaView.activationState()).isEqualTo(AdministratorView.ActivationState.PENDING);
        assertThat(biaView.activatedAt()).isNull();
        assertThat(biaView.active()).isTrue();
    }

    @Test
    void anInactiveOneIsShownAsInactiveWithoutAnActivationDate() {
        AdministratorView view = list("caio", null).items().getFirst();

        assertThat(view.id()).isEqualTo(caio.id());
        assertThat(view.active()).isFalse();
        assertThat(view.activationState()).isEqualTo(AdministratorView.ActivationState.PENDING);
        assertThat(view.activatedAt()).isNull();
    }

    @Test
    void filtersBySituationAndSearchesByNameOrEmail() {
        assertThat(list(null, true).items())
                .extracting(AdministratorView::id)
                .containsExactly(ana.id(), bia.id());
        assertThat(list(null, false).items())
                .extracting(AdministratorView::id)
                .containsExactly(caio.id());
        assertThat(list("BIA@school", null).items())
                .extracting(AdministratorView::id)
                .containsExactly(bia.id());
        assertThat(list("zzz", null).total()).isZero();
    }

    @Test
    void paginates() {
        PageResult<AdministratorView> page =
                useCase.run(
                        ActorFixtures.administratorWeb(ana.id()),
                        new ListAdministratorsUseCase.Command(null, null, new PageRequest(1, 2)));

        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    void neverExposesAHashOrACode() {
        assertThat(list(null, null).items().toString())
                .doesNotContain("activation:", "fake-argon2id", "Ana", "@school.test");
    }

    @Test
    void aBlankSearchAndAMissingPageUseTheDefaults() {
        PageResult<AdministratorView> page =
                useCase.run(
                        ActorFixtures.administratorWeb(ana.id()),
                        new ListAdministratorsUseCase.Command("   ", null, null));

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(PageRequest.DEFAULT_SIZE);
    }

    @Test
    void readingWritesNoAuthorship() {
        list(null, null);

        assertThat(c.recorder.isEmpty()).isTrue();
    }

    @Test
    void onlyAnAdministratorMayList() {
        ListAdministratorsUseCase.Command command =
                new ListAdministratorsUseCase.Command(null, null, PageRequest.first());

        failWith(
                () -> useCase.run(ActorFixtures.teacherWeb(), command),
                ErrorCode.RESOURCE_NOT_FOUND);
        failWith(
                () -> useCase.run(ActorFixtures.teacherTelegram(), command),
                ErrorCode.RESOURCE_NOT_FOUND);
        failWith(
                () -> useCase.run(ActorFixtures.administratorWithProvisionalPassword(), command),
                ErrorCode.PASSWORD_CHANGE_REQUIRED);
    }

    @Test
    void theCommandDoesNotRevealTheSearch() {
        assertThat(
                        new ListAdministratorsUseCase.Command("ana", null, PageRequest.first())
                                .toString())
                .doesNotContain("ana");
    }
}
