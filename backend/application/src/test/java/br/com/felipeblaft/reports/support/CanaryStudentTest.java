package br.com.felipeblaft.reports.support;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CanaryStudentTest {

    @Test
    void isMinorOnTheDefaultTestDate() {
        LocalDate today = AdjustableClock.DEFAULT.atZone(java.time.ZoneOffset.UTC).toLocalDate();

        assertThat(Student.requiresGuardian(CanaryStudent.BIRTH_DATE, today)).isTrue();
    }

    @Test
    void remainsMinorForYears() {
        assertThat(Student.requiresGuardian(CanaryStudent.BIRTH_DATE, LocalDate.of(2030, 1, 1)))
                .isTrue();
    }

    @Test
    void guardianPhoneHasTenOrElevenDigitsAndAMaskedForm() {
        assertThat(CanaryStudent.GUARDIAN_PHONE_DIGITS).matches("\\d{10,11}");
        assertThat(CanaryStudent.GUARDIAN_PHONE_MASKED.replaceAll("\\D", ""))
                .isEqualTo(CanaryStudent.GUARDIAN_PHONE_DIGITS);
    }

    @Test
    void guardianEmailLooksLikeAnEmailOnAReservedDomain() {
        assertThat(CanaryStudent.GUARDIAN_EMAIL).matches("[^@\\s]+@[^@\\s]+\\.test");
    }

    @Test
    void birthDateHasTheTwoUsualWrittenForms() {
        assertThat(CanaryStudent.BIRTH_DATE_ISO).isEqualTo(CanaryStudent.BIRTH_DATE.toString());
        assertThat(CanaryStudent.BIRTH_DATE_BR).isEqualTo("14/03/2016");
    }

    @Test
    void everyValueIsDistinctAndNoWordIsSharedBetweenStudentAndGuardian() {
        Set<String> values = new HashSet<>(CanaryStudent.sweepValues().values());
        assertThat(values).hasSize(CanaryStudent.sweepValues().size());

        Set<String> studentWords = words(CanaryStudent.NAME);
        Set<String> guardianWords = words(CanaryStudent.GUARDIAN_NAME);
        assertThat(studentWords).doesNotContainAnyElementsOf(guardianWords);
    }

    @Test
    void sweepValuesLabelEveryPersonalDatum() {
        assertThat(CanaryStudent.sweepValues())
                .containsValues(
                        CanaryStudent.NAME,
                        CanaryStudent.BIRTH_DATE_ISO,
                        CanaryStudent.BIRTH_DATE_BR,
                        CanaryStudent.GUARDIAN_NAME,
                        CanaryStudent.GUARDIAN_PHONE_DIGITS,
                        CanaryStudent.GUARDIAN_EMAIL);
        assertThat(CanaryStudent.sweepValues().values()).doesNotContain(CanaryCpf.DIGITS);
    }

    @Test
    void sweepValuesWithCpfAddsTheCanaryCpf() {
        assertThat(CanaryStudent.sweepValuesWithCpf())
                .containsValues(CanaryCpf.DIGITS, CanaryStudent.NAME);
    }

    private static Set<String> words(String text) {
        Set<String> words = new HashSet<>();
        for (String word : text.toLowerCase().split("\\s+")) {
            words.add(word);
        }
        return words;
    }
}
