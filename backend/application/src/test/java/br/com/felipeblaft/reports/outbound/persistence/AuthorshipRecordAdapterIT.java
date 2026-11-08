package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.access.ActionOrigin;
import br.com.felipeblaft.reports.domain.access.AuthorshipReadDetail;
import br.com.felipeblaft.reports.domain.access.AuthorshipRecord;
import br.com.felipeblaft.reports.domain.access.AuthorshipResult;
import br.com.felipeblaft.reports.domain.access.AuthorshipTargetType;
import br.com.felipeblaft.reports.domain.access.Role;
import br.com.felipeblaft.reports.domain.common.ReportType;
import br.com.felipeblaft.reports.port.AuthorshipRecorderPort;
import br.com.felipeblaft.reports.support.IntegrationBase;
import java.time.Instant;
import java.time.LocalDate;
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

    private AuthorshipRecord read(
            String operation,
            AuthorshipTargetType targetType,
            UUID targetId,
            AuthorshipReadDetail detail) {
        return new AuthorshipRecord(
                UUID.randomUUID(),
                Instant.parse("2026-11-03T13:00:00Z"),
                UUID.randomUUID(),
                Role.ADMINISTRATOR,
                ActionOrigin.WEB,
                operation,
                targetType,
                targetId,
                AuthorshipResult.SUCCESS,
                null,
                null,
                "req-ab12cd",
                detail);
    }

    @Test
    void queryDetailIsPersistedAndReadBack() {
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        AuthorshipReadDetail detail =
                AuthorshipReadDetail.ofQuery(
                        teacherId,
                        ReportType.SEMESTER,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 6, 30),
                        4);
        AuthorshipRecord record =
                read("SCHOOL_HISTORY_QUERY", AuthorshipTargetType.STUDENT, studentId, detail);

        recorder.record(record);

        Map<String, Object> row =
                jdbc.queryForMap("select * from authorship_record where id = ?", record.id());
        assertThat(row)
                .containsEntry("operation", "SCHOOL_HISTORY_QUERY")
                .containsEntry("target_type", "STUDENT")
                .containsEntry("target_id", studentId)
                .containsEntry("filter_teacher_id", teacherId)
                .containsEntry("filter_report_type", "SEMESTER")
                .containsEntry("page_number", 4);
        assertThat(row.get("filter_period_start").toString()).isEqualTo("2026-01-01");
        assertThat(row.get("filter_period_end").toString()).isEqualTo("2026-06-30");
        assertThat(row.get("document_version")).isNull();
    }

    @Test
    void documentDetailIsPersistedForBothReportTargets() {
        for (AuthorshipTargetType target :
                List.of(AuthorshipTargetType.LESSON_REPORT, AuthorshipTargetType.SEMESTER_REPORT)) {
            UUID reportId = UUID.randomUUID();
            AuthorshipRecord record =
                    read(
                            "SCHOOL_REPORT_DOCUMENT_DOWNLOAD",
                            target,
                            reportId,
                            AuthorshipReadDetail.ofDocument(3));

            recorder.record(record);

            Map<String, Object> row =
                    jdbc.queryForMap("select * from authorship_record where id = ?", record.id());
            assertThat(row)
                    .containsEntry("target_type", target.name())
                    .containsEntry("target_id", reportId)
                    .containsEntry("document_version", 3);
            assertThat(row.get("page_number")).isNull();
            assertThat(row.get("filter_report_type")).isNull();
        }
    }

    @Test
    void recordWithoutDetailKeepsTheDetailColumnsNull() {
        AuthorshipRecord record = success("TEACHER_REGISTER");

        recorder.record(record);

        Map<String, Object> row =
                jdbc.queryForMap("select * from authorship_record where id = ?", record.id());
        for (String column :
                List.of(
                        "filter_teacher_id",
                        "filter_report_type",
                        "filter_period_start",
                        "filter_period_end",
                        "page_number",
                        "document_version")) {
            assertThat(row.get(column)).as(column).isNull();
        }
    }

    @Test
    void readRecordWithoutFiltersIsPersistedWithAnEmptyTarget() {
        AuthorshipRecord record =
                read(
                        "SCHOOL_HISTORY_QUERY",
                        null,
                        null,
                        AuthorshipReadDetail.ofQuery(null, null, null, null, 0));

        recorder.record(record);

        Map<String, Object> row =
                jdbc.queryForMap("select * from authorship_record where id = ?", record.id());
        assertThat(row).containsEntry("page_number", 0);
        assertThat(row.get("target_type")).isNull();
        assertThat(row.get("target_id")).isNull();
        assertThat(row.get("filter_teacher_id")).isNull();
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
        // o domínio já recusa; o CHECK do banco continua valendo para quem grava direto
        assertThatThrownBy(() -> authorshipWith("STUDENT_REGISTER", HASH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into authorship_record (id, occurred_at, origin,"
                                                + " operation, result, origin_hash, correlation_id,"
                                                + " previous_hash, record_hash)"
                                                + " values (gen_random_uuid(), now(), 'WEB',"
                                                + " 'STUDENT_REGISTER', 'SUCCESS', ?,"
                                                + " 'req-ab12cd', repeat('0', 64), repeat('1', 64))",
                                        HASH))
                .isInstanceOf(DataAccessException.class);

        assertThat(count()).isZero();
    }

    private static AuthorshipRecord authorshipWith(String operation, String hash) {
        return new AuthorshipRecord(
                UUID.randomUUID(),
                Instant.parse("2026-09-21T13:00:00Z"),
                UUID.randomUUID(),
                Role.TEACHER,
                ActionOrigin.WEB,
                operation,
                AuthorshipTargetType.STUDENT,
                UUID.randomUUID(),
                AuthorshipResult.SUCCESS,
                null,
                hash,
                "req-ab12cd");
    }

    /**
     * O CHECK {@code ck_authorship_record_origin_hash_event} (V10) e o conjunto do domínio ({@link
     * AuthorshipRecord#ORIGIN_HASH_EVENTS}) não podem divergir: falha se um deles mudar sem o
     * outro.
     */
    @Test
    void originHashCheckMatchesTheDomainEventSet() {
        String definition =
                jdbc.queryForObject(
                        "select pg_get_constraintdef(oid) from pg_constraint where conname ="
                                + " 'ck_authorship_record_origin_hash_event'",
                        String.class);
        java.util.Set<String> inTheDatabase = new java.util.TreeSet<>();
        java.util.regex.Matcher quoted =
                java.util.regex.Pattern.compile("'([A-Z][A-Z0-9_]*)'").matcher(definition);
        while (quoted.find()) {
            inTheDatabase.add(quoted.group(1));
        }

        assertThat(inTheDatabase)
                .containsExactlyInAnyOrderElementsOf(AuthorshipRecord.ORIGIN_HASH_EVENTS);
        for (String event : AuthorshipRecord.ORIGIN_HASH_EVENTS) {
            assertThatCode(() -> recorder.record(authorshipWith(event, HASH)))
                    .as(event)
                    .doesNotThrowAnyException();
        }
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
                        + " error_code, origin_hash, correlation_id, previous_hash, record_hash)"
                        + " values (gen_random_uuid(), now(), 'WEB', ?, 'FAILURE', ?, ?, ?,"
                        + " repeat('0', 64), repeat('1', 64))";
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
                        + " operation, result, correlation_id, previous_hash, record_hash)"
                        + " values (gen_random_uuid(), now(), ?, ?, ?, 'TEACHER_UPDATE', ?, '123',"
                        + " repeat('0', 64), repeat('1', 64))";
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
