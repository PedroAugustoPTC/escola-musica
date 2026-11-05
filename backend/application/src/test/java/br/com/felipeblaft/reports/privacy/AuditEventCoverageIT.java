package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.AdjustableClock;
import br.com.felipeblaft.reports.support.AudioNormalizationTest;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import br.com.felipeblaft.reports.usecase.privacy.RunRetentionUseCase;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Cobertura da trilha (SC-002): a lista de {@code contracts/audit-events.md} é a fonte. Toda
 * operação da tabela tem um cenário (executado aqui, coberto por outro teste da trilha ou
 * adiado para a história que a implementa); operação nova na tabela sem cenário reprova o teste.
 * Os cenários executados aqui conferem que cada operação gera exatamente um evento com origem,
 * papel, alvo e resultado esperados.
 */
class AuditEventCoverageIT extends SchoolHistoryBase {

    @Autowired RunRetentionUseCase retention;

    private static final Path TABLE =
            Path.of("..", "..", "specs", "007-privacy-retention-operations", "contracts", "audit-events.md");

    private static final Pattern OPERATION = Pattern.compile("`([A-Z][A-Z_]+)`");

    /** Cobertas por testes próprios da trilha (005, 006 e US1). */
    private static final Map<String, String> COVERED_ELSEWHERE = coveredElsewhere();

    /** Operações das histórias seguintes: o cenário entra junto com a implementação. */
    private static final Map<String, String> DEFERRED = deferred();

    private static Map<String, String> coveredElsewhere() {
        Map<String, String> covered = new LinkedHashMap<>();
        for (String operation :
                List.of(
                        "ADMIN_SIGN_IN",
                        "ADMIN_ACTIVATE",
                        "ADMIN_SIGN_OUT",
                        "ADMIN_CHANGE_PASSWORD",
                        "TEACHER_SIGN_IN",
                        "ATTEMPT_BLOCKED",
                        "TEACHER_LINK_TELEGRAM",
                        "TEACHER_UNLINK_TELEGRAM",
                        "AUTHORIZATION_DENIED",
                        "TEACHER_REGISTER",
                        "TEACHER_UPDATE",
                        "TEACHER_INACTIVATE",
                        "TEACHER_REACTIVATE",
                        "TEACHER_REISSUE_ACCESS_CODE",
                        "STUDENT_REGISTER",
                        "STUDENT_UPDATE",
                        "STUDENT_INACTIVATE",
                        "STUDENT_REACTIVATE",
                        "ASSIGNMENT_ASSIGN",
                        "ASSIGNMENT_REMOVE",
                        "ADMINISTRATOR_CREATE",
                        "ADMINISTRATOR_INACTIVATE",
                        "ADMINISTRATOR_REACTIVATE")) {
            covered.put(operation, "AuthorshipCoverageIT");
        }
        for (String operation :
                List.of(
                        "SCHOOL_HISTORY_QUERY",
                        "SCHOOL_REPORT_DOCUMENT_OPEN",
                        "SCHOOL_REPORT_DOCUMENT_DOWNLOAD")) {
            covered.put(operation, "AuthorshipSchoolHistoryIT");
        }
        for (String operation :
                List.of(
                        "TEACHER_SIGN_OUT",
                        "LESSON_REPORT_APPROVE",
                        "LESSON_REPORT_CANCEL",
                        "SEMESTER_REPORT_APPROVE",
                        "SEMESTER_REPORT_CANCEL",
                        "REPORT_DOCUMENT_OPEN",
                        "REPORT_DOCUMENT_DOWNLOAD")) {
            covered.put(operation, "TeacherWebAuditIT");
        }
        return covered;
    }

    private static Map<String, String> deferred() {
        Map<String, String> pending = new LinkedHashMap<>();
        for (String operation :
                List.of(
                        "ERASURE_REQUEST_REGISTER",
                        "ERASURE_PREVIEW",
                        "ERASURE_CONFIRM",
                        "ERASURE_COMPLETED",
                        "ERASURE_RECEIPT_READ")) {
            pending.put(operation, "US4");
        }
        for (String operation :
                List.of(
                        "OPERATIONS_COST_READ",
                        "OPERATIONS_ALERT_READ",
                        "OPERATIONS_ALERT_ACK",
                        "ALERT_OPENED",
                        "ALERT_NORMALIZED")) {
            pending.put(operation, "US5");
        }
        return pending;
    }

    /** Operações da primeira coluna de operação de cada linha da tabela do contrato. */
    static Set<String> tableOperations() throws Exception {
        Set<String> operations = new LinkedHashSet<>();
        for (String line : Files.readAllLines(TABLE)) {
            if (!line.startsWith("|") || line.startsWith("|---") || line.startsWith("| Grupo")) {
                continue;
            }
            String[] columns = line.split("\\|");
            if (columns.length < 4) {
                continue;
            }
            String text = columns[2].replaceAll("\\([^)]*\\)", "");
            Matcher matcher = OPERATION.matcher(text);
            while (matcher.find()) {
                operations.add(matcher.group(1));
            }
        }
        return operations;
    }

    @Test
    void everyOperationOfTheTableHasAScenarioOrADeferral() throws Exception {
        Set<String> table = tableOperations();
        assertThat(table).as("operações lidas da tabela").hasSizeGreaterThan(40);

        Set<String> known = new LinkedHashSet<>(COVERED_ELSEWHERE.keySet());
        known.addAll(DEFERRED.keySet());
        known.addAll(List.of("HISTORY_QUERY", "REPORT_DOCUMENT_SENT", "AUDIT_TRAIL_QUERY", "AUDIT_TRAIL_VERIFY"));
        // cenários executados neste teste: consentimento (US2) e retenção (US3)
        known.addAll(
                List.of(
                        "CONSENT_RECORD",
                        "CONSENT_REVOKE",
                        "CONSENT_TERM_PUBLISH",
                        "CONSENT_BLOCKED_AI",
                        "RETENTION_RUN",
                        "AUDIT_TRAIL_PURGED"));

        assertThat(table).as("operação da tabela sem cenário").isSubsetOf(known);
        assertThat(known).as("cenário de operação que não está na tabela").isSubsetOf(table);
    }

    private List<Map<String, Object>> events(String operation) {
        return jdbc.queryForList(
                "select * from authorship_record where operation = ? order by sequence", operation);
    }

    private Map<String, Object> onlyEvent(String operation) {
        List<Map<String, Object>> rows = events(operation);
        assertThat(rows).as("eventos de " + operation).hasSize(1);
        return rows.getFirst();
    }

    @Test
    void trailQueryAndVerificationLeaveExactlyOneEventEachOnTheWeb() throws Exception {
        Cookie administrator = signInNewAdministrator();
        clearAuthorship();

        assertThat(call(HttpMethod.GET, ADMIN + "/audit-trail?from=2026-09-01&to=2026-09-30", administrator)
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        call(HttpMethod.GET, ADMIN + "/audit-trail/integrity?verifyNow=true", administrator)
                                .getStatus())
                .isEqualTo(200);

        Map<String, Object> query = onlyEvent("AUDIT_TRAIL_QUERY");
        assertThat(query.get("origin")).isEqualTo("WEB");
        assertThat(query.get("author_role")).isEqualTo("ADMINISTRATOR");
        assertThat(query.get("target_type")).isEqualTo("AUDIT_TRAIL");
        assertThat(query.get("result")).isEqualTo("SUCCESS");
        assertThat(query.get("filter_period_start")).isNotNull();
        assertThat(query.get("filter_period_end")).isNotNull();

        Map<String, Object> verify = onlyEvent("AUDIT_TRAIL_VERIFY");
        assertThat(verify.get("origin")).isEqualTo("WEB");
        assertThat(verify.get("author_role")).isEqualTo("ADMINISTRATOR");
        assertThat(verify.get("target_type")).isEqualTo("AUDIT_TRAIL");
        assertThat(verify.get("item_count")).isNotNull();
    }

    @Test
    void consentOperationsLeaveExactlyOneEventEachOnTheWeb() throws Exception {
        Cookie administrator = signInNewAdministrator();
        Teacher clara = scenario.clara();
        Student minor = scenario.studentWithoutConsent(clara, "Matteo Ferreira", "Piano");
        Cookie teacher = signInTeacher(ScenarioIT.CODE_CLARA);
        clearAuthorship();

        assertThat(
                        call(
                                        HttpMethod.POST,
                                        ADMIN + "/consent-terms",
                                        administrator,
                                        Map.of(
                                                "publishedOn", "2026-09-01",
                                                "text", "Institutional text",
                                                "requiresNewConsent", false))
                                .getStatus())
                .isEqualTo(201);
        // sem consentimento: a IA é bloqueada para o menor
        assertThat(
                        mvc.perform(
                                        multipart(
                                                        "/api/v1/students/{id}/lesson-reports",
                                                        minor.id())
                                                .file(
                                                        new MockMultipartFile(
                                                                "audio",
                                                                "lesson.webm",
                                                                "audio/webm",
                                                                AudioNormalizationTest
                                                                        .AUDIO_WITH_SPEECH))
                                                .cookie(teacher)
                                                .with(csrf().asHeader()))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(409);
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        ADMIN + "/students/" + minor.id() + "/consents",
                                        administrator,
                                        Map.of(
                                                "consentingName", "Maria Responsável",
                                                "relationship", "MOTHER",
                                                "consentedOn", "2026-09-01",
                                                "termVersion", 1,
                                                "collectionMethod", "DIGITAL"))
                                .getStatus())
                .isEqualTo(201);
        UUID consent =
                jdbc.queryForObject(
                        "select id from student_consent where student_id = ?",
                        UUID.class,
                        minor.id());
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        ADMIN + "/consents/" + consent + "/revocation",
                                        administrator,
                                        Map.of("revokedOn", "2026-09-10", "revokedBy", "MOTHER"))
                                .getStatus())
                .isEqualTo(200);

        Map<String, Object> publish = onlyEvent("CONSENT_TERM_PUBLISH");
        assertThat(publish.get("origin")).isEqualTo("WEB");
        assertThat(publish.get("author_role")).isEqualTo("ADMINISTRATOR");
        assertThat(publish.get("target_type")).isEqualTo("CONSENT_TERM");
        assertThat(publish.get("result")).isEqualTo("SUCCESS");

        Map<String, Object> blocked = onlyEvent("CONSENT_BLOCKED_AI");
        assertThat(blocked.get("origin")).isEqualTo("WEB");
        assertThat(blocked.get("author_role")).isEqualTo("TEACHER");
        assertThat(blocked.get("target_type")).isEqualTo("STUDENT");
        assertThat(blocked.get("target_id")).isEqualTo(minor.id());
        assertThat(blocked.get("result")).isEqualTo("BLOCKED");
        assertThat(blocked.get("error_code")).isEqualTo("CONSENT_REQUIRED");

        Map<String, Object> recorded = onlyEvent("CONSENT_RECORD");
        assertThat(recorded.get("origin")).isEqualTo("WEB");
        assertThat(recorded.get("author_role")).isEqualTo("ADMINISTRATOR");
        assertThat(recorded.get("target_type")).isEqualTo("CONSENT");
        assertThat(recorded.get("result")).isEqualTo("SUCCESS");

        Map<String, Object> revoked = onlyEvent("CONSENT_REVOKE");
        assertThat(revoked.get("origin")).isEqualTo("WEB");
        assertThat(revoked.get("author_role")).isEqualTo("ADMINISTRATOR");
        assertThat(revoked.get("target_type")).isEqualTo("CONSENT");
        assertThat(revoked.get("target_id")).isEqualTo(consent);
        assertThat(revoked.get("result")).isEqualTo("SUCCESS");
    }

    @Test
    void retentionRunAndTrailPurgeLeaveOneEventEachWithTheRetentionOrigin() throws Exception {
        // um evento muito antigo, para o expurgo da trilha ter o que remover
        clock.define(Instant.parse("2020-01-01T12:00:00Z"));
        signInNewAdministrator();
        clock.define(AdjustableClock.DEFAULT);

        RunRetentionUseCase.Result result =
                retention.run(Actor.retention(), UUID.randomUUID().toString());
        assertThat(result.status().name()).isEqualTo("SUCCEEDED");

        Map<String, Object> run = onlyEvent("RETENTION_RUN");
        assertThat(run.get("origin")).isEqualTo("RETENTION");
        assertThat(run.get("author_id")).isNull();
        assertThat(run.get("target_type")).isEqualTo("RETENTION_RUN");
        assertThat(run.get("result")).isEqualTo("SUCCESS");
        assertThat(run.get("item_count")).isNotNull();

        Map<String, Object> purge = onlyEvent("AUDIT_TRAIL_PURGED");
        assertThat(purge.get("origin")).isEqualTo("RETENTION");
        assertThat(purge.get("target_type")).isEqualTo("AUDIT_TRAIL");
        assertThat(((Number) purge.get("item_count")).intValue()).isPositive();
    }

    @Test
    void historyAndDocumentLeaveExactlyOneEventEachOnTelegram() throws Exception {
        Teacher clara = scenario.clara();
        Student matteo = scenario.student(clara, "Matteo Ferreira", "Piano");
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        scenario.approvedLesson(clara, matteo, LocalDate.of(2026, 3, 10), "Escalas");
        clearAuthorship();

        sendText(USER_CLARA, "histórico");
        sendText(USER_CLARA, matteo.name());
        sendCallback(USER_CLARA, "v1:history-item:0");

        Map<String, Object> history = onlyEvent("HISTORY_QUERY");
        assertThat(history.get("origin")).isEqualTo("TELEGRAM");
        assertThat(history.get("author_role")).isEqualTo("TEACHER");
        assertThat(history.get("target_type")).isEqualTo("STUDENT");
        assertThat(history.get("target_id")).isEqualTo(matteo.id());
        assertThat(history.get("result")).isEqualTo("SUCCESS");

        Map<String, Object> sent = onlyEvent("REPORT_DOCUMENT_SENT");
        assertThat(sent.get("origin")).isEqualTo("TELEGRAM");
        assertThat(sent.get("author_role")).isEqualTo("TEACHER");
        assertThat(sent.get("target_type")).isEqualTo("LESSON_REPORT");
        assertThat(sent.get("document_version")).isEqualTo(1);
        assertThat(sent.get("result")).isEqualTo("SUCCESS");
    }
}
