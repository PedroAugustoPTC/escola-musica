package br.com.felipeblaft.reports.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.access.Actor;
import br.com.felipeblaft.reports.domain.privacy.ErasedStudent;
import br.com.felipeblaft.reports.domain.privacy.ErasureRequest;
import br.com.felipeblaft.reports.domain.privacy.ErasureStatus;
import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.StudentScope;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.port.ErasureRequestRepository;
import br.com.felipeblaft.reports.port.StudentRepository;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.LeakAudit;
import br.com.felipeblaft.reports.support.LogCapture;
import br.com.felipeblaft.reports.support.PersonalDataSweep;
import br.com.felipeblaft.reports.support.SchoolHistoryBase;
import br.com.felipeblaft.reports.usecase.privacy.ConfirmErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.GetErasureReceiptUseCase;
import br.com.felipeblaft.reports.usecase.privacy.PreviewErasureUseCase;
import br.com.felipeblaft.reports.usecase.privacy.RegisterErasureRequestUseCase;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Eliminação a pedido ponta a ponta (SC-004): depois de registrar, ver o alcance e confirmar, o
 * aluno canário não existe em nenhuma coluna de texto do esquema, os PDFs saíram do armazenamento,
 * o aluno some das consultas e o CPF antigo volta a ser aceito.
 */
class ErasureIT extends SchoolHistoryBase {

    @Autowired RegisterErasureRequestUseCase register;
    @Autowired PreviewErasureUseCase preview;
    @Autowired ConfirmErasureUseCase confirm;
    @Autowired GetErasureReceiptUseCase receipt;
    @Autowired ErasureRequestRepository erasures;
    @Autowired StudentRepository studentRepository;

    @Test
    void erasureLeavesNoPersonalDataAnywhereAndFreesTheCpf() {
        Actor admin = Actor.administrator(insertAdministrator().administrator().id(), false);
        Teacher clara = scenario.clara();
        Teacher bruno = scenario.bruno();
        Student student = scenario.student(clara, CanaryStudent.NAME, CanaryStudent.INSTRUMENT);
        assign(bruno, student);
        jdbc.update(
                "update student set birth_date = ?, guardian_name = ?, guardian_phone = ?,"
                        + " guardian_email = ? where id = ?",
                java.sql.Date.valueOf(CanaryStudent.BIRTH_DATE),
                CanaryStudent.GUARDIAN_NAME,
                CanaryStudent.GUARDIAN_PHONE_DIGITS,
                CanaryStudent.GUARDIAN_EMAIL,
                student.id());
        UUID first =
                scenario.approvedLesson(clara, student, LocalDate.of(2026, 3, 10), "Escalas").id();
        UUID second =
                scenario.approvedLesson(bruno, student, LocalDate.of(2026, 3, 17), "Ritmo").id();
        jdbc.update(
                "update lesson_report set transcription = ? where id = ?",
                CanaryStudent.TRANSCRIPTION_SNIPPET,
                first);
        jdbc.update(
                "insert into report_interaction (id, report_type, report_id, type, channel,"
                        + " medium, text, base_version, result, created_at)"
                        + " values (?, 'LESSON', ?, 'QUESTION_ANSWER', 'WEB', 'TEXT', ?, 1,"
                        + " 'APPLIED', ?)",
                UUID.randomUUID(),
                second,
                CanaryStudent.REPORT_TEXT_SNIPPET,
                Timestamp.from(clock.now()));
        assertThat(jdbc.queryForObject("select count(*) from pdf_document", Integer.class))
                .isPositive();
        String cpfHash =
                jdbc.queryForObject(
                        "select cpf_hash from student where id = ?", String.class, student.id());

        PersonalDataSweep sweep = PersonalDataSweep.of(jdbc);
        LeakAudit audit = sweep.newAudit();
        UUID requestId;
        try (LogCapture logs = LogCapture.start()) {
            requestId = erase(admin, student.id());
            sweep.sweep(audit, "eliminação", logs);
        }

        assertThat(erasures.isErased(student.id())).isTrue();
        assertThat(erasures.findById(requestId).orElseThrow().status())
                .isEqualTo(ErasureStatus.COMPLETED);
        assertThat(erasures.findById(requestId).orElseThrow().requesterName()).isNull();
        assertThat(studentRepository.searchById(student.id())).isEmpty();
        assertThat(studentRepository.findVisible(StudentScope.all(), student.id()))
                .isEmpty();
        assertThat(studentRepository.listByTeacher(clara.id())).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from teacher_student_assignment where student_id = ?",
                                Integer.class,
                                student.id()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from pdf_document where file_removed_at is null",
                                Integer.class))
                .isZero();
        assertThat(allTextColumnsContainingAnyCanary()).isEmpty();
        audit.assertClean();

        // o CPF antigo volta a ser aceito num novo cadastro (FR-024)
        assertThat(studentRepository.searchByCpfHash(cpfHash)).isEmpty();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from erased_student where student_id = ?",
                                Integer.class,
                                student.id()))
                .isOne();
        GetErasureReceiptUseCase.Receipt proof = receipt.run(admin, requestId, UUID.randomUUID().toString());
        assertThat(proof.studentShortId())
                .isEqualTo(student.id().toString().replace("-", "").substring(0, 6));
        assertThat(ErasedStudent.label(student.id())).startsWith("Aluno removido");
    }

    private UUID erase(Actor admin, UUID studentId) {
        ErasureRequest request =
                register.run(
                        admin,
                        new RegisterErasureRequestUseCase.Command(
                                studentId,
                                "MOTHER",
                                "Responsável Fictício",
                                LocalDate.ofInstant(clock.now(), java.time.ZoneOffset.UTC),
                                "EMAIL",
                                "PROT-1",
                                UUID.randomUUID().toString()));
        PreviewErasureUseCase.Preview shown = preview.run(admin, request.id(), UUID.randomUUID().toString());
        assertThat(shown.scope().totalLessonReports()).isEqualTo(2);
        confirm.run(
                admin,
                new ConfirmErasureUseCase.Command(
                        request.id(), shown.confirmationToken(), "ELIMINAR", UUID.randomUUID().toString()));
        return request.id();
    }

    /** Tabela.coluna em que algum valor canário aparece, varrendo todo texto e jsonb do esquema. */
    private List<String> allTextColumnsContainingAnyCanary() {
        Map<String, String> values = CanaryStudent.sweepValues();
        List<Map<String, Object>> columns =
                jdbc.queryForList(
                        "select table_name, column_name from information_schema.columns"
                                + " where table_schema = current_schema()"
                                + " and data_type in ('text', 'character varying', 'jsonb', 'json')"
                                + " and table_name not in ('flyway_schema_history')");
        java.util.ArrayList<String> hits = new java.util.ArrayList<>();
        for (Map<String, Object> column : columns) {
            String table = (String) column.get("table_name");
            String name = (String) column.get("column_name");
            for (String value : values.values()) {
                Integer found =
                        jdbc.queryForObject(
                                "select count(*) from \"" + table + "\" where \"" + name
                                        + "\"::text like ?",
                                Integer.class,
                                "%" + value + "%");
                if (found != null && found > 0) {
                    hits.add(table + "." + name);
                }
            }
        }
        return hits;
    }
}
