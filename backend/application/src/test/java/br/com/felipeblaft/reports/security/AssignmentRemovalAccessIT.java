package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.CanaryStudent;
import br.com.felipeblaft.reports.support.ResponseSweeper;
import br.com.felipeblaft.reports.support.ResponseSweeper.Place;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Retirar a associação tira o acesso (T163, SC-006 e SC-013): depois que o administrador retira o
 * professor do aluno, o professor perde o aluno e os relatórios dele na Web e no Telegram, com a
 * resposta idêntica à de um identificador inexistente; nenhum relatório é criado, alterado,
 * aprovado ou cancelado; ao reassociar, o aluno e o relatório voltam como estavam. A autoria {@code
 * ASSIGNMENT_*} não leva dado algum do aluno.
 */
class AssignmentRemovalAccessIT extends SecurityBase {

    private static final UUID ABSENT = UUID.fromString("00000000-0000-4000-8000-000000000999");

    private Cookie administrator;
    private Cookie claraSession;
    private Teacher clara;
    private Student student;
    private Reports reports;
    private String assignment;

    @BeforeEach
    void prepare() throws Exception {
        clara = scenario.clara();
        administrator = signInAdministrator();
        // o administrador cadastra o aluno menor canário e associa a Clara
        MvcResult created =
                call(
                        HttpMethod.POST,
                        API + "/admin/students",
                        "{\"name\":\""
                                + CanaryStudent.NAME
                                + "\",\"birthDate\":\""
                                + CanaryStudent.BIRTH_DATE_ISO
                                + "\",\"cpf\":\""
                                + CanaryCpf.MASKED
                                + "\",\"instrument\":\"Violão\",\"guardian\":{\"name\":\""
                                + CanaryStudent.GUARDIAN_NAME
                                + "\",\"phone\":\""
                                + CanaryStudent.GUARDIAN_PHONE_MASKED
                                + "\",\"email\":\""
                                + CanaryStudent.GUARDIAN_EMAIL
                                + "\"},\"teacherIds\":[\""
                                + clara.id()
                                + "\"]}",
                        administrator);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        student =
                studentRepository
                        .searchById(UUID.fromString(jsonOf(created).path("id").asString()))
                        .orElseThrow();
        claraSession = signInTeacher(ScenarioIT.CODE_CLARA);
        link(USER_CLARA, ScenarioIT.CODE_CLARA);
        reports = createReports(clara, student, claraSession);
        assignment = API + "/admin/students/" + student.id() + "/teachers/" + clara.id();
    }

    private String reportsDigest() {
        return jdbc.queryForObject(
                "select md5(coalesce((select string_agg(t::text, '|' order by t::text) from lesson_report t), '')"
                        + " || coalesce((select string_agg(t::text, '|' order by t::text) from semester_report t), '')"
                        + " || coalesce((select string_agg(t::text, '|' order by t::text) from report_version t), '')"
                        + " || coalesce((select string_agg(t::text, '|' order by t::text) from lesson t), '')"
                        + " || coalesce((select string_agg(t::text, '|' order by t::text) from processing_job t), ''))",
                String.class);
    }

    private record Attempt(String label, HttpMethod method, String path, String body) {}

    private List<Attempt> attemptsFor(
            UUID studentId, UUID open, UUID approved, UUID semester, UUID job) {
        return List.of(
                new Attempt("aluno", HttpMethod.GET, "/my-students/" + studentId, null),
                new Attempt("aluno (001)", HttpMethod.GET, "/students/" + studentId, null),
                new Attempt(
                        "histórico", HttpMethod.GET, "/students/" + studentId + "/history", null),
                new Attempt(
                        "em aberto",
                        HttpMethod.GET,
                        "/students/" + studentId + "/open-report",
                        null),
                new Attempt(
                        "semesters", HttpMethod.GET, "/students/" + studentId + "/semesters", null),
                new Attempt(
                        "gerar semestral",
                        HttpMethod.POST,
                        "/students/" + studentId + "/semester-reports",
                        "{\"periodKey\":\"2026-1\"}"),
                new Attempt("aula em aberto", HttpMethod.GET, "/lesson-reports/" + open, null),
                new Attempt(
                        "responder",
                        HttpMethod.POST,
                        "/lesson-reports/" + open + "/answers",
                        "{\"text\":\"Sim.\"}"),
                new Attempt(
                        "alterar aula",
                        HttpMethod.POST,
                        "/lesson-reports/" + approved + "/changes",
                        "{\"text\":\"Mude.\",\"baseVersion\":1}"),
                new Attempt(
                        "tentar de novo",
                        HttpMethod.POST,
                        "/lesson-reports/" + open + "/retry",
                        null),
                new Attempt(
                        "aprovar aula",
                        HttpMethod.POST,
                        "/lesson-reports/" + open + "/approval",
                        "{\"confirmedVersion\":1}"),
                new Attempt(
                        "cancelar aula",
                        HttpMethod.POST,
                        "/lesson-reports/" + open + "/cancellation",
                        null),
                new Attempt(
                        "PDF da aula",
                        HttpMethod.GET,
                        "/lesson-reports/" + approved + "/official-pdf",
                        null),
                new Attempt("semestral", HttpMethod.GET, "/semester-reports/" + semester, null),
                new Attempt(
                        "alterar semestral",
                        HttpMethod.POST,
                        "/semester-reports/" + semester + "/changes",
                        "{\"text\":\"Mude.\",\"baseVersion\":1}"),
                new Attempt(
                        "aprovar semestral",
                        HttpMethod.POST,
                        "/semester-reports/" + semester + "/approval",
                        "{\"confirmedVersion\":1}"),
                new Attempt(
                        "cancelar semestral",
                        HttpMethod.POST,
                        "/semester-reports/" + semester + "/cancellation",
                        null),
                new Attempt("job", HttpMethod.GET, "/jobs/" + job, null),
                new Attempt(
                        "descartar trabalho", HttpMethod.POST, "/jobs/" + job + "/discard", null));
    }

    private MvcResult run(Attempt attempt) throws Exception {
        return call(attempt.method(), API + attempt.path(), attempt.body(), claraSession);
    }

    @Test
    void afterTheRemovalTheTeacherLosesTheStudentAndTheReportsAndTheReassociationReturnsThem()
            throws Exception {
        // com a associação, tudo responde
        assertThat(
                        run(new Attempt(
                                        "aluno",
                                        HttpMethod.GET,
                                        "/my-students/" + student.id(),
                                        null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        MvcResult openBefore =
                call(
                        HttpMethod.GET,
                        API + "/lesson-reports/" + reports.openLesson(),
                        null,
                        claraSession);
        assertThat(openBefore.getResponse().getStatus()).isEqualTo(200);
        String reportsBefore = reportsDigest();
        String openBody = bodyOf(openBefore);

        // o administrador retira a associação (e vê quantos relatórios congelam)
        MvcResult preview =
                call(HttpMethod.GET, assignment + "/removal-preview", null, administrator);
        assertThat(preview.getResponse().getStatus()).isEqualTo(200);
        assertThat(
                        call(HttpMethod.DELETE, assignment, null, administrator)
                                .getResponse()
                                .getStatus())
                .isEqualTo(204);
        assertThat(reportsDigest())
                .as("retirar a associação não altera relatório")
                .isEqualTo(reportsBefore);

        // Web: tudo é como se não existisse, igual a identificadores que não existem
        List<String> differences = new ArrayList<>();
        List<Attempt> real =
                attemptsFor(
                        student.id(),
                        reports.openLesson(),
                        reports.approvedLesson(),
                        reports.semester(),
                        reports.job());
        List<Attempt> absent = attemptsFor(ABSENT, ABSENT, ABSENT, ABSENT, ABSENT);
        for (int i = 0; i < real.size(); i++) {
            MvcResult lost = run(real.get(i));
            MvcResult none = run(absent.get(i));
            if (lost.getResponse().getStatus() != 404
                    || !errorCode(lost).equals("RESOURCE_NOT_FOUND")) {
                differences.add(
                        real.get(i).label()
                                + ": esperado 404 RESOURCE_NOT_FOUND e veio "
                                + lost.getResponse().getStatus()
                                + " "
                                + errorCode(lost));
            }
            if (!bodyOf(lost).equals(bodyOf(none))
                    || lost.getResponse().getStatus() != none.getResponse().getStatus()) {
                differences.add(real.get(i).label() + ": difere da resposta do inexistente");
            }
        }
        // registrar aula por áudio também é negado
        MvcResult upload =
                mvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .multipart(
                                                API
                                                        + "/students/"
                                                        + student.id()
                                                        + "/lesson-reports")
                                        .file(audio())
                                        .cookie(claraSession)
                                        .with(
                                                org.springframework.security.test.web.servlet
                                                        .request
                                                        .SecurityMockMvcRequestPostProcessors.csrf()
                                                        .asHeader()))
                        .andReturn();
        assertThat(upload.getResponse().getStatus()).isEqualTo(404);
        assertThat(differences).isEmpty();
        assertThat(reportsDigest())
                .as("nenhum relatório criado, alterado, aprovado ou cancelado")
                .isEqualTo(reportsBefore);
        assertThat(count("lesson_report")).isEqualTo(2);
        assertThat(count("semester_report")).isEqualTo(1);

        // Telegram: o aluno some da lista e os botões dos relatórios viram os de identificador
        // inexistente
        assertThat(say(USER_CLARA, "meus alunos").plain()).doesNotContain(CanaryStudent.NAME);
        String absentName =
                say(USER_CLARA, "Zacarias Inexistente")
                        .fingerprint()
                        .replace("Zacarias Inexistente", "§");
        String typed =
                say(USER_CLARA, CanaryStudent.NAME).fingerprint().replace(CanaryStudent.NAME, "§");
        assertThat(typed).isEqualTo(absentName);
        for (String prefix : List.of("v1:focus:a", "v1:focus:s", "v1:focus:t")) {
            UUID real1 =
                    switch (prefix) {
                        case "v1:focus:a" -> reports.openLesson();
                        case "v1:focus:s" -> reports.semester();
                        default -> reports.job();
                    };
            String lost = tap(USER_CLARA, prefix + real1).fingerprint();
            String none = tap(USER_CLARA, prefix + ABSENT).fingerprint();
            assertThat(lost).as(prefix).isEqualTo(none);
        }
        for (String action :
                List.of("v1:approve:1", "v1:pdf:", "v1:cancel:", "v1:generate:2026-1")) {
            Reply reply = tap(USER_CLARA, action);
            assertThat(reply.plain()).as(action).doesNotContain(CanaryStudent.NAME);
            assertThat(reply.methods()).as(action).doesNotContain("sendDocument");
        }
        say(USER_CLARA, "histórico");
        assertThat(reportsDigest()).as("o bot também não altera nada").isEqualTo(reportsBefore);

        // reassociar devolve o aluno e o relatório como estavam
        assertThat(call(HttpMethod.PUT, assignment, null, administrator).getResponse().getStatus())
                .isIn(200, 204);
        assertThat(
                        run(new Attempt(
                                        "aluno",
                                        HttpMethod.GET,
                                        "/my-students/" + student.id(),
                                        null))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        MvcResult openAfter =
                call(
                        HttpMethod.GET,
                        API + "/lesson-reports/" + reports.openLesson(),
                        null,
                        claraSession);
        assertThat(openAfter.getResponse().getStatus()).isEqualTo(200);
        assertThat(bodyOf(openAfter)).isEqualTo(openBody);
        assertThat(reportsDigest()).isEqualTo(reportsBefore);
        assertThat(say(USER_CLARA, "meus alunos").plain()).contains(CanaryStudent.NAME);

        // a autoria registra retirada e reassociação sem dado do aluno
        List<String> operations =
                jdbc.queryForList(
                        "select operation from authorship_record where operation like 'ASSIGNMENT_%'"
                                + " order by occurred_at, operation",
                        String.class);
        assertThat(operations).contains("ASSIGNMENT_REMOVE", "ASSIGNMENT_ASSIGN");
        assertThat(
                        jdbc.queryForList(
                                "select target_type from authorship_record where operation like 'ASSIGNMENT_%'",
                                String.class))
                .containsOnly("ASSIGNMENT");
        String authorship =
                String.join(
                        "\n",
                        jdbc.query(
                                "select t::text from authorship_record t where operation like 'ASSIGNMENT_%'",
                                (rs, row) -> rs.getString(1)));
        ResponseSweeper.searching(CanaryStudent.sweepValuesWithCpf())
                .assertClean(Place.AUTHORSHIP, authorship);
    }
}
