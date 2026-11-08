package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.privacy.RetentionItemType;
import br.com.felipeblaft.reports.domain.privacy.RetentionRunStatus;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.DocumentStoragePort;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import br.com.felipeblaft.reports.usecase.privacy.RunRetentionUseCase;
import jakarta.servlet.http.Cookie;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Retenção contra o Postgres real (SC-003): só o vencido é tratado; o aprovado vigente, seu PDF e o
 * semestral oficial ficam idênticos; a segunda execução não trata nada; a trilha registra a
 * execução.
 */
class RetentionIT extends SchoolHistoryBase {

    @Autowired RunRetentionUseCase retention;
    @Autowired DocumentStoragePort storage;

    private Cookie clara;
    private java.time.Instant start;
    private UUID lessonOld;
    private UUID lessonCancelled;
    private UUID lessonOpen;
    private UUID semester;
    private Map<String, Object> lessonBefore;
    private String generalInformationBefore;
    private String semesterBodyBefore;
    private String semesterPdfHashBefore;

    @BeforeEach
    void prepare() throws Exception {
        Teacher teacher = scenario.clara();
        Student student = scenario.student(teacher, "Matteo Mafra", "Piano");
        lessonOld =
                scenario.approvedLesson(teacher, student, LocalDate.of(2026, 3, 10), "Sopro").id();
        lessonCancelled =
                scenario.approvedLesson(teacher, student, LocalDate.of(2026, 3, 11), "Ritmo").id();
        lessonOpen =
                scenario.approvedLesson(teacher, student, LocalDate.of(2026, 3, 12), "Postura")
                        .id();
        clara = signInTeacher(ScenarioIT.CODE_CLARA);
        semester = approvedSemester(clara, student.id(), "2026-1");

        revertLessonTo(lessonOpen, "AWAITING_REVIEW");
        revertLessonTo(lessonCancelled, "CANCELLED");
        jdbc.update(
                "update lesson_report set cancelled_at = ? where id = ?",
                Timestamp.from(clock.now()),
                lessonCancelled);
        for (UUID report : List.of(lessonOld, lessonCancelled)) {
            jdbc.update(
                    "insert into report_interaction (id, report_type, report_id, type, channel,"
                            + " medium, text, base_version, result, created_at)"
                            + " values (?, 'LESSON', ?, 'QUESTION_ANSWER', 'WEB', 'TEXT', 'typed',"
                            + " 1, 'APPLIED', ?)",
                    UUID.randomUUID(),
                    report,
                    Timestamp.from(clock.now()));
        }

        lessonBefore =
                jdbc.queryForMap(
                        "select content::text as content, pending_questions::text as questions"
                                + " from lesson_report where id = ?",
                        lessonOld);
        generalInformationBefore =
                jdbc.queryForObject(
                        "select general_information::text from semester_report where id = ?",
                        String.class,
                        semester);
        semesterBodyBefore = semesterBody();
        semesterPdfHashBefore = hashOfOfficialPdf("SEMESTER", semester);
        start = clock.now();
    }

    private String semesterBody() throws Exception {
        return mvc.perform(get("/api/v1/semester-reports/{id}", semester).cookie(clara))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String officialKey(String type, UUID reportId) {
        return jdbc.queryForObject(
                "select p.storage_key from pdf_document p join report_version v on v.id ="
                        + " p.version_id where v.report_type = ? and v.report_id = ?"
                        + " order by v.number desc limit 1",
                String.class,
                type,
                reportId);
    }

    private String hashOfOfficialPdf(String type, UUID reportId) throws Exception {
        byte[] bytes = storage.open(officialKey(type, reportId)).orElseThrow();
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private RunRetentionUseCase.Result runAfter(Duration time) {
        clock.advance(time);
        return retention.run(Actor.retention(), UUID.randomUUID().toString());
    }

    @Test
    void onlyTheExpiredIsTreatedAndTheApprovedStaysIntact() throws Exception {
        String cancelledKey = officialKey("LESSON", lessonCancelled);

        RunRetentionUseCase.Result result = runAfter(Duration.ofDays(91));

        assertThat(result.status()).isEqualTo(RetentionRunStatus.SUCCEEDED);

        // aprovado vencido: fonte removida, conteúdo e PDF aprovado intactos
        Map<String, Object> source =
                jdbc.queryForMap(
                        "select transcription, source_removed_at from lesson_report where id = ?",
                        lessonOld);
        assertThat(source.get("transcription")).isNull();
        assertThat(source.get("source_removed_at")).isNotNull();
        assertThat(
                        jdbc.queryForMap(
                                "select content::text as content, pending_questions::text as"
                                        + " questions from lesson_report where id = ?",
                                lessonOld))
                .isEqualTo(lessonBefore);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from report_interaction where report_id = ? and"
                                        + " text is not null",
                                Integer.class,
                                lessonOld))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from pdf_document p join report_version v on"
                                        + " v.id = p.version_id where v.report_id = ? and"
                                        + " p.content_removed_at is not null",
                                Integer.class,
                                lessonOld))
                .isZero();
        assertThat(hashOfOfficialPdf("LESSON", lessonOld))
                .isEqualTo(
                        jdbc.queryForObject(
                                "select p.sha256 from pdf_document p join report_version v on"
                                        + " v.id = p.version_id where v.report_id = ?",
                                String.class,
                                lessonOld));

        // cancelado vencido: anonimizado, versões e PDF esvaziados, arquivo removido
        assertThat(
                        jdbc.queryForObject(
                                "select anonymized_at is not null and transcription is null from"
                                        + " lesson_report where id = ?",
                                Boolean.class,
                                lessonCancelled))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select content::text from report_version where report_id = ?",
                                String.class,
                                lessonCancelled))
                .isEqualTo("{}");
        assertThat(storage.exists(cancelledKey)).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from pdf_document where file_removed_at is not"
                                        + " null",
                                Integer.class))
                .isEqualTo(1);

        // em aberto: nada mudou
        assertThat(
                        jdbc.queryForObject(
                                "select transcription is not null and source_removed_at is null"
                                        + " from lesson_report where id = ?",
                                Boolean.class,
                                lessonOpen))
                .isTrue();

        // semestral aprovado: informações gerais, resposta e PDF oficial idênticos
        assertThat(
                        jdbc.queryForObject(
                                "select general_information::text from semester_report where id"
                                        + " = ?",
                                String.class,
                                semester))
                .isEqualTo(generalInformationBefore);
        clock.define(start); // a sessão do professor vale no instante original
        assertThat(semesterBody()).isEqualTo(semesterBodyBefore);
        assertThat(hashOfOfficialPdf("SEMESTER", semester)).isEqualTo(semesterPdfHashBefore);

        // execução registrada: itens com ids e quantidades, evento de trilha
        UUID run = result.runId();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from retention_run_item where run_id = ? and"
                                        + " item_type = 'LESSON_SOURCE' and target_id = ?",
                                Integer.class,
                                run,
                                lessonOld))
                .isEqualTo(1);
        assertThat(result.counts().get(RetentionItemType.CANCELLED_LESSON_REPORT)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from authorship_record where operation ="
                                        + " 'RETENTION_RUN' and result = 'SUCCESS' and origin ="
                                        + " 'RETENTION'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void secondRunTreatsNothing() {
        runAfter(Duration.ofDays(91));

        RunRetentionUseCase.Result second = runAfter(Duration.ofMinutes(5));

        assertThat(second.status()).isEqualTo(RetentionRunStatus.SUCCEEDED);
        assertThat(second.counts()).isEmpty();
    }

    @Test
    void nothingIsTreatedBeforeTheDeadline() {
        RunRetentionUseCase.Result result = runAfter(Duration.ofDays(29));

        assertThat(result.counts()).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from lesson_report where transcription is null",
                                Integer.class))
                .isZero();
    }

    @Test
    void checkRefusesClearingGeneralInformationWithoutAnonymization() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "update semester_report set general_information = null"
                                                + " where id = ?",
                                        semester))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
