package br.com.felipeblaft.reports.domain.registry;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.StudentSearch.ResultType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudentSearchTest {

    private static Student student(String name) {
        return Student.create(
                name,
                LocalDate.of(2012, 1, 1),
                new byte[] {1},
                "h" + name,
                null,
                null,
                Instant.EPOCH);
    }

    private final Student matteo = student("Matteo Mafra");
    private final Student olavo = student("Olavo Torrente");
    private final Student miguel = student("Miguel de Castro Torrente");
    private final Student helena = student("Hélena Ávila");
    private final List<Student> students = List.of(matteo, olavo, miguel, helena);

    private List<Student> search(String text) {
        return StudentSearch.search(students, text).students();
    }

    @Test
    void emptySearchReturnsAll() {
        StudentSearch.Result result = StudentSearch.search(students, "   ");
        assertThat(result.type()).isEqualTo(ResultType.TODOS);
        assertThat(result.students()).hasSize(4);
        assertThat(StudentSearch.search(students, null).type()).isEqualTo(ResultType.TODOS);
    }

    @Test
    void ignoresAccentsCaseAndSpaces() {
        assertThat(search("HELENA")).containsExactly(helena);
        assertThat(search("avila")).containsExactly(helena);
        assertThat(search("  olavo   TORRENTE ")).containsExactly(olavo);
    }

    @Test
    void acceptsWordPrefixAndSubstring() {
        assertThat(search("torr")).containsExactly(olavo, miguel);
        assertThat(search("miguel torr")).containsExactly(miguel);
        assertThat(search("afra")).containsExactly(matteo);
        assertThat(StudentSearch.search(students, "torr").type()).isEqualTo(ResultType.ENCONTRADOS);
    }

    @Test
    void withoutMatchReturnsAllAsNone() {
        StudentSearch.Result result = StudentSearch.search(students, "Beethoven");
        assertThat(result.type()).isEqualTo(ResultType.NENHUM);
        assertThat(result.students()).hasSize(4);
    }

    @Test
    void exactMatchByFullNameOrFirstName() {
        assertThat(StudentSearch.exactMatch(students, "olavo")).contains(olavo);
        assertThat(StudentSearch.exactMatch(students, "Matteo Mafra")).contains(matteo);
        assertThat(StudentSearch.exactMatch(students, "helena")).contains(helena);
        assertThat(StudentSearch.exactMatch(students, "torrente")).isEmpty();
        assertThat(StudentSearch.exactMatch(students, "o aluno foi bem")).isEmpty();
        assertThat(StudentSearch.exactMatch(students, "")).isEmpty();
    }

    @Test
    void homonymsAreNotExactMatch() {
        Student otherOlavo = student("Olavo Souza");
        assertThat(StudentSearch.exactMatch(List.of(olavo, otherOlavo), "olavo")).isEmpty();
        assertThat(StudentSearch.normalize(null)).isEmpty();
    }
}
