package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.felipeblaft.reports.support.MigrationTestSupport;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Feature 006, {@code V16__authorship_read_detail.sql} (data-model.md §4.1 e §5): as colunas novas
 * do detalhe de leitura existem e são anuláveis, os {@code CHECK} recusam detalhe em operação de
 * outra feature e valores fora da faixa, o alvo aceita os dois tipos de relatório, os gatilhos de
 * somente acréscimo da {@code V10} continuam valendo e os índices parciais de aprovados existem.
 */
class MigrationV16IT {

    private static final String SCHEMA = "migration_v16_test";
    private static final List<String> DETAIL_COLUMNS =
            List.of(
                    "filter_teacher_id",
                    "filter_report_type",
                    "filter_period_start",
                    "filter_period_end",
                    "page_number",
                    "document_version");

    private final MigrationTestSupport db = new MigrationTestSupport(SCHEMA);

    @BeforeEach
    void startAtV15() throws Exception {
        db.dropSchema();
        db.flyway("15").migrate();
    }

    @AfterEach
    void cleanSchema() throws SQLException {
        db.dropSchema();
    }

    private int insert(String operation, String targetType, String detailColumns, String values)
            throws SQLException {
        return db.update(
                "insert into authorship_record (id, occurred_at, origin, operation, target_type,"
                        + " result, correlation_id"
                        + (detailColumns.isEmpty() ? "" : ", " + detailColumns)
                        + ") values ('"
                        + UUID.randomUUID()
                        + "', now(), 'WEB', '"
                        + operation
                        + "', "
                        + (targetType == null ? "null" : "'" + targetType + "'")
                        + ", 'SUCCESS', 'req-1'"
                        + (values.isEmpty() ? "" : ", " + values)
                        + ")");
    }

    @Test
    void newColumnsExistAndAreNullable() throws Exception {
        db.flyway(null).migrate();

        for (String column : DETAIL_COLUMNS) {
            assertThat(
                            db.single(
                                    "select is_nullable from information_schema.columns where"
                                            + " table_schema = '"
                                            + SCHEMA
                                            + "' and table_name = 'authorship_record' and"
                                            + " column_name = '"
                                            + column
                                            + "'"))
                    .as(column)
                    .isEqualTo("YES");
        }
    }

    @Test
    void existingRowsSurviveAndKeepNullDetail() throws Exception {
        insert("TEACHER_REGISTER", "TEACHER", "", "");

        db.flyway(null).migrate();

        assertThat(db.single("select count(*) from authorship_record")).isEqualTo("1");
        assertThat(
                        db.single(
                                "select count(*) from authorship_record where page_number is null"
                                        + " and document_version is null and filter_teacher_id is"
                                        + " null"))
                .isEqualTo("1");
    }

    @Test
    void detailIsAcceptedInTheThreeSchoolReadOperations() throws Exception {
        db.flyway(null).migrate();

        assertThat(
                        insert(
                                "SCHOOL_HISTORY_QUERY",
                                "STUDENT",
                                "filter_teacher_id, filter_report_type, filter_period_start,"
                                        + " filter_period_end, page_number",
                                "'"
                                        + UUID.randomUUID()
                                        + "', 'SEMESTER', '2026-01-01', '2026-06-30', 0"))
                .isEqualTo(1);
        for (String operation :
                List.of("SCHOOL_REPORT_DOCUMENT_OPEN", "SCHOOL_REPORT_DOCUMENT_DOWNLOAD")) {
            assertThat(insert(operation, "LESSON_REPORT", "document_version", "3"))
                    .as(operation)
                    .isEqualTo(1);
        }
    }

    @Test
    void detailInAnotherOperationIsRefused() throws Exception {
        db.flyway(null).migrate();

        assertThatThrownBy(() -> insert("STUDENT_REGISTER", "STUDENT", "page_number", "0"))
                .hasMessageContaining("ck_authorship_record_read_detail_operation");
        assertThatThrownBy(() -> insert("TEACHER_UPDATE", "TEACHER", "document_version", "1"))
                .hasMessageContaining("ck_authorship_record_read_detail_operation");
        assertThatThrownBy(
                        () ->
                                insert(
                                        "TEACHER_LIST",
                                        null,
                                        "filter_teacher_id",
                                        "'" + UUID.randomUUID() + "'"))
                .hasMessageContaining("ck_authorship_record_read_detail_operation");
    }

    @Test
    void reportTypeFilterIsOnlyLessonOrSemester() throws Exception {
        db.flyway(null).migrate();

        assertThatThrownBy(
                        () ->
                                insert(
                                        "SCHOOL_HISTORY_QUERY",
                                        null,
                                        "filter_report_type",
                                        "'ANNUAL'"))
                .hasMessageContaining("ck_authorship_record_filter_report_type");
        for (String type : List.of("LESSON", "SEMESTER")) {
            assertThatCode(
                            () ->
                                    insert(
                                            "SCHOOL_HISTORY_QUERY",
                                            null,
                                            "filter_report_type",
                                            "'" + type + "'"))
                    .as(type)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void pageNumberAndDocumentVersionHaveLowerBounds() throws Exception {
        db.flyway(null).migrate();

        assertThatThrownBy(() -> insert("SCHOOL_HISTORY_QUERY", null, "page_number", "-1"))
                .hasMessageContaining("ck_authorship_record_page_number");
        assertThatThrownBy(
                        () ->
                                insert(
                                        "SCHOOL_REPORT_DOCUMENT_OPEN",
                                        "LESSON_REPORT",
                                        "document_version",
                                        "0"))
                .hasMessageContaining("ck_authorship_record_document_version");
        assertThat(insert("SCHOOL_HISTORY_QUERY", null, "page_number", "0")).isEqualTo(1);
        assertThat(
                        insert(
                                "SCHOOL_REPORT_DOCUMENT_OPEN",
                                "SEMESTER_REPORT",
                                "document_version",
                                "1"))
                .isEqualTo(1);
    }

    @Test
    void targetTypeAcceptsBothReportTypesAndStillRefusesOthers() throws Exception {
        assertThatThrownBy(() -> insert("SCHOOL_REPORT_DOCUMENT_OPEN", "LESSON_REPORT", "", ""))
                .hasMessageContaining("ck_authorship_record_target_type");

        db.flyway(null).migrate();

        assertThat(insert("SCHOOL_REPORT_DOCUMENT_OPEN", "LESSON_REPORT", "", "")).isEqualTo(1);
        assertThat(insert("SCHOOL_REPORT_DOCUMENT_OPEN", "SEMESTER_REPORT", "", ""))
                .isEqualTo(1);
        for (String previous : List.of("TEACHER", "ADMINISTRATOR", "STUDENT", "ASSIGNMENT")) {
            assertThat(insert("TEACHER_LIST", previous, "", "")).as(previous).isEqualTo(1);
        }
        assertThatThrownBy(() -> insert("TEACHER_LIST", "REPORT", "", ""))
                .hasMessageContaining("ck_authorship_record_target_type");
    }

    @Test
    void appendOnlyTriggersStillRefuseUpdateDeleteAndTruncate() throws Exception {
        db.flyway(null).migrate();
        insert("SCHOOL_HISTORY_QUERY", null, "page_number", "0");

        assertThatThrownBy(() -> db.update("update authorship_record set result = 'FAILURE'"))
                .hasMessageContaining("somente de acréscimo");
        assertThatThrownBy(() -> db.update("delete from authorship_record"))
                .hasMessageContaining("somente de acréscimo");
        assertThatThrownBy(() -> db.execute("truncate table authorship_record"))
                .hasMessageContaining("somente de acréscimo");
        assertThat(db.single("select count(*) from authorship_record")).isEqualTo("1");
    }

    @Test
    void partialIndexesOfApprovedReportsExist() throws Exception {
        db.flyway(null).migrate();

        var indexes = db.catalog().indexes();
        for (String name :
                List.of(
                        "lesson_report.ix_lesson_report_approved_student",
                        "lesson_report.ix_lesson_report_approved_teacher",
                        "semester_report.ix_semester_report_approved_student",
                        "semester_report.ix_semester_report_approved_teacher",
                        "semester_report.ix_semester_report_approved_period")) {
            assertThat(indexes).as(name).containsKey(name);
            assertThat(indexes.get(name)).as(name).contains("APPROVED");
        }
    }

    @Test
    void aNewDatabaseMigratesFromScratchUpToAtLeastV16() throws Exception {
        db.dropSchema();

        db.flyway(null).migrate();

        assertThat(Integer.parseInt(db.single("select max(version::int) from flyway_schema_history")))
                .isGreaterThanOrEqualTo(16);
    }
}
