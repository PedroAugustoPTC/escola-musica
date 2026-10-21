package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Escopo, filtro de situação e limites de validação (data-model.md §5). */
class RegistrySupportTypesTest {

    @Test
    void scopeOfSchoolIsUnrestrictedAndScopeOfTeacherIsNot() {
        UUID teacher = UUID.randomUUID();
        assertThat(StudentScope.all().unrestricted()).isTrue();
        assertThat(StudentScope.all().teacherId()).isNull();
        assertThat(StudentScope.ofTeacher(teacher).unrestricted()).isFalse();
        assertThat(StudentScope.ofTeacher(teacher).teacherId()).isEqualTo(teacher);
        assertThatThrownBy(() -> StudentScope.ofTeacher(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void statusFilterMatchesBySituation() {
        assertThat(StudentStatusFilter.ACTIVE.matches(true)).isTrue();
        assertThat(StudentStatusFilter.ACTIVE.matches(false)).isFalse();
        assertThat(StudentStatusFilter.INACTIVE.matches(true)).isFalse();
        assertThat(StudentStatusFilter.INACTIVE.matches(false)).isTrue();
        assertThat(StudentStatusFilter.ALL.matches(true)).isTrue();
        assertThat(StudentStatusFilter.ALL.matches(false)).isTrue();
    }

    @Test
    void limitsFollowTheDataModel() {
        assertThat(RegistryLimits.NAME_MIN).isEqualTo(2);
        assertThat(RegistryLimits.NAME_MAX).isEqualTo(120);
        assertThat(RegistryLimits.INSTRUMENT_MAX).isEqualTo(60);
        assertThat(RegistryLimits.EMAIL_MAX).isEqualTo(254);
        assertThat(RegistryLimits.DDD_MIN).isEqualTo(11);
        assertThat(RegistryLimits.DDD_MAX).isEqualTo(99);
        assertThat(RegistryLimits.PHONE_DIGITS_LANDLINE).isEqualTo(10);
        assertThat(RegistryLimits.PHONE_DIGITS_MOBILE).isEqualTo(11);
        assertThat(RegistryLimits.BIRTH_MAX_AGE_YEARS).isEqualTo(120);
        assertThat(RegistryLimits.ADULT_AGE).isEqualTo(Student.ADULT_AGE).isEqualTo(18);
        assertThat(RegistryLimits.PASSWORD_MIN).isEqualTo(12);
        assertThat(RegistryLimits.PASSWORD_MAX).isEqualTo(128);
        assertThat(RegistryLimits.ACTIVATION_CODE_SIZE).isEqualTo(12);
        assertThat(RegistryLimits.ACTIVATION_CODE_VALIDITY).isEqualTo(Duration.ofDays(7));
        assertThat(RegistryLimits.SEARCH_MAX).isEqualTo(100);
        assertThat(RegistryLimits.TELEGRAM_PAGE_SIZE).isEqualTo(6);
    }
}
