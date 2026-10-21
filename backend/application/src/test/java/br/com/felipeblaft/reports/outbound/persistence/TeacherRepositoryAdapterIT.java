package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.common.PageRequest;
import br.com.felipeblaft.reports.domain.common.PageResult;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Professores no PostgreSQL real (feature 005): lista com busca e filtro, inserção sem abortar a
 * transação (e-mail concorrente: um insere, o outro recebe duplicidade) e inativar anula o código
 * de acesso (FR-016).
 */
class TeacherRepositoryAdapterIT extends IntegrationBase {

    private final Instant now = Instant.parse("2026-09-21T13:00:00Z");

    @Autowired TeacherRepository teachers;

    private Teacher saved(String name, String email, boolean active) {
        Teacher teacher = Teacher.create(name, email, active, now);
        teachers.save(teacher);
        return teacher;
    }

    @Test
    void listIsAlphabeticalAndSearchesNameOrEmailWithoutAccentsOrCase() {
        saved("Zélia Prado", "zelia@example.test", true);
        saved("Ana Souza", "ana@escola.example", true);
        saved("Álvaro Lima", "alvaro@example.test", false);

        PageResult<Teacher> all = teachers.list(null, null, PageRequest.first());
        assertThat(all.items())
                .extracting(Teacher::name)
                .containsExactly("Álvaro Lima", "Ana Souza", "Zélia Prado");
        assertThat(all.total()).isEqualTo(3);

        assertThat(teachers.list("ZELIA", null, PageRequest.first()).items())
                .extracting(Teacher::name)
                .containsExactly("Zélia Prado");
        assertThat(teachers.list("alvaro", null, PageRequest.first()).items())
                .extracting(Teacher::name)
                .containsExactly("Álvaro Lima");
        assertThat(teachers.list("@escola", null, PageRequest.first()).items())
                .extracting(Teacher::name)
                .containsExactly("Ana Souza");
        assertThat(teachers.list("nobody", null, PageRequest.first()).total()).isZero();
    }

    @Test
    void listFiltersBySituationAndPaginates() {
        for (int i = 0; i < 5; i++) {
            saved("Prof " + i, "p" + i + "@example.test", true);
        }
        saved("Inativo", "off@example.test", false);

        assertThat(teachers.list(null, true, PageRequest.first()).total()).isEqualTo(5);
        assertThat(teachers.list(null, false, PageRequest.first()).items())
                .extracting(Teacher::name)
                .containsExactly("Inativo");
        PageResult<Teacher> page = teachers.list(null, true, new PageRequest(1, 2));
        assertThat(page.items()).extracting(Teacher::name).containsExactly("Prof 2", "Prof 3");
        assertThat(page.total()).isEqualTo(5);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
    }

    @Test
    void insertIfAbsentInsertsOnceAndReportsDuplicateWithoutThrowing() {
        Teacher first = Teacher.create("Clara", "clara@example.test", true, now);
        Teacher duplicate = Teacher.create("Outra", "clara@example.test", true, now);

        assertThat(teachers.insertIfAbsent(first)).isTrue();
        assertThat(teachers.insertIfAbsent(duplicate)).isFalse();

        assertThat(teachers.searchByEmail("clara@example.test").orElseThrow().name())
                .isEqualTo("Clara");
        assertThat(jdbc.queryForObject("select count(*) from teacher", Integer.class)).isEqualTo(1);
    }

    @Test
    void insertIfAbsentKeepsTheAccessCodeFields() {
        Teacher teacher = Teacher.create("Clara", "clara@example.test", true, now);
        teacher.replaceCode("f".repeat(64), now, Duration.ofDays(7));

        assertThat(teachers.insertIfAbsent(teacher)).isTrue();

        Teacher read = teachers.searchById(teacher.id()).orElseThrow();
        assertThat(read.accessCodeHash()).isEqualTo("f".repeat(64));
        assertThat(read.accessCodeExpiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
        assertThat(read.accessCodeRenewedAt()).isEqualTo(now);
    }

    @Test
    void concurrentInsertsWithTheSameEmailInsertExactlyOne() throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Teacher teacher = Teacher.create("Prof " + i, "race@example.test", true, now);
            tasks.add(() -> teachers.insertIfAbsent(teacher));
        }

        List<Boolean> results = ConcurrentRunner.runTogether(tasks);

        assertThat(results.stream().filter(Boolean::booleanValue)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from teacher", Integer.class)).isEqualTo(1);
    }

    @Test
    void inactivatingNullsTheAccessCodeColumns() {
        Teacher teacher = Teacher.create("Clara", "clara@example.test", true, now);
        teacher.replaceCode("a".repeat(64), now, Duration.ofDays(7));
        teachers.save(teacher);

        teacher.inactivate(now.plusSeconds(5));
        teachers.save(teacher);

        assertCodeColumnsAreNull(teacher.id());
        assertThat(teachers.searchById(teacher.id()).orElseThrow().active()).isFalse();
    }

    @Test
    void persistenceNeverKeepsACodeOfAnInactiveTeacherEvenIfTheObjectStillHasOne() {
        Teacher inconsistent =
                new Teacher(
                        UUID.randomUUID(),
                        "Clara",
                        "clara@example.test",
                        false,
                        "b".repeat(64),
                        now.plus(Duration.ofDays(7)),
                        now,
                        now,
                        now);

        teachers.save(inconsistent);

        assertCodeColumnsAreNull(inconsistent.id());
    }

    @Test
    void insertIfAbsentOfAnInactiveTeacherAlsoStoresNoCode() {
        Teacher inconsistent =
                new Teacher(
                        UUID.randomUUID(),
                        "Clara",
                        "clara@example.test",
                        false,
                        "c".repeat(64),
                        now.plus(Duration.ofDays(7)),
                        now,
                        now,
                        now);

        assertThat(teachers.insertIfAbsent(inconsistent)).isTrue();

        assertCodeColumnsAreNull(inconsistent.id());
    }

    @Test
    void reactivatedTeacherStartsWithoutCodeUntilANewOneIsIssued() {
        Teacher teacher = Teacher.create("Clara", "clara@example.test", true, now);
        teacher.replaceCode("d".repeat(64), now, Duration.ofDays(7));
        teachers.save(teacher);
        teacher.inactivate(now.plusSeconds(1));
        teachers.save(teacher);

        teacher.reactivate(now.plusSeconds(2));
        teachers.save(teacher);

        Teacher read = teachers.searchById(teacher.id()).orElseThrow();
        assertThat(read.active()).isTrue();
        assertThat(read.accessCodeHash()).isNull();
        assertThat(teachers.searchByAccessCodeHash("d".repeat(64))).isEmpty();
    }

    private void assertCodeColumnsAreNull(UUID id) {
        assertThat(
                        jdbc.queryForObject(
                                "select access_code_hash is null and access_code_expires_at is null"
                                        + " and access_code_renewed_at is null from teacher where id = ?",
                                Boolean.class,
                                id))
                .isTrue();
    }
}
