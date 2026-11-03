package br.com.felipeblaft.reports.support;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.domain.registry.TeacherStudent;
import br.com.felipeblaft.reports.port.AssignmentRepository;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Base dos testes da visão da escola (feature 006) na pilha real: entrada do administrador, rotas
 * da visão, geração e aprovação de um semestral pela API do professor e retrocesso de relatório de
 * aula a um estado não aprovado. Nada aqui faz dublê de caso de uso.
 */
public abstract class SchoolHistoryBase extends AdministrationStackBase {

    protected static final String SCHOOL_HISTORY = ADMIN + "/school-history";

    @Autowired protected AssignmentRepository assignmentRepository;

    /** Associa também o professor ao aluno (o aluno passa a ter mais de um professor). */
    protected void assign(Teacher teacher, Student student) {
        assignmentRepository.save(TeacherStudent.create(teacher.id(), student.id(), clock.now()));
    }

    /** Insere um administrador e entra; devolve o cookie da sessão. */
    protected Cookie signInNewAdministrator() throws Exception {
        return signInAdministrator(insertAdministrator());
    }

    protected static String lessonPdf(UUID reportId) {
        return SCHOOL_HISTORY + "/lesson-reports/" + reportId + "/pdf";
    }

    protected static String lessonDownload(UUID reportId) {
        return lessonPdf(reportId) + "/download";
    }

    protected static String semesterPdf(UUID reportId) {
        return SCHOOL_HISTORY + "/semester-reports/" + reportId + "/pdf";
    }

    protected static String semesterDownload(UUID reportId) {
        return semesterPdf(reportId) + "/download";
    }

    /**
     * Gera o semestral do aluno pela API do professor, processa a fila e o aprova na versão 1; o
     * aluno precisa ter ao menos uma aula aprovada no período.
     */
    protected UUID approvedSemester(Cookie teacher, UUID studentId, String periodKey)
            throws Exception {
        UUID id = generatedSemester(teacher, studentId, periodKey);
        MockHttpServletResponse approval =
                call(
                        HttpMethod.POST,
                        "/api/v1/semester-reports/" + id + "/approval",
                        teacher,
                        "{\"confirmedVersion\":1}");
        assertThat(approval.getStatus()).as("aprovação do semestral").isEqualTo(200);
        return id;
    }

    /** Gera o semestral e processa a fila, sem aprovar: fica aguardando conferência. */
    protected UUID generatedSemester(Cookie teacher, UUID studentId, String periodKey)
            throws Exception {
        String query = "select id from semester_report where student_id = ? and period_key = ?";
        List<UUID> before = jdbc.queryForList(query, UUID.class, studentId, periodKey);
        MockHttpServletResponse generation =
                call(
                        HttpMethod.POST,
                        "/api/v1/students/" + studentId + "/semester-reports",
                        teacher,
                        "{\"periodKey\":\"" + periodKey + "\"}");
        assertThat(generation.getStatus()).as("geração do semestral").isEqualTo(202);
        scenario.processQueue();
        List<UUID> created =
                new ArrayList<>(jdbc.queryForList(query, UUID.class, studentId, periodKey));
        created.removeAll(before);
        assertThat(created).as("semestral gerado").hasSize(1);
        return created.getFirst();
    }

    /** Devolve o relatório de aula a um estado não aprovado (sem versão nem data de aprovação). */
    protected void revertLessonTo(UUID reportId, String status) {
        jdbc.update(
                "update lesson_report set status = ?, approved_at = null,"
                        + " approved_version = null, approval_channel = null where id = ?",
                status,
                reportId);
    }

    /** O mesmo para o semestral. */
    protected void revertSemesterTo(UUID reportId, String status) {
        jdbc.update(
                "update semester_report set status = ?, approved_at = null,"
                        + " approved_version = null, approval_channel = null where id = ?",
                status,
                reportId);
    }
}
