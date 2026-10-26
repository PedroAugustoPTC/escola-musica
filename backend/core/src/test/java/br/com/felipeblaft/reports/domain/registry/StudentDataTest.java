package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.common.DomainException;
import br.com.felipeblaft.reports.domain.common.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudentDataTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);
    private static final String CPF = "529.982.247-25";

    private static StudentData adult() {
        return StudentData.of("Ana Souza", "1990-03-04", CPF, "Piano", null, null, null, TODAY);
    }

    private static List<Map<String, String>> violations(DomainException error) {
        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> fields =
                (List<Map<String, String>>) error.details().get("fields");
        return fields;
    }

    private static DomainException refused(Runnable action) {
        return org.assertj.core.api.Assertions.catchThrowableOfType(
                DomainException.class, action::run);
    }

    private static Map<String, String> violation(String field, ErrorCode code) {
        return Map.of("field", field, "code", code.name());
    }

    @Test
    void validAdultWithoutGuardian() {
        StudentData data = adult();
        assertThat(data.name().value()).isEqualTo("Ana Souza");
        assertThat(data.birthDate().value()).isEqualTo(LocalDate.of(1990, 3, 4));
        assertThat(data.cpf().digits()).isEqualTo("52998224725");
        assertThat(data.instrument().value()).isEqualTo("Piano");
        assertThat(data.guardian()).isNull();
    }

    @Test
    void instrumentIsOptional() {
        assertThat(
                        StudentData.of(
                                        "Ana Souza",
                                        "1990-03-04",
                                        CPF,
                                        null,
                                        null,
                                        null,
                                        null,
                                        TODAY)
                                .instrument())
                .isNull();
        assertThat(
                        StudentData.of(
                                        "Ana Souza",
                                        "1990-03-04",
                                        CPF,
                                        "   ",
                                        null,
                                        null,
                                        null,
                                        TODAY)
                                .instrument())
                .isNull();
    }

    @Test
    void minorWithCompleteGuardian() {
        StudentData data =
                StudentData.of(
                        "Matteo",
                        "2014-05-10",
                        CPF,
                        null,
                        "Maria Silva",
                        "(11) 98765-4321",
                        "Maria@Example.com",
                        TODAY);
        assertThat(data.guardian().isComplete()).isTrue();
        assertThat(data.guardian().phone().digits()).isEqualTo("11987654321");
        assertThat(data.guardian().email().value()).isEqualTo("maria@example.com");
    }

    @Test
    void minorWithoutGuardianIsRefused() {
        DomainException error =
                refused(
                        () ->
                                StudentData.of(
                                        "Matteo",
                                        "2014-05-10",
                                        CPF,
                                        null,
                                        null,
                                        null,
                                        null,
                                        TODAY));
        assertThat(violations(error))
                .containsExactly(violation("guardian", ErrorCode.GUARDIAN_REQUIRED));
    }

    @Test
    void minorWithPartialGuardianIsRefused() {
        DomainException error =
                refused(
                        () ->
                                StudentData.of(
                                        "Matteo",
                                        "2014-05-10",
                                        CPF,
                                        null,
                                        "Maria Silva",
                                        "  ",
                                        null,
                                        TODAY));
        assertThat(violations(error))
                .containsExactly(violation("guardian", ErrorCode.GUARDIAN_REQUIRED));
    }

    @Test
    void eighteenCompleteYearsTodayIsAdultAndGuardianIsOptional() {
        StudentData data = StudentData.of("Ana", "2008-09-21", CPF, null, null, null, null, TODAY);
        assertThat(data.guardian()).isNull();
        assertThatThrownBy(
                        () ->
                                StudentData.of(
                                        "Ana", "2008-09-22", CPF, null, null, null, null, TODAY))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void adultKeepsTheGuardianWhenProvided() {
        StudentData data =
                StudentData.of(
                        "Ana",
                        "2000-01-01",
                        CPF,
                        null,
                        "Maria Silva",
                        "1134567890",
                        "maria@example.com",
                        TODAY);
        assertThat(data.guardian().name().value()).isEqualTo("Maria Silva");
    }

    @Test
    void adultWithOnlyTheGuardianNameIsTolerated() {
        StudentData data =
                StudentData.of("Ana", "2000-01-01", CPF, null, "Maria Silva", null, null, TODAY);
        assertThat(data.guardian().isComplete()).isFalse();
        assertThat(data.guardian().phone()).isNull();
        assertThat(data.guardian().email()).isNull();
    }

    @Test
    void guardianDataWithoutNameIsInvalid() {
        DomainException error =
                refused(
                        () ->
                                StudentData.of(
                                        "Ana",
                                        "2000-01-01",
                                        CPF,
                                        null,
                                        null,
                                        "11987654321",
                                        null,
                                        TODAY));
        assertThat(violations(error))
                .containsExactly(violation("guardian.name", ErrorCode.INVALID_NAME));
    }

    @Test
    void accumulatesEveryViolationInOneFailure() {
        DomainException error =
                refused(
                        () ->
                                StudentData.of(
                                        "A",
                                        "31/02/2020",
                                        "111.111.111-11",
                                        "x".repeat(61),
                                        "B",
                                        "123",
                                        "no-at-sign",
                                        TODAY));
        assertThat(violations(error))
                .containsExactlyInAnyOrder(
                        violation("name", ErrorCode.INVALID_NAME),
                        violation("birthDate", ErrorCode.INVALID_BIRTH_DATE),
                        violation("cpf", ErrorCode.INVALID_CPF),
                        violation("instrument", ErrorCode.INVALID_INSTRUMENT),
                        violation("guardian.name", ErrorCode.INVALID_NAME),
                        violation("guardian.phone", ErrorCode.INVALID_PHONE),
                        violation("guardian.email", ErrorCode.INVALID_EMAIL));
    }

    @Test
    void controlCharactersAreReportedAsInvalidText() {
        DomainException error =
                refused(
                        () ->
                                StudentData.of(
                                        "Ana\u0000",
                                        "1990-03-04",
                                        CPF,
                                        "Pi\tano",
                                        null,
                                        null,
                                        null,
                                        TODAY));
        assertThat(violations(error))
                .containsExactlyInAnyOrder(
                        violation("name", ErrorCode.INVALID_TEXT),
                        violation("instrument", ErrorCode.INVALID_TEXT));
    }

    @Test
    void invalidBirthDateSkipsTheGuardianRequirement() {
        DomainException error =
                refused(
                        () ->
                                StudentData.of(
                                        "Ana", "2099-01-01", CPF, null, null, null, null, TODAY));
        assertThat(violations(error))
                .containsExactly(violation("birthDate", ErrorCode.INVALID_BIRTH_DATE));
    }

    @Test
    void cpfIsRequiredOnRegistration() {
        for (String blank : new String[] {null, "", "  "}) {
            DomainException error =
                    refused(
                            () ->
                                    StudentData.of(
                                            "Ana",
                                            "1990-03-04",
                                            blank,
                                            null,
                                            null,
                                            null,
                                            null,
                                            TODAY));
            assertThat(violations(error)).containsExactly(violation("cpf", ErrorCode.INVALID_CPF));
        }
    }

    @Test
    void blankCpfKeepsTheCurrentOneOnEdit() {
        for (String blank : new String[] {null, "", "   "}) {
            StudentData data =
                    StudentData.forEdit("Ana", "1990-03-04", blank, null, null, null, null, TODAY);
            assertThat(data.cpf()).isNull();
        }
        assertThat(
                        StudentData.forEdit("Ana", "1990-03-04", CPF, null, null, null, null, TODAY)
                                .cpf()
                                .digits())
                .isEqualTo("52998224725");
    }

    @Test
    void invalidCpfIsStillRefusedOnEdit() {
        DomainException error =
                refused(
                        () ->
                                StudentData.forEdit(
                                        "Ana", "1990-03-04", "123", null, null, null, null, TODAY));
        assertThat(violations(error)).containsExactly(violation("cpf", ErrorCode.INVALID_CPF));
    }

    @Test
    void failureDetailsNeverRepeatTheReceivedValues() {
        DomainException error =
                refused(
                        () ->
                                StudentData.of(
                                        "Zé\u0000Segredo",
                                        "31/02/2020",
                                        "111.111.111-11",
                                        null,
                                        "Resp Segredo",
                                        "(11) 9",
                                        "segredo",
                                        TODAY));
        String text = error.details() + " " + error.getMessage();
        assertThat(text)
                .doesNotContain("Segredo")
                .doesNotContain("111")
                .doesNotContain("31/02")
                .doesNotContain("(11)");
    }

    @Test
    void textRevealsNoPersonalData() {
        StudentData data = adult();
        assertThat(data.toString()).isEqualTo("StudentData");
    }
}
