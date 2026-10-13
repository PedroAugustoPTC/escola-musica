package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TeacherTest {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");
    private static final Duration SEVEN_DAYS = Duration.ofDays(7);

    private Teacher withCode() {
        Teacher teacher = Teacher.create("Clara Souza", " Clara@Exemplo.TEST ", true, T0);
        teacher.replaceCode("hash-1", T0, SEVEN_DAYS);
        return teacher;
    }

    @Test
    void newNormalizesEmailAndHasNoCode() {
        Teacher teacher = Teacher.create("Clara Souza", " Clara@Exemplo.TEST ", true, T0);
        assertThat(teacher.email()).isEqualTo("clara@exemplo.test");
        assertThat(teacher.accessCodeHash()).isNull();
        assertThat(teacher.validCodeAt(T0)).isFalse();
        assertThat(teacher.codeExpiredAt(T0)).isFalse();
        assertThat(teacher.renewValidity(T0, SEVEN_DAYS)).isFalse();
        assertThat(teacher.firstName()).isEqualTo("Clara");
        assertThat(teacher.createdAt()).isEqualTo(T0);
        assertThat(Teacher.normalizeEmail(null)).isNull();
    }

    @Test
    void codeValidUntilSevenDaysAfterIssuance() {
        Teacher teacher = withCode();
        assertThat(teacher.accessCodeExpiresAt()).isEqualTo(T0.plus(SEVEN_DAYS));
        assertThat(teacher.validCodeAt(T0.plus(SEVEN_DAYS).minusSeconds(1))).isTrue();
        assertThat(teacher.validCodeAt(T0.plus(SEVEN_DAYS))).isFalse();
        assertThat(teacher.codeExpiredAt(T0.plus(SEVEN_DAYS))).isTrue();
        assertThat(teacher.codeExpiredAt(T0)).isFalse();
    }

    @Test
    void inactiveTeacherHasNoValidCode() {
        Teacher teacher = withCode();
        assertThat(teacher.updateRegistry("Clara Souza", false, T0.plusSeconds(1))).isTrue();
        assertThat(teacher.active()).isFalse();
        assertThat(teacher.validCodeAt(T0.plusSeconds(2))).isFalse();
    }

    @Test
    void renewsAtMostOncePerHour() {
        Teacher teacher = withCode();
        assertThat(teacher.renewValidity(T0.plus(Duration.ofMinutes(59)), SEVEN_DAYS)).isFalse();
        assertThat(teacher.accessCodeExpiresAt()).isEqualTo(T0.plus(SEVEN_DAYS));

        Instant oneHourLater = T0.plus(Duration.ofHours(1));
        assertThat(teacher.renewValidity(oneHourLater, SEVEN_DAYS)).isTrue();
        assertThat(teacher.accessCodeExpiresAt()).isEqualTo(oneHourLater.plus(SEVEN_DAYS));
        assertThat(teacher.accessCodeRenewedAt()).isEqualTo(oneHourLater);
        assertThat(teacher.updatedAt()).isEqualTo(oneHourLater);
    }

    @Test
    void renewsWhenNeverRenewed() {
        Teacher teacher =
                new Teacher(
                        Teacher.create("A", "a@b.c", true, T0).id(),
                        "A",
                        "a@b.c",
                        true,
                        "hash",
                        T0,
                        null,
                        T0,
                        T0);
        assertThat(teacher.renewValidity(T0, SEVEN_DAYS)).isTrue();
    }

    @Test
    void reissueReplacesHashAndRestartsValidity() {
        Teacher teacher = withCode();
        Instant after = T0.plus(Duration.ofDays(10));
        teacher.replaceCode("hash-2", after, SEVEN_DAYS);
        assertThat(teacher.accessCodeHash()).isEqualTo("hash-2");
        assertThat(teacher.validCodeAt(after.plus(Duration.ofDays(6)))).isTrue();
    }

    @Test
    void updateRegistryWithoutChangeDoesNotAlter() {
        Teacher teacher = withCode();
        assertThat(teacher.updateRegistry("Clara Souza", true, T0.plusSeconds(5))).isFalse();
        assertThat(teacher.updateRegistry("Clara S.", true, T0.plusSeconds(5))).isTrue();
        assertThat(teacher.name()).isEqualTo("Clara S.");
    }
}
