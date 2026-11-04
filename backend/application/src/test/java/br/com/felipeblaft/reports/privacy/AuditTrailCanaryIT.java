package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.LeakAudit;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.support.PersonalDataSweep;
import br.com.felipeblaft.reports.support.ScenarioIT;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

/**
 * Varredura da US1 (SC-001, FR-042): depois de login com código errado, cadastro, leituras e
 * consultas da trilha nos dois canais, nenhum valor canário (inclusive o código digitado) aparece
 * em logs, {@code ai_call} nem {@code authorship_record}.
 */
class AuditTrailCanaryIT extends SchoolHistoryBase {

    private static final String TYPED_CODE = "QZ7-TYPED-CODE-CANARY";

    @Autowired StudentRepository students;

    @Test
    void trailOperationsOnBothChannelsLeaveNoCanaryBehind() throws Exception {
        Map<String, String> values = new LinkedHashMap<>(CanaryStudent.sweepValuesWithCpf());
        values.put("código digitado", TYPED_CODE);
        PersonalDataSweep sweep = PersonalDataSweep.of(jdbc, values);
        LeakAudit audit = sweep.newAudit();

        Teacher clara = scenario.clara();
        Cookie administrator = signInNewAdministrator();
        try (LogCapture logs = LogCapture.start()) {
            // login com código errado (Web e Telegram)
            assertThat(signInTeacherRaw(TYPED_CODE, "203.0.113.31").getStatus()).isNotEqualTo(200);
            botReplyTo(USER_CLARA, TYPED_CODE);

            // cadastro do aluno canário e leituras pelo administrador
            UUID studentId =
                    registerStudentAsAdministrator(
                            administrator,
                            minorStudent(
                                    CanaryStudent.NAME,
                                    CanaryStudent.BIRTH_DATE_ISO,
                                    CanaryCpf.MASKED,
                                    CanaryStudent.GUARDIAN_NAME,
                                    CanaryStudent.GUARDIAN_PHONE_MASKED,
                                    CanaryStudent.GUARDIAN_EMAIL),
                            clara.id());
            Student student = students.searchById(studentId).orElseThrow();
            UUID lesson =
                    scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 10), "Escalas")
                            .id();
            call(HttpMethod.GET, SCHOOL_HISTORY + "?studentId=" + studentId, administrator);
            call(HttpMethod.GET, lessonPdf(lesson), administrator);

            // leituras do professor na Web
            Cookie teacher = signInTeacher(ScenarioIT.CODE_CLARA);
            call(HttpMethod.GET, "/api/v1/students/" + studentId + "/history", teacher);
            call(HttpMethod.GET, "/api/v1/lesson-reports/" + lesson + "/official-pdf", teacher);

            // consultas da trilha: por aluno, por período e verificação de integridade
            String period = "from=2026-03-01&to=2026-03-31";
            call(HttpMethod.GET, ADMIN + "/audit-trail?from=2026-09-01&to=2026-09-30&targetId=" + studentId, administrator);
            call(HttpMethod.GET, ADMIN + "/audit-trail?" + period, administrator);
            call(HttpMethod.GET, ADMIN + "/audit-trail/integrity?verifyNow=true", administrator);

            sweep.sweep(audit, "trilha nos dois canais", logs);
        }

        assertThat(audit.sweeps()).isPositive();
        audit.assertClean();
    }
}
