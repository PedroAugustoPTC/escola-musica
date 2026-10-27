package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StudentApplyEditTest {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    private static Student student() {
        return Student.create(
                "Matteo Mafra",
                LocalDate.of(2014, 5, 10),
                new byte[] {1, 2},
                "hash",
                "Maria Silva",
                "11987654321",
                "maria@example.com",
                "Piano",
                T0);
    }

    private static StudentData data(String name, String birth, String instrument, String guardian) {
        return StudentData.forEdit(
                name,
                birth,
                null,
                instrument,
                guardian,
                guardian == null ? null : "11987654321",
                guardian == null ? null : "maria@example.com",
                TODAY);
    }

    @Test
    void sameDataChangesNothing() {
        Student student = student();
        boolean changed =
                student.applyEdit(
                        data("Matteo Mafra", "2014-05-10", "Piano", "Maria Silva"), null, T1);
        assertThat(changed).isFalse();
        assertThat(student.updatedAt()).isEqualTo(T0);
    }

    @Test
    void changesEveryRegistryField() {
        Student student = student();
        boolean changed =
                student.applyEdit(
                        data("Matteo M.", "2014-05-11", "Violão", "Joana Lima"), null, T1);
        assertThat(changed).isTrue();
        assertThat(student.name()).isEqualTo("Matteo M.");
        assertThat(student.birthDate()).isEqualTo(LocalDate.of(2014, 5, 11));
        assertThat(student.instrument()).isEqualTo("Violão");
        assertThat(student.guardianName()).isEqualTo("Joana Lima");
        assertThat(student.guardianPhone()).isEqualTo("11987654321");
        assertThat(student.guardianEmail()).isEqualTo("maria@example.com");
        assertThat(student.updatedAt()).isEqualTo(T1);
        assertThat(student.cpfHash()).isEqualTo("hash");
    }

    @Test
    void removingGuardianAndInstrumentClearsThem() {
        Student student = student();
        boolean changed =
                student.applyEdit(data("Matteo Mafra", "1990-05-10", null, null), null, T1);
        assertThat(changed).isTrue();
        assertThat(student.instrument()).isNull();
        assertThat(student.guardianName()).isNull();
        assertThat(student.guardianPhone()).isNull();
        assertThat(student.guardianEmail()).isNull();
    }

    @Test
    void guardianContactChangeAloneIsAChange() {
        Student student = student();
        StudentData edit =
                StudentData.forEdit(
                        "Matteo Mafra",
                        "2014-05-10",
                        null,
                        "Piano",
                        "Maria Silva",
                        "1134567890",
                        "maria@example.com",
                        TODAY);
        assertThat(student.applyEdit(edit, null, T1)).isTrue();
        assertThat(student.guardianPhone()).isEqualTo("1134567890");
        StudentData other =
                StudentData.forEdit(
                        "Matteo Mafra",
                        "2014-05-10",
                        null,
                        "Piano",
                        "Maria Silva",
                        "1134567890",
                        "other@example.com",
                        TODAY);
        assertThat(student.applyEdit(other, null, T1)).isTrue();
        assertThat(student.guardianEmail()).isEqualTo("other@example.com");
    }

    @Test
    void replacesTheProtectedCpfOnlyWhenGiven() {
        Student student = student();
        ProtectedCpf replacement = new ProtectedCpf(new byte[] {9, 9}, "new-hash");
        boolean changed =
                student.applyEdit(
                        data("Matteo Mafra", "2014-05-10", "Piano", "Maria Silva"),
                        replacement,
                        T1);
        assertThat(changed).isTrue();
        assertThat(student.cpfHash()).isEqualTo("new-hash");
        assertThat(student.encryptedCpf()).containsExactly(9, 9);
        assertThat(student.updatedAt()).isEqualTo(T1);
    }

    @Test
    void protectedCpfCopiesTheBytesAndHidesThem() {
        byte[] bytes = {1, 2, 3};
        ProtectedCpf protectedCpf = new ProtectedCpf(bytes, "h");
        bytes[0] = 7;
        assertThat(protectedCpf.encrypted()).containsExactly(1, 2, 3);
        protectedCpf.encrypted()[0] = 8;
        assertThat(protectedCpf.encrypted()).containsExactly(1, 2, 3);
        assertThat(protectedCpf.hash()).isEqualTo("h");
        assertThat(protectedCpf.toString()).isEqualTo("ProtectedCpf");
    }
}
