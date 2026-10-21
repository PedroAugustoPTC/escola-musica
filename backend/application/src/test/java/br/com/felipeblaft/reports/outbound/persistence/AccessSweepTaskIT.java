package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Administrator;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.AdministratorRepository;
import br.com.felipeblaft.reports.port.TeacherRepository;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A varredura diária cobre as duas tabelas de sessão: a do professor e a do administrador. */
class AccessSweepTaskIT extends IntegrationBase {

    @Autowired AccessSweepTask sweep;
    @Autowired TeacherRepository teachers;
    @Autowired AdministratorRepository administrators;

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void removesExpiredSessionsOfBothTablesAndKeepsTheValidOnes() {
        Instant now = clock.now();
        Teacher clara = Teacher.create("Clara", "clara@example.test", true, now);
        teachers.save(clara);
        Administrator marina = AdministratorTestData.active("Marina", "marina@example.test");
        administrators.insertIfAbsent(marina);

        webSession(clara, 1, now.minusSeconds(1));
        webSession(clara, 2, now.plus(Duration.ofHours(12)));
        adminSession(marina, 3, now.minusSeconds(1));
        adminSession(marina, 4, now);
        adminSession(marina, 5, now.plus(Duration.ofHours(2)));

        sweep.scan();

        assertThat(count("web_session")).isEqualTo(1);
        assertThat(count("administrator_session")).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select token_hash from administrator_session", String.class))
                .isEqualTo(AdministratorTestData.tokenHash(5));
    }

    @Test
    void runsWithNothingToRemove() {
        sweep.scan();

        assertThat(count("web_session")).isZero();
        assertThat(count("administrator_session")).isZero();
    }

    private void webSession(Teacher owner, int n, Instant expiresAt) {
        jdbc.update(
                "insert into web_session (id, teacher_id, token_hash, created_at, last_used_at,"
                        + " expires_at) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                owner.id(),
                AdministratorTestData.tokenHash(n),
                Timestamp.from(clock.now()),
                Timestamp.from(clock.now()),
                Timestamp.from(expiresAt));
    }

    private void adminSession(Administrator owner, int n, Instant expiresAt) {
        jdbc.update(
                "insert into administrator_session (id, administrator_id, token_hash, created_at,"
                        + " expires_at) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                owner.id(),
                AdministratorTestData.tokenHash(n),
                Timestamp.from(clock.now()),
                Timestamp.from(expiresAt));
    }
}
