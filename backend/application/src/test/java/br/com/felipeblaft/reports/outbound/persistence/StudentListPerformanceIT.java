package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.StudentSearch;
import br.com.felipeblaft.reports.domain.registry.StudentStatusFilter;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Meta de desempenho (plan.md, R-22): com 1.000 alunos, listar e buscar por nome, 30 execuções, p95
 * de até 300 ms. A busca é em memória sobre a lista entregue pelo repositório.
 */
class StudentListPerformanceIT extends RegistryPersistenceBase {

    private static final int STUDENTS = 1_000;
    private static final int EXECUTIONS = 30;
    private static final long LIMIT_MS = 300;

    private static final String[] FIRST_NAMES = {
        "Ana", "Álvaro", "Beatriz", "Caio", "Débora", "Érica", "Fábio", "Gabriela", "Hélio", "Íris"
    };
    private static final String[] LAST_NAMES = {
        "Souza",
        "Lima",
        "Prado",
        "Alves",
        "Ferreira",
        "Dias",
        "Martins",
        "Rocha",
        "Nunes",
        "Tavares"
    };

    private Teacher clara;

    private void insert1000Students() {
        clara = teacher("Clara Souza", true);
        Instant created = now();
        List<Object[]> studentRows = new ArrayList<>();
        List<Object[]> assignmentRows = new ArrayList<>();
        for (int i = 0; i < STUDENTS; i++) {
            UUID id = UUID.randomUUID();
            String name =
                    FIRST_NAMES[i % FIRST_NAMES.length]
                            + " "
                            + LAST_NAMES[(i / FIRST_NAMES.length) % LAST_NAMES.length]
                            + " "
                            + i;
            byte[] encrypted = ("encrypted-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            studentRows.add(
                    new Object[] {
                        id,
                        name,
                        java.sql.Date.valueOf("1990-01-15"),
                        encrypted,
                        String.format("%064x", i),
                        "Piano",
                        i % 10 != 0,
                        Timestamp.from(created),
                        Timestamp.from(created)
                    });
            assignmentRows.add(
                    new Object[] {UUID.randomUUID(), clara.id(), id, Timestamp.from(created)});
        }
        jdbc.batchUpdate(
                "insert into student (id, name, birth_date, cpf_encrypted, cpf_hash, instrument,"
                        + " active, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                studentRows);
        jdbc.batchUpdate(
                "insert into teacher_student_assignment (id, teacher_id, student_id, created_at)"
                        + " values (?, ?, ?, ?)",
                assignmentRows);
    }

    private static long percentile95(List<Long> millis) {
        List<Long> sorted = new ArrayList<>(millis);
        Collections.sort(sorted);
        int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return sorted.get(index);
    }

    private static long millisOf(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    @Test
    void listAndSearchByNameWithThousandStudentsKeepP95Under300ms() {
        insert1000Students();
        StudentScope teacherScope = StudentScope.ofTeacher(clara.id());
        // aquecimento (conexões, JIT e plano do banco) fora da medição
        students.list(StudentScope.all(), StudentStatusFilter.ACTIVE);
        students.list(teacherScope, StudentStatusFilter.ACTIVE);

        List<Long> listing = new ArrayList<>();
        List<Long> search = new ArrayList<>();
        List<Long> teacherListing = new ArrayList<>();
        for (int i = 0; i < EXECUTIONS; i++) {
            AtomicReference<List<Student>> listed = new AtomicReference<>();
            listing.add(
                    millisOf(
                            () ->
                                    listed.set(
                                            students.list(
                                                    StudentScope.all(),
                                                    StudentStatusFilter.ACTIVE))));
            assertThat(listed.get()).hasSize(900);

            AtomicReference<List<Student>> found = new AtomicReference<>();
            search.add(
                    millisOf(
                            () -> {
                                List<Student> loaded =
                                        students.list(
                                                StudentScope.all(), StudentStatusFilter.ACTIVE);
                                String term = StudentSearch.normalize("alvaro souza");
                                found.set(
                                        loaded.stream()
                                                .filter(
                                                        s ->
                                                                StudentSearch.normalize(s.name())
                                                                        .contains(term))
                                                .toList());
                            }));
            assertThat(found.get()).isNotEmpty();

            teacherListing.add(
                    millisOf(
                            () ->
                                    listed.set(
                                            students.list(teacherScope, StudentStatusFilter.ALL))));
            assertThat(listed.get()).hasSize(STUDENTS);
        }

        assertThat(percentile95(listing))
                .as("p95 da lista (ms): %s", listing)
                .isLessThanOrEqualTo(LIMIT_MS);
        assertThat(percentile95(search))
                .as("p95 da busca (ms): %s", search)
                .isLessThanOrEqualTo(LIMIT_MS);
        assertThat(percentile95(teacherListing))
                .as("p95 da lista do professor (ms): %s", teacherListing)
                .isLessThanOrEqualTo(LIMIT_MS);
    }
}
