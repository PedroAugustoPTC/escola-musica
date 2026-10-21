package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Situação e contato do responsável (feature 005). */
class StudentSituationTest {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant T1 = Instant.parse("2026-09-02T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-03T12:00:00Z");

    private Student student() {
        return Student.create(
                "Matteo Mafra",
                LocalDate.of(2014, 5, 10),
                new byte[] {1, 2},
                "hash",
                "Rita Mafra",
                "11987654321",
                "rita@example.com",
                "Piano",
                T0);
    }

    @Test
    void guardianContactIsKept() {
        Student student = student();
        assertThat(student.guardianName()).isEqualTo("Rita Mafra");
        assertThat(student.guardianPhone()).isEqualTo("11987654321");
        assertThat(student.guardianEmail()).isEqualTo("rita@example.com");
    }

    @Test
    void existingStudentWithoutContactStaysValid() {
        Student legacy =
                new Student(
                        java.util.UUID.randomUUID(),
                        "Ana",
                        LocalDate.of(2000, 1, 1),
                        new byte[] {1},
                        "h",
                        null,
                        null,
                        true,
                        T0,
                        T0);
        assertThat(legacy.guardianPhone()).isNull();
        assertThat(legacy.guardianEmail()).isNull();
    }

    @Test
    void inactivateAndReactivateChangeOnlyTheSituation() {
        Student student = student();
        assertThat(student.inactivate(T1)).isTrue();
        assertThat(student.active()).isFalse();
        assertThat(student.updatedAt()).isEqualTo(T1);
        assertThat(student.inactivate(T2)).isFalse();
        assertThat(student.updatedAt()).isEqualTo(T1);

        assertThat(student.reactivate(T2)).isTrue();
        assertThat(student.active()).isTrue();
        assertThat(student.updatedAt()).isEqualTo(T2);
        assertThat(student.reactivate(T2)).isFalse();
        assertThat(student.name()).isEqualTo("Matteo Mafra");
    }

    @Test
    void toStringRevealsNoPersonalData() {
        assertThat(student().toString())
                .doesNotContain("Matteo")
                .doesNotContain("Rita")
                .doesNotContain("11987654321")
                .doesNotContain("rita@example.com");
    }
}
