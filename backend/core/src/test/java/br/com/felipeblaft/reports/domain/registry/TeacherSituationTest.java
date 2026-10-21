package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Inativar, reativar e editar o professor (feature 005). */
class TeacherSituationTest {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant T1 = Instant.parse("2026-09-02T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-03T12:00:00Z");

    private Teacher teacherWithCode() {
        Teacher teacher = Teacher.create("Clara Souza", "Clara@School.com", true, T0);
        teacher.replaceCode("hash", T0, Duration.ofDays(7));
        return teacher;
    }

    @Test
    void inactivateInvalidatesTheCode() {
        Teacher teacher = teacherWithCode();
        assertThat(teacher.inactivate(T1)).isTrue();
        assertThat(teacher.active()).isFalse();
        assertThat(teacher.accessCodeHash()).isNull();
        assertThat(teacher.accessCodeExpiresAt()).isNull();
        assertThat(teacher.accessCodeRenewedAt()).isNull();
        assertThat(teacher.validCodeAt(T1)).isFalse();
        assertThat(teacher.updatedAt()).isEqualTo(T1);
        assertThat(teacher.inactivate(T2)).isFalse();
        assertThat(teacher.updatedAt()).isEqualTo(T1);
    }

    @Test
    void reactivateDoesNotRestoreTheCode() {
        Teacher teacher = teacherWithCode();
        teacher.inactivate(T1);
        assertThat(teacher.reactivate(T2)).isTrue();
        assertThat(teacher.active()).isTrue();
        assertThat(teacher.accessCodeHash()).isNull();
        assertThat(teacher.updatedAt()).isEqualTo(T2);
        assertThat(teacher.reactivate(T2)).isFalse();
    }

    @Test
    void renameAndChangeEmailNormalize() {
        Teacher teacher = teacherWithCode();
        teacher.rename("Clara S. Souza", T1);
        teacher.changeEmail("  FRESH@School.com ", T2);
        assertThat(teacher.name()).isEqualTo("Clara S. Souza");
        assertThat(teacher.email()).isEqualTo("fresh@school.com");
        assertThat(teacher.updatedAt()).isEqualTo(T2);
    }

    @Test
    void toStringRevealsNoPersonalData() {
        assertThat(teacherWithCode().toString())
                .doesNotContain("Clara")
                .doesNotContain("school.com")
                .doesNotContain("hash");
    }
}
