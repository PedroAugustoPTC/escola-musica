package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.domain.audit.AuditChain;
import br.com.felipeblaft.reports.support.MigrationTestSupport;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Feature 007, {@code V17__privacy_retention_operations.sql} (data-model.md §1 a §5): migração
 * aditiva sobre um banco já migrado até a {@code V16} com linhas de autoria, aluno, trabalho e
 * chamada de IA. Nenhuma linha some; o canal da chamada de IA é herdado do trabalho; os hashes da
 * trilha antiga batem com {@link AuditChain}; os {@code CHECK} novos valem e os gatilhos de só
 * acréscimo continuam recusando {@code UPDATE} e {@code TRUNCATE}.
 */
class V17MigrationIT {

    private static final String SCHEMA = "migration_v17_test";
    private static final UUID TEACHER = UUID.randomUUID();
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID JOB = UUID.randomUUID();

    private final MigrationTestSupport db = new MigrationTestSupport(SCHEMA);

    @BeforeEach
    void startAtV16() throws Exception {
        db.dropSchema();
        db.flyway("16").migrate();
        db.update(
                "insert into teacher (id, name, email, active, created_at, updated_at) values ('"
                        + TEACHER
                        + "', 'Clara', 'clara@example.test', true, now(), now())");
        db.update(
                "insert into student (id, name, birth_date, cpf_encrypted, cpf_hash, active,"
                        + " created_at, updated_at) values ('"
                        + STUDENT
                        + "', 'Matteo', '2012-05-10', '\\x00', 'hash-matteo', true, now(), now())");
        db.update(
                "insert into processing_job (id, type, teacher_id, student_id, request_channel,"
                        + " medium, parameters, status, step, attempts, delay_notice_sent, created_at)"
                        + " values ('"
                        + JOB
                        + "', 'LESSON_REGISTRATION', '"
                        + TEACHER
                        + "', '"
                        + STUDENT
                        + "', 'TELEGRAM', 'AUDIO', '{}'::jsonb, 'COMPLETED', 'STRUCTURING', 0, false, now())");
        db.update(
                "insert into ai_call (id, job_id, task, duration_ms, success, created_at) values ('"
                        + UUID.randomUUID()
                        + "', '"
                        + JOB
                        + "', 'TRANSCRIPTION', 10, true, now())");
        insertAuthorship("ADMIN_SIGN_IN", "2026-01-01T10:00:00.000001Z", "");
        insertAuthorship("STUDENT_REGISTER", "2026-01-01T10:00:00.000002Z", "");
        db.update(
                "insert into authorship_record (id, occurred_at, origin, operation, target_type, result,"
                        + " correlation_id, filter_report_type, filter_period_start, filter_period_end,"
                        + " page_number) values ('"
                        + UUID.randomUUID()
                        + "', '2026-01-02T08:30:00.5Z', 'WEB', 'SCHOOL_HISTORY_QUERY', null, 'SUCCESS',"
                        + " 'req-abc', 'LESSON', '2026-01-01', '2026-01-31', 2)");
    }

    @AfterEach
    void cleanSchema() throws SQLException {
        db.dropSchema();
    }

    private void insertAuthorship(String operation, String occurredAt, String extra)
            throws SQLException {
        db.update(
                "insert into authorship_record (id, occurred_at, author_id, author_role, origin,"
                        + " operation, target_type, target_id, result, correlation_id) values ('"
                        + UUID.randomUUID()
                        + "', '"
                        + occurredAt
                        + "', '"
                        + TEACHER
                        + "', 'ADMINISTRATOR', 'WEB', '"
                        + operation
                        + "', 'STUDENT', '"
                        + STUDENT
                        + "', 'SUCCESS', '"
                        + UUID.randomUUID()
                        + "')");
    }

    @Test
    void migrationKeepsEveryExistingRow() throws Exception {
        db.flyway(null).migrate();

        assertThat(db.single("select count(*) from authorship_record")).isEqualTo("3");
        assertThat(db.single("select count(*) from student")).isEqualTo("1");
        assertThat(db.single("select count(*) from ai_call")).isEqualTo("1");
    }

    @Test
    void aiCallChannelIsFilledFromTheProcessingJob() throws Exception {
        db.flyway(null).migrate();

        assertThat(db.single("select channel from ai_call")).isEqualTo("TELEGRAM");
        assertThat(
                        db.single(
                                "select is_nullable from information_schema.columns where table_schema = '"
                                        + SCHEMA
                                        + "' and table_name = 'ai_call' and column_name = 'channel'"))
                .isEqualTo("NO");
    }

    @Test
    void oldAuthorshipRowsGetTheCanonicalChainComputedByTheCore() throws Exception {
        db.flyway(null).migrate();

        List<AuditChain.Link> links = new ArrayList<>();
        try (Connection connection = db.connection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "select sequence, occurred_at, author_id, author_role, origin, operation,"
                                        + " target_type, target_id, result, error_code, origin_hash,"
                                        + " correlation_id, filter_teacher_id, filter_report_type,"
                                        + " filter_period_start, filter_period_end, filter_operation,"
                                        + " filter_actor_id, filter_target_id, page_number,"
                                        + " document_version, item_count, previous_hash, record_hash"
                                        + " from authorship_record order by sequence")) {
            while (rows.next()) {
                links.add(
                        new AuditChain.Link(
                                new AuditChain.Fields(
                                        rows.getLong("sequence"),
                                        rows.getTimestamp("occurred_at").toInstant(),
                                        uuid(rows, "author_id"),
                                        rows.getString("author_role"),
                                        rows.getString("origin"),
                                        rows.getString("operation"),
                                        rows.getString("target_type"),
                                        uuid(rows, "target_id"),
                                        rows.getString("result"),
                                        rows.getString("error_code"),
                                        rows.getString("origin_hash"),
                                        rows.getString("correlation_id"),
                                        uuid(rows, "filter_teacher_id"),
                                        rows.getString("filter_report_type"),
                                        date(rows, "filter_period_start"),
                                        date(rows, "filter_period_end"),
                                        rows.getString("filter_operation"),
                                        uuid(rows, "filter_actor_id"),
                                        uuid(rows, "filter_target_id"),
                                        integer(rows, "page_number"),
                                        integer(rows, "document_version"),
                                        integer(rows, "item_count")),
                                rows.getString("previous_hash"),
                                rows.getString("record_hash")));
            }
        }

        assertThat(links).hasSize(3);
        assertThat(links).extracting(l -> l.fields().sequence()).containsExactly(1L, 2L, 3L);
        assertThat(links.getFirst().previousHash()).isEqualTo(AuditChain.GENESIS_HASH);
        assertThat(AuditChain.firstBroken(links, AuditChain.GENESIS_HASH)).isEmpty();
    }

    private static UUID uuid(ResultSet rows, String column) throws SQLException {
        String value = rows.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private static LocalDate date(ResultSet rows, String column) throws SQLException {
        java.sql.Date value = rows.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static Integer integer(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    @Test
    void newAppendsGetTheNextSequenceFromTheDatabase() throws Exception {
        db.flyway(null).migrate();

        db.update(
                "insert into authorship_record (id, occurred_at, origin, operation, result, correlation_id,"
                        + " previous_hash, record_hash) values ('"
                        + UUID.randomUUID()
                        + "', now(), 'RETENTION', 'RETENTION_RUN', 'SUCCESS', 'req-9', '"
                        + "0".repeat(64)
                        + "', '"
                        + "1".repeat(64)
                        + "')");

        assertThat(db.single("select max(sequence) from authorship_record")).isEqualTo("4");
    }

    @Test
    void studentCheckAcceptsOnlyAFullyErasedOrAFullyFilledStudent() throws Exception {
        db.flyway(null).migrate();

        assertThat(
                        db.update(
                                "update student set erased_at = now(), name = null, birth_date = null,"
                                        + " cpf_encrypted = null, cpf_hash = null, guardian_name = null,"
                                        + " guardian_phone = null, guardian_email = null, instrument = null,"
                                        + " active = false where id = '"
                                        + STUDENT
                                        + "'"))
                .isEqualTo(1);
        UUID other = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                db.update(
                                        "insert into student (id, name, birth_date, cpf_encrypted, cpf_hash,"
                                                + " active, created_at, updated_at, erased_at) values ('"
                                                + other
                                                + "', 'Ainda aqui', '2012-05-10', '\\x00', 'h2', true, now(),"
                                                + " now(), now())"))
                .hasMessageContaining("ck_student_erased");
        assertThatThrownBy(
                        () ->
                                db.update(
                                        "insert into student (id, name, birth_date, cpf_encrypted, cpf_hash,"
                                                + " active, created_at, updated_at) values ('"
                                                + UUID.randomUUID()
                                                + "', null, '2012-05-10', '\\x00', 'h3', true, now(), now())"))
                .hasMessageContaining("ck_student_erased");
    }

    @Test
    void semesterGeneralInformationCanOnlyBeNullWhenAnonymized() throws Exception {
        db.flyway(null).migrate();
        UUID report = UUID.randomUUID();
        String insert =
                "insert into semester_report (id, teacher_id, student_id, period_key, period_start,"
                        + " period_end, considered_lesson_count, open_report_count, general_information,"
                        + " content, status, current_version, origin_channel, created_at, updated_at,"
                        + " processing_time_ms, lock_version, anonymized_at) values ('"
                        + report
                        + "', '"
                        + TEACHER
                        + "', '"
                        + STUDENT
                        + "', '2026-1', '2026-02-01', '2026-06-30', 12, 0, null, '{}'::jsonb, 'AWAITING_REVIEW',"
                        + " 1, 'WEB', now(), now(), 0, 0, ";

        assertThatThrownBy(() -> db.update(insert + "null)"))
                .hasMessageContaining("ck_semester_report_general_information");
        assertThat(db.update(insert + "now())")).isEqualTo(1);
    }

    @Test
    void auditTrailStaysAppendOnly() throws Exception {
        db.flyway(null).migrate();

        assertThatThrownBy(() -> db.update("update authorship_record set result = 'FAILURE'"))
                .hasMessageContaining("somente de acréscimo");
        assertThatThrownBy(() -> db.update("delete from authorship_record"))
                .hasMessageContaining("somente de acréscimo");
        assertThatThrownBy(() -> db.execute("truncate authorship_record"))
                .hasMessageContaining("somente de acréscimo");
        assertThat(db.single("select count(*) from authorship_record")).isEqualTo("3");
    }

    @Test
    void newOriginsTargetsAndDetailChecksValidate() throws Exception {
        db.flyway(null).migrate();
        String head =
                "insert into authorship_record (id, occurred_at, origin, operation, target_type, result,"
                        + " correlation_id, previous_hash, record_hash";
        String hashes = "'" + "0".repeat(64) + "', '" + "2".repeat(64) + "'";

        assertThat(
                        db.update(
                                head
                                        + ", item_count) values ('"
                                        + UUID.randomUUID()
                                        + "', now(), 'RETENTION', 'RETENTION_RUN', 'RETENTION_RUN',"
                                        + " 'SUCCESS', 'req-1', "
                                        + hashes
                                        + ", 40)"))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                db.update(
                                        head
                                                + ", item_count) values ('"
                                                + UUID.randomUUID()
                                                + "', now(), 'WEB', 'STUDENT_UPDATE', 'STUDENT', 'SUCCESS',"
                                                + " 'req-1', "
                                                + hashes
                                                + ", 5)"))
                .hasMessageContaining("ck_authorship_record_item_count_operation");
        assertThatThrownBy(
                        () ->
                                db.update(
                                        head
                                                + ") values ('"
                                                + UUID.randomUUID()
                                                + "', now(), 'OTHER_ORIGIN', 'STUDENT_UPDATE', null, 'SUCCESS',"
                                                + " 'req-1', "
                                                + hashes
                                                + ")"))
                .hasMessageContaining("ck_authorship_record_origin");
    }

    @Test
    void newTablesExistWithTheirUniquenessRules() throws Exception {
        db.flyway(null).migrate();

        UUID term = UUID.randomUUID();
        db.update(
                "insert into consent_term_version (id, number, published_on, text, requires_new_consent,"
                        + " created_by, created_at) values ('"
                        + term
                        + "', 1, '2026-11-01', 'Termo institucional', false, '"
                        + TEACHER
                        + "', now())");
        assertThatThrownBy(
                        () ->
                                db.update(
                                        "insert into consent_term_version (id, number, published_on, text,"
                                                + " requires_new_consent, created_by, created_at) values ('"
                                                + UUID.randomUUID()
                                                + "', 1, '2026-11-02', 'Outro', true, '"
                                                + TEACHER
                                                + "', now())"))
                .hasMessageContaining("uk_consent_term_version_number");

        db.update(
                "insert into operational_alert (id, type, component, detail_code, status, started_at,"
                        + " last_seen_at, occurrences) values ('"
                        + UUID.randomUUID()
                        + "', 'STORAGE_FAILED', 'pdf-storage', 'WRITE_FAILED', 'ACTIVE', now(), now(), 1)");
        assertThatThrownBy(
                        () ->
                                db.update(
                                        "insert into operational_alert (id, type, component, detail_code,"
                                                + " status, started_at, last_seen_at, occurrences) values ('"
                                                + UUID.randomUUID()
                                                + "', 'STORAGE_FAILED', 'pdf-storage', 'WRITE_FAILED',"
                                                + " 'ACTIVE', now(), now(), 1)"))
                .hasMessageContaining("uk_operational_alert_active");
    }
}
