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

/**
 * Feature 003, R-03 (verificação 6): depois da V7 as proteções continuam ativas. Tenta violar o
 * índice único dos relatórios de aula em aberto, o índice por expressão JSON da consolidação
 * semestral (que agora lê a chave {@code periodKey}: com a chave antiga ele deixaria de proteger
 * sem acusar erro) e os CHECKs, e exige erro de integridade com o nome NOVO da proteção. Os valores
 * de enumeração seguem em português até a V8.
 */
class MigrationV7ProtectionsIT {

    private static final String SCHEMA = "migration_v7_protections_test";

    private final MigrationTestSupport db = new MigrationTestSupport(SCHEMA);
    private JsonNode map;
    private final UUID teacher = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();

    @BeforeEach
    void migrate() throws Exception {
        db.dropSchema();
        db.flyway("7").migrate();
        map = MigrationTestSupport.renameMap();
        db.update(
                "insert into teacher (id, name, email, active, created_at, updated_at)"
                        + " values ('"
                        + teacher
                        + "', 'Clara', 'clara@exemplo.test', true, now(), now())");
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

    private String newName(String group, String oldName) {
        return map.get(group).get(oldName).asString();
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

    private int lessonReport(UUID lesson, String status, int rounds) throws SQLException {
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
                        + "', 'texto', '{}', '[]', "
                        + rounds
                        + ", '"
                        + status
                        + "', 1, 'WEB', now(), now(), 0, 0)");
    }

    private int consolidationJob(String parameters, String status) throws SQLException {
        return db.update(
                "insert into processing_job (id, type, teacher_id, student_id, request_channel, medium,"
                        + " parameters, status, step, attempts, delay_notice_sent, created_at) values ('"
                        + UUID.randomUUID()
                        + "', 'CONSOLIDACAO_SEMESTRAL', '"
                        + teacher
                        + "', '"
                        + student
                        + "', 'WEB', 'TEXTO', '"
                        + parameters
                        + "'::jsonb, '"
                        + status
                        + "', 'ENVIANDO', 0, false, now())");
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
    void secondOpenLessonReportForTheSamePairIsRefused() throws Exception {
        assertThat(lessonReport(lesson("2026-09-01"), "AGUARDANDO_RESPOSTAS", 0)).isEqualTo(1);

        String index = newName("indexes", "uk_relatorio_aula_em_aberto");
        assertThat(index).isEqualTo("uk_lesson_report_open");
        assertThatThrownBy(() -> lessonReport(lesson("2026-09-02"), "AGUARDANDO_CONFERENCIA", 0))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(index);
        // aprovado não conta como em aberto
        assertThat(lessonReport(lesson("2026-09-03"), "APROVADO", 0)).isEqualTo(1);
    }

    @Test
    void secondRunningConsolidationForTheSameTriplePeriodKeyIsRefused() throws Exception {
        assertThat(consolidationJob("{\"periodKey\": \"2026-1\"}", "PENDENTE")).isEqualTo(1);

        String index = newName("indexes", "uk_trabalho_consolidacao_em_curso");
        assertThatThrownBy(() -> consolidationJob("{\"periodKey\": \"2026-1\"}", "EM_EXECUCAO"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(index);
        // outro período é permitido; consolidação já concluída não conta
        assertThat(consolidationJob("{\"periodKey\": \"2026-2\"}", "PENDENTE")).isEqualTo(1);
        assertThat(consolidationJob("{\"periodKey\": \"2026-1\"}", "CONCLUIDO")).isEqualTo(1);
    }

    @Test
    void checkConstraintsAreStillActiveWithTheNewNames() throws Exception {
        UUID lesson = lesson("2026-09-01");
        assertThatThrownBy(() -> lessonReport(lesson, "APROVADO", 3))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(newName("constraints", "ck_relatorio_aula_rodadas"));
        assertThatThrownBy(() -> channelAction("INEXISTENTE", "MENSAGEM", "PROATIVA", "null"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(newName("constraints", "ck_acao_canal_estado"));
        assertThatThrownBy(() -> channelAction("PENDENTE", "INEXISTENTE", "PROATIVA", "null"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(newName("constraints", "ck_acao_canal_tipo"));
        assertThatThrownBy(() -> channelAction("PENDENTE", "MENSAGEM", "INEXISTENTE", "null"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(newName("constraints", "ck_acao_canal_origem"));
        assertThatThrownBy(() -> channelAction("PENDENTE", "MENSAGEM", "RESPOSTA_UPDATE", "null"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(newName("constraints", "ck_acao_canal_resposta_update"));
        // e o caminho válido continua aceito
        assertThat(channelAction("PENDENTE", "MENSAGEM", "RESPOSTA_UPDATE", "11")).isEqualTo(1);
    }
}
