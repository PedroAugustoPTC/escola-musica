package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.support.MigrationTestSupport;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Feature 003, R-03 (verificação da V8), FR-015: a V8 troca as CHAVES e o vocabulário do JSON do
 * fio guardado em {@code channel_action} ({@code payload} e {@code compensation}), sem tocar nos
 * textos (campos {@code texto} e {@code legenda}); e ABORTA se houver valor fora do vocabulário em
 * coluna de conjunto fechado, em vez de deixar dado legível só pela metade.
 */
class MigrationV8JsonKeysAndUnknownValueIT {

    private static final String SCHEMA = "migration_v8_json_keys_test";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MigrationTestSupport db = new MigrationTestSupport(SCHEMA);

    @BeforeEach
    void migrateToV7() throws Exception {
        db.dropSchema();
        db.flyway("7").migrate();
    }

    @AfterEach
    void cleanSchema() throws SQLException {
        db.dropSchema();
    }

    private void action(String id, String type, String state, String payload, String compensation)
            throws SQLException {
        db.update(
                "insert into channel_action (id, batch_id, channel, origin, update_id, chat_id, type, state,"
                        + " payload, compensation, deadline_at, created_at) values ('"
                        + id
                        + "', 'u1', 'TELEGRAM', 'PROATIVA', null, 7, '"
                        + type
                        + "', '"
                        + state
                        + "', '"
                        + payload
                        + "'::jsonb, "
                        + (compensation == null ? "null" : "'" + compensation + "'::jsonb")
                        + ", now() + interval '1 day', now())");
    }

    @Test
    void payloadAndCompensationGetTheNewKeysAndVocabularyAndKeepTheTexts() throws Exception {
        String text = "Olá, \\\"Clara\\\"; aprovar → sim";
        String payload =
                "{\"acaoId\":\"u1-0\",\"tipo\":\"MENSAGEM\",\"chatId\":7,\"texto\":\""
                        + text
                        + "\",\"teclado\":{\"linhas\":[[{\"rotulo\":\"Aprovar\",\"dados\":\"v1:aprov:3\"},"
                        + "{\"rotulo\":\"Cancelar\",\"dados\":\"v1:canc:\"}]]}}";
        String compensation =
                "{\"tipo\":\"DOCUMENTO\",\"chatId\":7,\"documentoId\":\"abc\",\"nomeArquivo\":\"relatorio.pdf\","
                        + "\"tipoConteudo\":\"application/pdf\",\"legenda\":\"Segue o relatório\"}";
        action("u1-0", "MENSAGEM", "PENDENTE", payload, compensation);

        db.flyway("8").migrate();

        JsonNode migratedPayload =
                JSON.readTree(db.single("select payload::text from channel_action"));
        assertThat(migratedPayload.has("acaoId")).isFalse();
        assertThat(migratedPayload.get("actionId").asString()).isEqualTo("u1-0");
        assertThat(migratedPayload.get("type").asString()).isEqualTo("MESSAGE");
        assertThat(migratedPayload.get("text").asString())
                .isEqualTo("Olá, \"Clara\"; aprovar → sim");
        JsonNode buttons = migratedPayload.get("keyboard").get("rows").get(0);
        assertThat(buttons.get(0).get("label").asString()).isEqualTo("Aprovar");
        assertThat(buttons.get(0).get("data").asString()).isEqualTo("v1:approve:3");
        assertThat(buttons.get(1).get("data").asString()).isEqualTo("v1:cancel:");

        JsonNode migratedCompensation =
                JSON.readTree(db.single("select compensation::text from channel_action"));
        assertThat(migratedCompensation.get("type").asString()).isEqualTo("DOCUMENT");
        assertThat(migratedCompensation.get("documentId").asString()).isEqualTo("abc");
        assertThat(migratedCompensation.get("fileName").asString()).isEqualTo("relatorio.pdf");
        assertThat(migratedCompensation.get("contentType").asString()).isEqualTo("application/pdf");
        assertThat(migratedCompensation.get("caption").asString()).isEqualTo("Segue o relatório");

        String oldKeys =
                db.single(
                        "select count(*) from channel_action a, jsonb_each(a.payload) e"
                                + " where e.key in ('acaoId','tipo','texto','teclado','legenda')");
        assertThat(oldKeys).isEqualTo("0");
        assertThat(db.single("select state from channel_action")).isEqualTo("PENDING");
    }

    @Test
    void anUnknownStatusMakesTheMigrationAbortAndLeavesTheSchemaAtV7() throws Exception {
        UUID teacher = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        UUID lesson = UUID.randomUUID();
        db.update(
                "insert into teacher (id, name, email, active, created_at, updated_at) values ('"
                        + teacher
                        + "', 'Clara', 'clara@example.test', true, now(), now())");
        db.update(
                "insert into student (id, name, birth_date, cpf_encrypted, cpf_hash, active, created_at, updated_at)"
                        + " values ('"
                        + student
                        + "', 'Matteo', '2012-05-10', '\\x00', 'hash-x', true, now(), now())");
        db.update(
                "insert into lesson (id, teacher_id, student_id, lesson_date, created_at) values ('"
                        + lesson
                        + "', '"
                        + teacher
                        + "', '"
                        + student
                        + "', '2026-09-01', now())");
        db.update(
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
                        + "', 'texto', '{}', '[]', 0, 'STATUS_INEXISTENTE', 1, 'WEB', now(), now(), 0, 0)");

        assertThatThrownBy(() -> db.flyway("8").migrate())
                .hasMessageContaining("V8")
                .hasMessageContaining("lesson_report.status");

        // a migração roda numa transação: nada foi trocado e a V8 não ficou registrada
        assertThat(db.single("select status from lesson_report")).isEqualTo("STATUS_INEXISTENTE");
        assertThat(db.single("select max(version) from flyway_schema_history where success"))
                .isEqualTo("7");
    }
}
