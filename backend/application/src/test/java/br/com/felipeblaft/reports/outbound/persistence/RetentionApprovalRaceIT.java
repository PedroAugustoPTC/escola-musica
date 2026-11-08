package br.com.felipeblaft.reports.outbound.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.DocumentStoragePort;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import br.com.felipeblaft.reports.usecase.privacy.RunRetentionUseCase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/**
 * Aprovação em paralelo com a retenção (FR-011): o relatório aprovado agora não perde fonte nem o
 * PDF da versão aprovada, mesmo que a rotina rode no mesmo instante.
 */
class RetentionApprovalRaceIT extends SchoolHistoryBase {

    @Autowired RunRetentionUseCase retention;
    @Autowired DocumentStoragePort storage;

    @Test
    void approvalRacingWithTheRunKeepsSourceAndApprovedPdf() throws Exception {
        Teacher teacher = scenario.clara();
        Student student = scenario.student(teacher, "Matteo Mafra", "Piano");
        UUID old =
                scenario.approvedLesson(teacher, student, LocalDate.of(2026, 3, 10), "Sopro").id();
        Cookie clara = signInTeacher(ScenarioIT.CODE_CLARA);
        UUID semester = generatedSemester(clara, student.id(), "2026-1");
        // a aula antiga já passou do prazo; o semestral será aprovado agora, junto com a rotina
        jdbc.update(
                "update lesson_report set approved_at = approved_at - interval '40 days'"
                        + " where id = ?",
                old);

        List<Callable<Integer>> tasks =
                List.of(
                        () ->
                                call(
                                                HttpMethod.POST,
                                                "/api/v1/semester-reports/" + semester + "/approval",
                                                clara,
                                                "{\"confirmedVersion\":1}")
                                        .getStatus(),
                        () -> {
                            retention.run(Actor.retention(), UUID.randomUUID().toString());
                            return 0;
                        });
        List<Integer> results = ConcurrentRunner.runTogether(tasks);

        assertThat(results.getFirst()).isEqualTo(200);
        assertThat(
                        jdbc.queryForObject(
                                "select transcription is null from lesson_report where id = ?",
                                Boolean.class,
                                old))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select source_removed_at is null and anonymized_at is null"
                                        + " and general_information is not null from"
                                        + " semester_report where id = ?",
                                Boolean.class,
                                semester))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from pdf_document p join report_version v on"
                                        + " v.id = p.version_id where v.report_id = ? and"
                                        + " p.content_removed_at is not null",
                                Integer.class,
                                semester))
                .isZero();
        String key =
                jdbc.queryForObject(
                        "select p.storage_key from pdf_document p join report_version v on v.id ="
                                + " p.version_id where v.report_id = ? and v.number = 1 and p.type = 'OFFICIAL'",
                        String.class,
                        semester);
        assertThat(storage.exists(key)).isTrue();
    }
}
