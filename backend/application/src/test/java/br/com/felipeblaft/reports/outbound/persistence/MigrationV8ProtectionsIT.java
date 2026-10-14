package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.support.MigrationTestSupport;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Feature 003, R-03 (verificação da V8): depois da V8 as proteções recriadas com os literais novos
 * continuam barrando o que barravam (índices parciais e CHECKs de enumeração), e cada valor novo
 * cabe no {@code varchar(n)} da coluna. Os valores antigos deixam de ser aceitos.
 */
class MigrationV8ProtectionsIT {

    private static final String SCHEMA = "migration_v8_protections_test";

    private final MigrationTestSupport db = new MigrationTestSupport(SCHEMA);
    private final UUID teacher = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();

    @BeforeEach
    void migrate() throws Exception {
        db.dropSchema();
        db.flyway("8").migrate();
        db.update(
                "insert into teacher (id, name, email, active, created_at, updated_at)"
                        + " values ('"
                        + teacher
                        + "', 'Clara', 'clara@example.test', true, now(), now())");
        db.update(
                "insert into student (id, name, birth_date, cpf_encrypted, cpf_hash, active, created_at, updated_at)"
                        + " values ('"
                        + student
                        + "', 'Matteo', '2012-05-10', '\\x00', 'hash-matteo', true, now(), now())");
    }

    @AfterEach
    void cleanSchema() throws SQLException {
        db.dropSchema();
    }

    private UUID lesson(String date) throws SQLException {
        UUID id = UUID.randomUUID();
        db.update(
                "insert into lesson (id, teacher_id, student_id, lesson_date, created_at) values ('"
                        + id
                        + "', '"
                        + teacher
                        + "', '"
                        + student
                        + "', '"
                        + date
                        + "', now())");
        return id;
    }

    private int lessonReport(UUID lesson, String status) throws SQLException {
        return db.update(
                "insert into lesson_report (id, lesson_id, teacher_id, student_id, transcription, content,"
                        + " pending_questions, question_rounds, status, current_version, origin_channel,"
                        + " created_at, updated_at, processing_time_ms, lock_version) values ('"
                        + UUID.randomUUID()
                        + "', '"
                        + lesson
                        + "', '"
                        + teacher
                        + "', '"
                        + student
                        + "', 'texto', '{}', '[]', 0, '"
                        + status
                        + "', 1, 'WEB', now(), now(), 0, 0)");
    }

    private int job(String type, String parameters, String status) throws SQLException {
        return db.update(
                "insert into processing_job (id, type, teacher_id, student_id, request_channel, medium,"
                        + " parameters, status, step, attempts, delay_notice_sent, created_at) values ('"
                        + UUID.randomUUID()
                        + "', '"
                        + type
                        + "', '"
                        + teacher
                        + "', '"
                        + student
                        + "', 'WEB', 'TEXT', '"
                        + parameters
                        + "'::jsonb, '"
                        + status
                        + "', 'UPLOADING', 0, false, now())");
    }

    private int channelAction(String state, String type, String origin, String updateId)
            throws SQLException {
        return db.update(
                "insert into channel_action (id, batch_id, channel, origin, update_id, chat_id, type, state,"
                        + " deadline_at, created_at) values ('"
                        + UUID.randomUUID()
                        + "', 'u1', 'TELEGRAM', '"
                        + origin
                        + "', "
                        + updateId
                        + ", 7, '"
                        + type
                        + "', '"
                        + state
                        + "', now() + interval '1 day', now())");
    }

    @Test
    void secondOpenLessonReportForTheSamePairIsRefusedWithTheNewStatuses() throws Exception {
        assertThat(lessonReport(lesson("2026-09-01"), "AWAITING_ANSWERS")).isEqualTo(1);

        assertThatThrownBy(() -> lessonReport(lesson("2026-09-02"), "AWAITING_REVIEW"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_lesson_report_open");
        // aprovado não conta como em aberto
        assertThat(lessonReport(lesson("2026-09-03"), "APPROVED")).isEqualTo(1);
    }

    @Test
    void secondRunningJobsOfTheSameTripleAreRefused() throws Exception {
        assertThat(job("SEMESTER_CONSOLIDATION", "{\"periodKey\": \"2026-1\"}", "PENDING"))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                job(
                                        "SEMESTER_CONSOLIDATION",
                                        "{\"periodKey\": \"2026-1\"}",
                                        "RUNNING"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_processing_job_semester_consolidation_running");
        assertThat(job("SEMESTER_CONSOLIDATION", "{\"periodKey\": \"2026-2\"}", "PENDING"))
                .isEqualTo(1);
        assertThat(job("SEMESTER_CONSOLIDATION", "{\"periodKey\": \"2026-1\"}", "COMPLETED"))
                .isEqualTo(1);

        assertThat(job("LESSON_REGISTRATION", "{}", "PENDING")).isEqualTo(1);
        assertThatThrownBy(() -> job("LESSON_REGISTRATION", "{}", "RUNNING"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_processing_job_registration_running");
    }

    @Test
    void enumerationChecksAcceptOnlyTheNewVocabulary() throws Exception {
        assertThatThrownBy(() -> channelAction("PENDENTE", "MESSAGE", "PROACTIVE", "null"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_channel_action_state");
        assertThatThrownBy(() -> channelAction("PENDING", "MENSAGEM", "PROACTIVE", "null"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_channel_action_type");
        assertThatThrownBy(() -> channelAction("PENDING", "MESSAGE", "PROATIVA", "null"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_channel_action_origin");
        assertThatThrownBy(() -> channelAction("PENDING", "MESSAGE", "UPDATE_REPLY", "null"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_channel_action_update_reply");
        assertThat(channelAction("PENDING", "MESSAGE", "UPDATE_REPLY", "11")).isEqualTo(1);
        assertThat(channelAction("PERMANENTLY_FAILED", "STEP_MESSAGE", "COMPENSATION", "null"))
                .isEqualTo(1);
    }

    @Test
    void everyNewValueFitsTheColumnItIsStoredIn() throws Exception {
        JsonNode values;
        try (InputStream in = getClass().getResourceAsStream("/db/v8-values.json")) {
            values = JsonMapper.builder().build().readTree(in);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
        for (var column : values.get("columns").properties()) {
            int limit = column.getValue().get("limit").asInt();
            for (var pair : column.getValue().get("pairs").properties()) {
                assertThat(pair.getValue().asString().length())
                        .as("%s: %s", column.getKey(), pair.getValue().asString())
                        .isLessThanOrEqualTo(limit);
            }
        }
    }
}
