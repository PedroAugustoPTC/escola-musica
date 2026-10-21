package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.access.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActorFixturesTest {

    @Test
    void administratorWebActsOnTheWebWithoutRestriction() {
        Actor actor = ActorFixtures.administratorWeb();

        assertThat(actor.role()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(actor.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(actor.id()).isEqualTo(ActorFixtures.ADMINISTRATOR_ID);
        assertThat(actor.passwordChangeRequired()).isFalse();
    }

    @Test
    void administratorWithProvisionalPasswordIsRestricted() {
        Actor actor = ActorFixtures.administratorWithProvisionalPassword();

        assertThat(actor.role()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(actor.passwordChangeRequired()).isTrue();
    }

    @Test
    void administratorWithOtherIdKeepsTheWebOrigin() {
        UUID id = UUID.randomUUID();

        Actor actor = ActorFixtures.administratorWeb(id);

        assertThat(actor.id()).isEqualTo(id);
        assertThat(actor.origin()).isEqualTo(ActionOrigin.WEB);
    }

    @Test
    void teacherActsOnEachChannelWithTheSameId() {
        Actor web = ActorFixtures.teacherWeb();
        Actor telegram = ActorFixtures.teacherTelegram();

        assertThat(web.role()).isEqualTo(Role.TEACHER);
        assertThat(web.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(telegram.role()).isEqualTo(Role.TEACHER);
        assertThat(telegram.origin()).isEqualTo(ActionOrigin.TELEGRAM);
        assertThat(web.id()).isEqualTo(ActorFixtures.TEACHER_ID).isEqualTo(telegram.id());
        assertThat(web.passwordChangeRequired()).isFalse();
        assertThat(telegram.passwordChangeRequired()).isFalse();
    }

    @Test
    void teacherWithOtherIdKeepsTheChannel() {
        UUID id = UUID.randomUUID();

        assertThat(ActorFixtures.teacherWeb(id).id()).isEqualTo(id);
        assertThat(ActorFixtures.teacherWeb(id).origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(ActorFixtures.teacherTelegram(id).id()).isEqualTo(id);
        assertThat(ActorFixtures.teacherTelegram(id).origin()).isEqualTo(ActionOrigin.TELEGRAM);
    }

    @Test
    void systemHasNoRoleNorId() {
        Actor load = ActorFixtures.initialLoad();
        Actor startup = ActorFixtures.bootstrap();

        assertThat(load.isSystem()).isTrue();
        assertThat(load.origin()).isEqualTo(ActionOrigin.INITIAL_LOAD);
        assertThat(load.role()).isNull();
        assertThat(load.id()).isNull();
        assertThat(startup.isSystem()).isTrue();
        assertThat(startup.origin()).isEqualTo(ActionOrigin.SYSTEM_STARTUP);
    }

    @Test
    void fixedIdsAreDistinct() {
        assertThat(ActorFixtures.ADMINISTRATOR_ID).isNotEqualTo(ActorFixtures.TEACHER_ID);
    }
}
