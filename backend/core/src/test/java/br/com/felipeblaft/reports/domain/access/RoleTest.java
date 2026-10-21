package br.com.felipeblaft.reports.domain.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** SC-007: o sistema tem exatamente dois papéis; aluno e responsável nunca são papel. */
class RoleTest {

    @Test
    void hasExactlyTwoRoles() {
        assertThat(Role.values()).hasSize(2);
        assertThat(Arrays.stream(Role.values()).map(Enum::name))
                .containsExactlyInAnyOrder("ADMINISTRATOR", "TEACHER");
    }

    @Test
    void systemOriginsAreNotRoles() {
        assertThat(ActionOrigin.WEB.system()).isFalse();
        assertThat(ActionOrigin.TELEGRAM.system()).isFalse();
        assertThat(ActionOrigin.SYSTEM_STARTUP.system()).isTrue();
        assertThat(ActionOrigin.INITIAL_LOAD.system()).isTrue();
    }
}
