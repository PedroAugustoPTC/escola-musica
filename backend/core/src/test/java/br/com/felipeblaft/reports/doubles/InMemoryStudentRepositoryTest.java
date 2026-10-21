package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryStudentRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");

    private final InMemoryAssignmentRepository assignments = new InMemoryAssignmentRepository();
    private final InMemoryStudentRepository students = new InMemoryStudentRepository(assignments);
    private final UUID clara = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();

    private Student student(String name, String cpfHash) {
        return Student.create(
                name,
                LocalDate.of(2012, 1, 1),
                new byte[] {1},
                cpfHash,
                "Responsável",
                "Violão",
                NOW);
    }

    private Student saved(String name, String cpfHash) {
        Student student = student(name, cpfHash);
        students.save(student);
        return student;
    }

    @Test
    void findVisibleInTheWholeSchoolScopeReturnsAnyStudent() {
        Student student = saved("Matteo Mafra", "cpf:1");

        assertThat(students.findVisible(StudentScope.all(), student.id())).contains(student);
    }

    @Test
    void findVisibleForTheTeacherReturnsOnlyTheirOwnStudent() {
        Student own = saved("Matteo Mafra", "cpf:1");
        assignments.assign(clara, own.id());

        assertThat(students.findVisible(StudentScope.ofTeacher(clara), own.id())).contains(own);
    }

    @Test
    void anotherTeachersStudentIsIndistinguishableFromANonexistentOne() {
        Student alheio = saved("Matteo Mafra", "cpf:1");
        assignments.assign(bruno, alheio.id());

        assertThat(students.findVisible(StudentScope.ofTeacher(clara), alheio.id())).isEmpty();
        assertThat(students.findVisible(StudentScope.ofTeacher(clara), UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    void studentWithoutTeacherIsInvisibleToTeachersButVisibleToTheSchool() {
        Student orphan = saved("Olavo Torrente", "cpf:2");

        assertThat(students.findVisible(StudentScope.ofTeacher(clara), orphan.id())).isEmpty();
        assertThat(students.findVisible(StudentScope.all(), orphan.id())).contains(orphan);
    }

    @Test
    void findVisibleOfNonexistentIdIsEmptyInAnyScope() {
        assertThat(students.findVisible(StudentScope.all(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void inactiveStudentRemainsVisibleToItsTeacher() {
        Student student = saved("Matteo Mafra", "cpf:1");
        student.inactivate(NOW);
        assignments.assign(clara, student.id());

        assertThat(students.findVisible(StudentScope.ofTeacher(clara), student.id()))
                .contains(student);
    }

    @Test
    void listWholeSchoolFiltersBySituationAndSortsByNameIgnoringAccentsAndCase() {
        Student helena = saved("Hélena Ávila", "cpf:1");
        Student bruna = saved("bruna Lima", "cpf:2");
        Student ana = saved("Ana Souza", "cpf:3");
        Student inactive = saved("Zeca Prado", "cpf:4");
        inactive.inactivate(NOW);

        assertThat(students.list(StudentScope.all(), StudentStatusFilter.ACTIVE))
                .containsExactly(ana, bruna, helena);
        assertThat(students.list(StudentScope.all(), StudentStatusFilter.INACTIVE))
                .containsExactly(inactive);
        assertThat(students.list(StudentScope.all(), StudentStatusFilter.ALL))
                .containsExactly(ana, bruna, helena, inactive);
    }

    @Test
    void listForTheTeacherReturnsOnlyTheirStudents() {
        Student own = saved("Matteo Mafra", "cpf:1");
        Student other = saved("Olavo Torrente", "cpf:2");
        Student shared = saved("Miguel Castro", "cpf:3");
        assignments.assign(clara, own.id());
        assignments.assign(bruno, other.id());
        assignments.assign(clara, shared.id());
        assignments.assign(bruno, shared.id());

        assertThat(students.list(StudentScope.ofTeacher(clara), StudentStatusFilter.ALL))
                .containsExactly(own, shared);
        assertThat(students.list(StudentScope.ofTeacher(bruno), StudentStatusFilter.ALL))
                .containsExactly(shared, other);
    }

    @Test
    void listForTeacherWithoutStudentsIsEmpty() {
        saved("Matteo Mafra", "cpf:1");

        assertThat(students.list(StudentScope.ofTeacher(clara), StudentStatusFilter.ALL)).isEmpty();
    }

    @Test
    void insertIfAbsentInsertsANewCpf() {
        Student student = student("Matteo Mafra", "cpf:1");

        assertThat(students.insertIfAbsent(student)).isTrue();

        assertThat(students.searchById(student.id())).contains(student);
    }

    @Test
    void insertIfAbsentRefusesDuplicateCpfWithoutOverwriting() {
        Student first = saved("Matteo Mafra", "cpf:1");
        Student duplicate = student("Outro Nome", "cpf:1");

        assertThat(students.insertIfAbsent(duplicate)).isFalse();

        assertThat(students.all()).containsExactly(first);
        assertThat(students.searchById(duplicate.id())).isEmpty();
    }

    @Test
    void searchByCpfHashFindsOnlyTheMatchingStudent() {
        Student student = saved("Matteo Mafra", "cpf:1");
        saved("Olavo Torrente", "cpf:2");

        assertThat(students.searchByCpfHash("cpf:1")).contains(student);
        assertThat(students.searchByCpfHash("cpf:9")).isEmpty();
    }
}
