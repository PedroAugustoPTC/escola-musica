package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BirthDateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    private static void refuses(String text) {
        assertThatThrownBy(() -> BirthDate.parse(text, TODAY))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_BIRTH_DATE));
    }

    @Test
    void parsesTheWebAndTheTelegramFormats() {
        assertThat(BirthDate.parse("2014-05-10", TODAY).value())
                .isEqualTo(LocalDate.of(2014, 5, 10));
        assertThat(BirthDate.parse("10/05/2014", TODAY).value())
                .isEqualTo(LocalDate.of(2014, 5, 10));
        assertThat(BirthDate.parse("  10/05/2014 ", TODAY).value())
                .isEqualTo(LocalDate.of(2014, 5, 10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2023-02-29", "2023-02-31", "31/02/2020", "31/04/2020", "2020-13-01"})
    void datesThatDoNotExistAreRefused(String text) {
        refuses(text);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc", "2020/01/01", "1/1/2020", "01-01-2020", "2020-1-1"})
    void otherFormatsAreRefused(String text) {
        refuses(text);
    }

    @Test
    void leapDayIsAccepted() {
        assertThat(BirthDate.parse("2008-02-29", TODAY).value())
                .isEqualTo(LocalDate.of(2008, 2, 29));
    }

    @Test
    void todayIsAcceptedAndTomorrowIsNot() {
        assertThat(BirthDate.parse("2026-09-21", TODAY).value()).isEqualTo(TODAY);
        refuses("2026-09-22");
        refuses("2027-01-01");
    }

    @Test
    void exactlyOneHundredTwentyYearsAgoIsAcceptedAndOneDayBeforeIsNot() {
        assertThat(BirthDate.parse("1906-09-21", TODAY).value())
                .isEqualTo(LocalDate.of(1906, 9, 21));
        refuses("1906-09-20");
        refuses("1850-01-01");
    }

    @Test
    void eighteenCompleteYearsIsAdult() {
        BirthDate birth = BirthDate.parse("2008-09-21", TODAY);
        assertThat(birth.ageAt(TODAY)).isEqualTo(18);
        assertThat(birth.isAdultAt(TODAY)).isTrue();
        assertThat(birth.ageAt(TODAY.minusDays(1))).isEqualTo(17);
        assertThat(birth.isAdultAt(TODAY.minusDays(1))).isFalse();
    }

    @Test
    void ageNeverGoesBelowZero() {
        assertThat(BirthDate.parse("2026-09-21", TODAY).ageAt(TODAY.minusDays(5))).isZero();
    }

    @Test
    void leapDayBirthdayCountsOnTheFirstOfMarchInCommonYears() {
        BirthDate birth = BirthDate.parse("2008-02-29", TODAY);
        assertThat(birth.ageAt(LocalDate.of(2026, 2, 28))).isEqualTo(17);
        assertThat(birth.ageAt(LocalDate.of(2026, 3, 1))).isEqualTo(18);
    }

    @Test
    void equalityByValueAndNoPersonalDataInText() {
        BirthDate birth = BirthDate.parse("2014-05-10", TODAY);
        assertThat(birth).isEqualTo(BirthDate.parse("10/05/2014", TODAY));
        assertThat(birth).hasSameHashCodeAs(BirthDate.parse("10/05/2014", TODAY));
        assertThat(birth).isNotEqualTo(BirthDate.parse("2014-05-11", TODAY));
        assertThat(birth).isNotEqualTo("2014-05-10");
        assertThat(birth.toString()).doesNotContain("2014").doesNotContain("05");
    }
}
