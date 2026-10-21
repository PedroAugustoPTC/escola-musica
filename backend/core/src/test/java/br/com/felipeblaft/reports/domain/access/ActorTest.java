package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActorTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void administratorActsOnlyThroughTheWeb() {
        Actor actor = Actor.administrator(id, true);
        assertThat(actor.role()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(actor.id()).isEqualTo(id);
        assertThat(actor.origin()).isEqualTo(ActionOrigin.WEB);
        assertThat(actor.passwordChangeRequired()).isTrue();
        assertThat(actor.isSystem()).isFalse();
    }

    @Test
    void teacherActsThroughWebOrTelegram() {
        assertThat(Actor.teacher(id, ActionOrigin.WEB).origin()).isEqualTo(ActionOrigin.WEB);
        Actor telegram = Actor.teacher(id, ActionOrigin.TELEGRAM);
        assertThat(telegram.role()).isEqualTo(Role.TEACHER);
        assertThat(telegram.passwordChangeRequired()).isFalse();
        assertThatThrownBy(() -> Actor.teacher(id, ActionOrigin.INITIAL_LOAD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void systemActorHasNeitherRoleNorId() {
        Actor actor = Actor.system(ActionOrigin.INITIAL_LOAD);
        assertThat(actor.isSystem()).isTrue();
        assertThat(actor.role()).isNull();
        assertThat(actor.id()).isNull();
        assertThatThrownBy(() -> Actor.system(ActionOrigin.WEB))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInconsistentCombinations() {
        assertThatThrownBy(() -> new Actor(Role.TEACHER, null, ActionOrigin.WEB, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Actor(Role.TEACHER, id, ActionOrigin.INITIAL_LOAD, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Actor(null, id, ActionOrigin.INITIAL_LOAD, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Actor(null, null, ActionOrigin.INITIAL_LOAD, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Actor(Role.ADMINISTRATOR, id, null, false))
                .isInstanceOf(NullPointerException.class);
    }
}
