package br.com.felipeblaft.reports.doubles;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryTeacherRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-21T13:00:00Z");

    private final InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();

    private Teacher saved(String name, String email, boolean active) {
        Teacher teacher = Teacher.create(name, email, active, NOW);
        teachers.save(teacher);
        return teacher;
    }

    @Test
    void listsInAlphabeticalOrderIgnoringAccentsAndCase() {
        Teacher zelia = saved("Zélia Prado", "zelia@school.test", true);
        Teacher alice = saved("alice Rocha", "alice@school.test", true);
        Teacher ines = saved("Inês Alves", "ines@school.test", true);

        PageResult<Teacher> page = teachers.list(null, null, PageRequest.first());

        assertThat(page.items()).containsExactly(alice, ines, zelia);
        assertThat(page.total()).isEqualTo(3);
    }

    @Test
    void filtersBySituationWhereNullMeansAll() {
        Teacher active = saved("Ana Souza", "ana@school.test", true);
        Teacher inactive = saved("Bia Lima", "bia@school.test", false);

        assertThat(teachers.list(null, true, PageRequest.first()).items()).containsExactly(active);
        assertThat(teachers.list(null, false, PageRequest.first()).items())
                .containsExactly(inactive);
        assertThat(teachers.list(null, null, PageRequest.first()).items())
                .containsExactly(active, inactive);
    }

    @Test
    void searchesByNameWithoutAccentsNorCase() {
        Teacher ines = saved("Inês Alves", "contato@school.test", true);
        saved("Bruno Castro", "bruno@school.test", true);

        assertThat(teachers.list("INES", null, PageRequest.first()).items()).containsExactly(ines);
        assertThat(teachers.list("  alves ", null, PageRequest.first()).items())
                .containsExactly(ines);
    }

    @Test
    void searchesByEmail() {
        saved("Inês Alves", "contato@school.test", true);
        Teacher bruno = saved("Bruno Castro", "bruno.castro@school.test", true);

        assertThat(teachers.list("castro@SCHOOL", null, PageRequest.first()).items())
                .containsExactly(bruno);
    }

    @Test
    void blankSearchMeansNoFilterAndNoMatchIsEmpty() {
        saved("Inês Alves", "ines@school.test", true);

        assertThat(teachers.list("   ", null, PageRequest.first()).items()).hasSize(1);
        PageResult<Teacher> none = teachers.list("missing", null, PageRequest.first());
        assertThat(none.items()).isEmpty();
        assertThat(none.total()).isZero();
    }

    @Test
    void paginatesAfterFilteringKeepingTheTotal() {
        for (int i = 1; i <= 5; i++) {
            saved("Professor " + i, "p" + i + "@school.test", true);
        }

        PageResult<Teacher> second = teachers.list(null, null, new PageRequest(1, 2));

        assertThat(second.items())
                .extracting(Teacher::name)
                .containsExactly("Professor 3", "Professor 4");
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
        assertThat(second.total()).isEqualTo(5);
    }

    @Test
    void insertIfAbsentInsertsANewEmail() {
        Teacher teacher = Teacher.create("Ana Souza", "ana@school.test", true, NOW);

        assertThat(teachers.insertIfAbsent(teacher)).isTrue();

        assertThat(teachers.searchByEmail("ana@school.test")).contains(teacher);
    }

    @Test
    void insertIfAbsentRefusesDuplicateEmailWithoutOverwriting() {
        Teacher first = saved("Ana Souza", "ana@school.test", true);
        Teacher duplicate = Teacher.create("Outra Ana", "ANA@school.test ", true, NOW);

        assertThat(teachers.insertIfAbsent(duplicate)).isFalse();

        assertThat(teachers.all()).containsExactly(first);
    }
}
