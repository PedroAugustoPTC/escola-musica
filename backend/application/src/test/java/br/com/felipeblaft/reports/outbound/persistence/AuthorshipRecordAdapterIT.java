package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Registro de autoria com Postgres real (FR-040, R-10): somente acréscimo (gatilhos), gravação na
 * mesma transação do caso de uso ({@code record}) ou em transação independente ({@code
 * recordIndependent}), resumo da origem só em eventos de autenticação e nenhuma coluna que aceite
 * CPF.
 */
class AuthorshipRecordAdapterIT extends IntegrationBase {

    private static final String HASH = "a".repeat(64);
    private static final String CPF = "52998224725";

    @Autowired AuthorshipRecorderPort recorder;
    @Autowired PlatformTransactionManager transactions;

    private AuthorshipRecord success(String operation) {
        return new AuthorshipRecord(
                UUID.randomUUID(),
                Instant.parse("2026-09-21T13:00:00Z"),
                UUID.randomUUID(),
                Role.ADMINISTRATOR,
                ActionOrigin.WEB,
                operation,
                AuthorshipTargetType.TEACHER,
                UUID.randomUUID(),
                AuthorshipResult.SUCCESS,
                null,
                null,
                "req-ab12cd");
    }

    private AuthorshipRecord authenticationFailure(UUID id) {
        return new AuthorshipRecord(
                id,
                Instant.parse("2026-09-21T13:00:00Z"),
                null,
                null,
                ActionOrigin.WEB,
                "ADMIN_SIGN_IN",
                null,
                null,
                AuthorshipResult.FAILURE,
                "UNKNOWN_EMAIL",
                HASH,
                "req-ab12cd");
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from authorship_record", Integer.class);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactions);
    }

    @Test
    void recordPersistsAllTheFields() {
        AuthorshipRecord record = success("TEACHER_REGISTER");

        recorder.record(record);

        Map<String, Object> row =
                jdbc.queryForMap("select * from authorship_record where id = ?", record.id());
        assertThat(row)
                .containsEntry("author_id", record.authorId())
                .containsEntry("author_role", "ADMINISTRATOR")
                .containsEntry("origin", "WEB")
                .containsEntry("operation", "TEACHER_REGISTER")
                .containsEntry("target_type", "TEACHER")
                .containsEntry("target_id", record.targetId())
                .containsEntry("result", "SUCCESS")
                .containsEntry("correlation_id", "req-ab12cd");
        assertThat(row.get("error_code")).isNull();
        assertThat(row.get("origin_hash")).isNull();
    }

    @Test
    void authenticationEventKeepsTheOriginHashAndAnUnknownAuthor() {
        UUID id = UUID.randomUUID();

        recorder.recordIndependent(authenticationFailure(id));

        Map<String, Object> row =
                jdbc.queryForMap("select * from authorship_record where id = ?", id);
        assertThat(row)
                .containsEntry("origin_hash", HASH)
                .containsEntry("error_code", "UNKNOWN_EMAIL")
                .containsEntry("result", "FAILURE");
        assertThat(row.get("author_id")).isNull();
        assertThat(row.get("author_role")).isNull();
    }

    @Test
    void updateDeleteAndTruncateFail() {
        AuthorshipRecord record = success("TEACHER_REGISTER");
        recorder.record(record);

        assertThatThrownBy(() -> jdbc.update("update authorship_record set result = 'FAILURE'"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("somente de acréscimo");
        assertThatThrownBy(() -> jdbc.update("delete from authorship_record"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("somente de acréscimo");
        assertThatThrownBy(() -> jdbc.execute("truncate table authorship_record"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("somente de acréscimo");
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select result from authorship_record", String.class))
                .isEqualTo("SUCCESS");
    }

    @Test
    void recordJoinsTheCallerTransactionAndRollsBackWithIt() {
        transaction()
                .executeWithoutResult(
                        status -> {
                            recorder.record(success("TEACHER_REGISTER"));
                            status.setRollbackOnly();
                        });

        assertThat(count()).isZero();
    }

    @Test
    void recordCommitsTogetherWithTheCallerTransaction() {
        transaction().executeWithoutResult(status -> recorder.record(success("TEACHER_UPDATE")));

        assertThat(count()).isEqualTo(1);
    }

    @Test
    void recordFailureUndoesTheWholeOperation() {
        AuthorshipRecord record = success("TEACHER_REGISTER");

        assertThatThrownBy(
                        () ->
                                transaction()
                                        .executeWithoutResult(
                                                status -> {
                                                    jdbc.update(
                                                            "insert into access_attempt (id, scope,"
                                                                    + " channel, source_hash,"
                                                                    + " failed_attempts,"
                                                                    + " updated_at) values"
                                                                    + " (gen_random_uuid(),"
                                                                    + " 'ADMIN_SIGN_IN', 'WEB',"
                                                                    + " 'h', 1, now())");
                                                    recorder.record(record);
                                                    recorder.record(record);
                                                }))
                .isInstanceOf(DataAccessException.class);

        assertThat(count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from access_attempt", Integer.class))
                .isZero();
    }

    @Test
    void recordIndependentSurvivesTheRollbackOfTheCallerTransaction() {
        transaction()
                .executeWithoutResult(
                        status -> {
                            recorder.recordIndependent(authenticationFailure(UUID.randomUUID()));
                            status.setRollbackOnly();
                        });

        assertThat(count()).isEqualTo(1);
    }

    @Test
    void recordIndependentFailureNeverMasksTheOriginalRefusal() {
        AuthorshipRecord record = authenticationFailure(UUID.randomUUID());
        recorder.recordIndependent(record);

        assertThatCode(() -> recorder.recordIndependent(record)).doesNotThrowAnyException();

        assertThat(count()).isEqualTo(1);
    }

    @Test
    void originHashOnlyInAuthenticationEvents() {
        AuthorshipRecord notAuthentication =
                new AuthorshipRecord(
                        UUID.randomUUID(),
                        Instant.parse("2026-09-21T13:00:00Z"),
                        UUID.randomUUID(),
                        Role.TEACHER,
                        ActionOrigin.WEB,
                        "STUDENT_REGISTER",
                        AuthorshipTargetType.STUDENT,
                        UUID.randomUUID(),
                        AuthorshipResult.SUCCESS,
                        null,
                        HASH,
                        "req-ab12cd");

        assertThatThrownBy(() -> recorder.record(notAuthentication))
                .isInstanceOf(DataAccessException.class);

        assertThat(count()).isZero();
    }

    @Test
    void noColumnAcceptsACpf() {
        List<String> columns =
                jdbc.queryForList(
                        "select column_name from information_schema.columns where table_name ="
                                + " 'authorship_record'",
                        String.class);
        assertThat(columns)
                .noneMatch(name -> name.contains("cpf") || name.contains("name"))
                .doesNotContain("email", "phone", "password", "code");

        String insert =
                "insert into authorship_record (id, occurred_at, origin, operation, result,"
                        + " error_code, origin_hash, correlation_id) values (gen_random_uuid(),"
                        + " now(), 'WEB', ?, 'FAILURE', ?, ?, ?)";
        String ok = "TEACHER_REGISTER";
        assertThatThrownBy(() -> jdbc.update(insert, CPF, null, null, "req-ab12cd"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, ok, CPF, null, "req-ab12cd"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, ok, "CPF_UNAVAILABLE", CPF, "req-ab12cd"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, ok, null, null, CPF))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, ok, null, null, "req-" + CPF))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, ok, null, null, "mary@school.example.com"))
                .isInstanceOf(DataAccessException.class);
        assertThat(count()).isZero();
    }

    @Test
    void checksRejectInvalidEnumerationsAndAnAuthorWithoutRole() {
        String insert =
                "insert into authorship_record (id, occurred_at, author_id, author_role, origin,"
                        + " operation, result, correlation_id) values (gen_random_uuid(), now(),"
                        + " ?, ?, ?, 'TEACHER_UPDATE', ?, '123')";
        UUID author = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update(insert, author, null, "WEB", "SUCCESS"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, author, "TEACHER", "EMAIL", "SUCCESS"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, author, "TEACHER", "WEB", "MAYBE"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, author, "STUDENT", "WEB", "SUCCESS"))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.update(insert, null, null, "SYSTEM_STARTUP", "SUCCESS")).isEqualTo(1);
    }
}
