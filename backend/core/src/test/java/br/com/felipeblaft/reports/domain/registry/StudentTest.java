package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StudentTest {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");

    private Student student(LocalDate birth) {
        return Student.create(
                "Matteo Mafra", birth, new byte[] {1, 2}, "hash", "Resp", "Piano", T0);
    }

    @Test
    void completeAgeAtDate() {
        Student student = student(LocalDate.of(2014, 5, 10));
        assertThat(student.ageAt(LocalDate.of(2026, 5, 9))).isEqualTo(11);
        assertThat(student.ageAt(LocalDate.of(2026, 5, 10))).isEqualTo(12);
        assertThat(Student.age(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 1, 1))).isZero();
    }

    @Test
    void underEighteenRequiresGuardian() {
        LocalDate today = LocalDate.of(2026, 9, 21);
        assertThat(Student.requiresGuardian(LocalDate.of(2008, 9, 22), today)).isTrue();
        assertThat(Student.requiresGuardian(LocalDate.of(2008, 9, 21), today)).isFalse();
        assertThat(Student.requiresGuardian(LocalDate.of(1990, 1, 1), today)).isFalse();
    }

    @Test
    void newActiveStudentWithProtectedCpf() {
        Student student = student(LocalDate.of(2014, 5, 10));
        assertThat(student.active()).isTrue();
        assertThat(student.cpfHash()).isEqualTo("hash");
        byte[] encrypted = student.encryptedCpf();
        encrypted[0] = 9;
        assertThat(student.encryptedCpf()).containsExactly(1, 2);
        assertThat(student.toString()).doesNotContain("Matteo");
        assertThat(student.createdAt()).isEqualTo(T0);
        assertThat(student.updatedAt()).isEqualTo(T0);
    }

    @Test
    void updatesRegistryOnlyWhenChanged() {
        Student student = student(LocalDate.of(2014, 5, 10));
        Instant after = T0.plusSeconds(60);
        assertThat(
                        student.updateRegistry(
                                "Matteo Mafra", LocalDate.of(2014, 5, 10), "Resp", "Piano", after))
                .isFalse();
        assertThat(
                        student.updateRegistry(
                                "Matteo Mafra", LocalDate.of(2014, 5, 10), "Resp", "Violão", after))
                .isTrue();
        assertThat(student.instrument()).isEqualTo("Violão");
        assertThat(student.updatedAt()).isEqualTo(after);
        assertThat(
                        student.updateRegistry(
                                "Matteo M.", LocalDate.of(2014, 5, 10), "Resp", "Violão", after))
                .isTrue();
        assertThat(
                        student.updateRegistry(
                                "Matteo M.", LocalDate.of(2014, 5, 11), "Resp", "Violão", after))
                .isTrue();
        assertThat(
                        student.updateRegistry(
                                "Matteo M.", LocalDate.of(2014, 5, 11), null, "Violão", after))
                .isTrue();
        assertThat(student.name()).isEqualTo("Matteo M.");
        assertThat(student.birthDate()).isEqualTo(LocalDate.of(2014, 5, 11));
        assertThat(student.guardianName()).isNull();
    }
}
