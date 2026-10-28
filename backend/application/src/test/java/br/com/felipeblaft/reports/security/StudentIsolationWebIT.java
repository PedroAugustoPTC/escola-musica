package br.com.felipeblaft.reports.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.felipeblaft.reports.domain.registry.Student;
import br.com.felipeblaft.reports.domain.registry.Teacher;
import br.com.felipeblaft.reports.support.CanaryCpf;
import br.com.felipeblaft.reports.support.ScenarioIT;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Isolamento entre professores na Web (T136, SC-006): a professora A, com a sessão dela, tenta ler,
 * editar (inclusive o CPF), inativar, reativar, buscar e cadastrar sobre o aluno do professor B por
 * identificador manipulado, e percorre as rotas da 001 por aluno e por relatório. Tudo é negado
 * como inexistente (404 {@code RESOURCE_NOT_FOUND}, nunca 403), nada é alterado e a resposta é a
 * mesma do identificador que não existe. Nas rotas "Meus alunos" cada tentativa deixa o registro
 * {@code AUTHORIZATION_DENIED}, igual para o aluno alheio e para o inexistente (FR-040, FR-025).
 */
class StudentIsolationWebIT extends SecurityBase {

    private static final UUID ABSENT = UUID.fromString("00000000-0000-4000-8000-000000000999");
    private static final String BRUNO_DATA = "Sopro com apoio diafragmático";

    private Teacher clara;
    private Teacher bruno;
    private Student claraStudent;
    private Student brunoStudent;
    private Student brunoInactive;
    private Cookie claraSession;
    private Cookie brunoSession;
    private Reports brunoReports;

    /** Uma tentativa da professora A: o caminho usa {@code {id}} para o recurso alvo. */
    private record Attempt(
            String label,
            HttpMethod method,
            String path,
            String body,
            boolean audio,
            boolean recorded) {

        static Attempt of(String label, HttpMethod method, String path, String body) {
            return new Attempt(label, method, path, body, false, false);
        }

        /** Rota "Meus alunos" (005), que deixa o registro de autoria da negativa. */
        static Attempt registry(String label, HttpMethod method, String path, String body) {
            return new Attempt(label, method, path, body, false, true);
        }

        static Attempt upload(String label, String path) {
            return new Attempt(label, HttpMethod.POST, path, null, true, false);
        }
    }

    @BeforeEach
    void prepare() throws Exception {
        clara = scenario.clara();
        bruno = scenario.bruno();
        claraStudent = scenario.student(clara, "Matteo Mafra", "Piano");
        brunoStudent = scenario.student(bruno, "Helena Prado", "Flauta");
        brunoInactive = scenario.student(bruno, "Ivo Bastos", "Violão");
        jdbc.update("update student set active = false where id = ?", brunoInactive.id());
        claraSession = signInTeacher(ScenarioIT.CODE_CLARA);
        brunoSession = signInTeacher(ScenarioIT.CODE_BRUNO);
        brunoReports = createReports(bruno, brunoStudent, brunoSession);
    }

    private List<Attempt> studentAttempts() {
        return List.of(
                Attempt.registry("open", HttpMethod.GET, "/my-students/{id}", null),
                Attempt.registry(
                        "editar o nome",
                        HttpMethod.PATCH,
                        "/my-students/{id}",
                        "{\"name\":\"Nome Trocado\"}"),
                Attempt.registry(
                        "editar o CPF",
                        HttpMethod.PATCH,
                        "/my-students/{id}",
                        "{\"cpf\":\"" + CanaryCpf.MASKED + "\"}"),
                Attempt.registry(
                        "inactivate", HttpMethod.POST, "/my-students/{id}/inactivation", null),
                Attempt.registry(
                        "reactivate", HttpMethod.POST, "/my-students/{id}/reactivation", null),
                Attempt.of("001: aluno", HttpMethod.GET, "/students/{id}", null),
                Attempt.of(
                        "001: relatório em aberto",
                        HttpMethod.GET,
                        "/students/{id}/open-report",
                        null),
                Attempt.upload("001: registrar aula", "/students/{id}/lesson-reports"),
                Attempt.of("001: semestres", HttpMethod.GET, "/students/{id}/semesters", null),
                Attempt.of(
                        "001: prévia do semestre",
                        HttpMethod.GET,
                        "/students/{id}/semesters/2026-1/preview",
                        null),
                Attempt.of(
                        "001: gerar semestral",
                        HttpMethod.POST,
                        "/students/{id}/semester-reports",
                        "{\"periodKey\":\"2026-1\"}"),
                Attempt.of("001: histórico", HttpMethod.GET, "/students/{id}/history", null));
    }

    private List<Attempt> reportAttempts(UUID lesson, UUID semester, UUID job) {
        return List.of(
                Attempt.of("001: aula", HttpMethod.GET, "/lesson-reports/" + lesson, null),
                Attempt.of(
                        "001: responder",
                        HttpMethod.POST,
                        "/lesson-reports/" + lesson + "/answers",
                        "{\"text\":\"Sim.\"}"),
                Attempt.of(
                        "001: pedir alteração da aula",
                        HttpMethod.POST,
                        "/lesson-reports/" + lesson + "/changes",
                        "{\"text\":\"Mude.\",\"baseVersion\":1}"),
                Attempt.of(
                        "001: tentar de novo",
                        HttpMethod.POST,
                        "/lesson-reports/" + lesson + "/retry",
                        null),
                Attempt.of(
                        "001: aprovar aula",
                        HttpMethod.POST,
                        "/lesson-reports/" + lesson + "/approval",
                        "{\"confirmedVersion\":1}"),
                Attempt.of(
                        "001: cancelar aula",
                        HttpMethod.POST,
                        "/lesson-reports/" + lesson + "/cancellation",
                        null),
                Attempt.of(
                        "001: PDF de conferência da aula",
                        HttpMethod.GET,
                        "/lesson-reports/" + lesson + "/versions/1/pdf",
                        null),
                Attempt.of(
                        "001: PDF oficial da aula",
                        HttpMethod.GET,
                        "/lesson-reports/" + lesson + "/official-pdf",
                        null),
                Attempt.of("001: semestral", HttpMethod.GET, "/semester-reports/" + semester, null),
                Attempt.of(
                        "001: alterar semestral",
                        HttpMethod.POST,
                        "/semester-reports/" + semester + "/changes",
                        "{\"text\":\"Mude.\",\"baseVersion\":1}"),
                Attempt.of(
                        "001: aprovar semestral",
                        HttpMethod.POST,
                        "/semester-reports/" + semester + "/approval",
                        "{\"confirmedVersion\":1}"),
                Attempt.of(
                        "001: cancelar semestral",
                        HttpMethod.POST,
                        "/semester-reports/" + semester + "/cancellation",
                        null),
                Attempt.of(
                        "001: PDF de conferência semestral",
                        HttpMethod.GET,
                        "/semester-reports/" + semester + "/versions/1/pdf",
                        null),
                Attempt.of(
                        "001: PDF oficial semestral",
                        HttpMethod.GET,
                        "/semester-reports/" + semester + "/official-pdf",
                        null),
                Attempt.of("001: trabalho", HttpMethod.GET, "/jobs/" + job, null),
                Attempt.of(
                        "001: descartar trabalho",
                        HttpMethod.POST,
                        "/jobs/" + job + "/discard",
                        null));
    }

    private MvcResult perform(Attempt attempt, UUID id) throws Exception {
        String path = API + attempt.path().replace("{id}", id.toString());
        if (attempt.audio()) {
            return mvc.perform(
                            MockMvcRequestBuilders.multipart(path)
                                    .file(audio())
                                    .cookie(claraSession)
                                    .with(
                                            org.springframework.security.test.web.servlet.request
                                                    .SecurityMockMvcRequestPostProcessors.csrf()
                                                    .asHeader()))
                    .andReturn();
        }
        return call(attempt.method(), path, attempt.body(), claraSession);
    }

    @Test
    void everyAttemptOnAnotherTeachersStudentIsDeniedAsIfItDidNotExistAndNothingChanges()
            throws Exception {
        String before = businessDataDigest();
        List<String> failures = new ArrayList<>();

        List<Attempt> attempts = new ArrayList<>(studentAttempts());
        for (Attempt attempt : attempts) {
            for (UUID target : List.of(brunoStudent.id(), brunoInactive.id())) {
                check(attempt, target, ABSENT, failures);
            }
        }
        for (UUID lesson : List.of(brunoReports.approvedLesson(), brunoReports.openLesson())) {
            checkReports(lesson, failures);
        }

        assertThat(failures).isEmpty();
        assertThat(businessDataDigest()).as("nada de Bruno mudou").isEqualTo(before);
    }

    private void checkReports(UUID lesson, List<String> failures) throws Exception {
        for (Attempt attempt :
                reportAttempts(lesson, brunoReports.semester(), brunoReports.job())) {
            // as rotas de relatório levam o id na própria URL: aqui a alheia é a real e a ausente é
            // a mesma rota com um identificador que não existe
            MvcResult foreign = callRaw(attempt);
            MvcResult absent = callRaw(withAbsentIds(attempt, lesson));
            if (foreign.getResponse().getStatus() != 404
                    || !errorCode(foreign).equals("RESOURCE_NOT_FOUND")) {
                failures.add(
                        attempt.label()
                                + ": esperado 404 RESOURCE_NOT_FOUND e veio "
                                + foreign.getResponse().getStatus()
                                + " "
                                + errorCode(foreign));
            }
            if (!bodyOf(foreign).equals(bodyOf(absent))
                    || foreign.getResponse().getStatus() != absent.getResponse().getStatus()) {
                failures.add(
                        attempt.label()
                                + ": a resposta ao relatório alheio difere da do inexistente");
            }
            if (bodyOf(foreign).contains("Helena") || bodyOf(foreign).contains(BRUNO_DATA)) {
                failures.add(attempt.label() + ": revelou dado do aluno de outro professor");
            }
        }
    }

    private MvcResult callRaw(Attempt attempt) throws Exception {
        return call(attempt.method(), API + attempt.path(), attempt.body(), claraSession);
    }

    private Attempt withAbsentIds(Attempt attempt, UUID lesson) {
        String path =
                attempt.path()
                        .replace(lesson.toString(), ABSENT.toString())
                        .replace(brunoReports.semester().toString(), ABSENT.toString())
                        .replace(brunoReports.job().toString(), ABSENT.toString());
        return new Attempt(
                attempt.label(),
                attempt.method(),
                path,
                attempt.body(),
                attempt.audio(),
                attempt.recorded());
    }

    private void check(Attempt attempt, UUID foreignId, UUID absentId, List<String> failures)
            throws Exception {
        int deniedBefore = denials();
        MvcResult foreign = perform(attempt, foreignId);
        int foreignDenials = denials() - deniedBefore;
        deniedBefore = denials();
        MvcResult absent = perform(attempt, absentId);
        int absentDenials = denials() - deniedBefore;

        String label = attempt.label() + " (" + foreignId + ")";
        if (foreign.getResponse().getStatus() != 404
                || !errorCode(foreign).equals("RESOURCE_NOT_FOUND")) {
            failures.add(
                    label
                            + ": esperado 404 RESOURCE_NOT_FOUND e veio "
                            + foreign.getResponse().getStatus()
                            + " "
                            + errorCode(foreign));
        }
        if (foreign.getResponse().getStatus() != absent.getResponse().getStatus()
                || !bodyOf(foreign).equals(bodyOf(absent))) {
            failures.add(label + ": a resposta difere da do aluno inexistente");
        }
        if (bodyOf(foreign).contains("Helena")
                || bodyOf(foreign).contains("Bruno")
                || bodyOf(foreign).contains("Ivo")) {
            failures.add(label + ": a resposta revelou dado do aluno de outro professor");
        }
        if (foreignDenials != absentDenials) {
            failures.add(
                    label
                            + ": AUTHORIZATION_DENIED difere entre alheio ("
                            + foreignDenials
                            + ") e inexistente ("
                            + absentDenials
                            + ")");
        }
        if (attempt.recorded() && foreignDenials != 1) {
            failures.add(label + ": a tentativa deveria deixar um AUTHORIZATION_DENIED");
        }
    }

    @Test
    void searchNeverShowsAnotherTeachersStudentInAnyStatusFilter() throws Exception {
        for (String status : List.of("ACTIVE", "INACTIVE", "ALL")) {
            for (String term : List.of("Helena", "Prado", "Ivo", "Bastos", "Flauta")) {
                MvcResult mine =
                        call(
                                HttpMethod.GET,
                                API + "/my-students?search=" + term + "&status=" + status,
                                null,
                                claraSession);
                assertThat(mine.getResponse().getStatus()).isEqualTo(200);
                assertThat(jsonOf(mine).path("items")).as("%s/%s", status, term).isEmpty();
                assertThat(bodyOf(mine)).doesNotContain("Helena", "Ivo");
            }
        }
        MvcResult home = call(HttpMethod.GET, API + "/students?search=Helena", null, claraSession);
        assertThat(bodyOf(home)).doesNotContain("Helena", "Prado");
        MvcResult all = call(HttpMethod.GET, API + "/my-students?status=ALL", null, claraSession);
        assertThat(jsonOf(all).path("items")).hasSize(1);
        assertThat(bodyOf(all)).contains("Matteo").doesNotContain("Helena", "Ivo");
    }

    @Test
    void registeringWithTheCpfOfAnotherTeachersStudentIsRefusedWithoutCreatingOrSharing()
            throws Exception {
        // Bruno cadastra o aluno dele pela API, com o CPF canário
        MvcResult created =
                call(
                        HttpMethod.POST,
                        API + "/my-students",
                        "{\"name\":\"Joana Cantora\",\"birthDate\":\"1990-03-04\",\"cpf\":\""
                                + CanaryCpf.MASKED
                                + "\",\"instrument\":\"Piano\"}",
                        brunoSession);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        UUID joana = UUID.fromString(jsonOf(created).path("id").asString());
        String before = businessDataDigest();

        MvcResult attempt =
                call(
                        HttpMethod.POST,
                        API + "/my-students",
                        "{\"name\":\"Outra Pessoa\",\"birthDate\":\"1985-01-02\",\"cpf\":\""
                                + CanaryCpf.DIGITS
                                + "\",\"instrument\":\"Violino\"}",
                        claraSession);

        assertThat(errorCode(attempt)).isEqualTo("CPF_UNAVAILABLE");
        assertThat(bodyOf(attempt)).doesNotContain("Joana", "Cantora", "Bruno", CanaryCpf.DIGITS);
        assertThat(businessDataDigest()).as("nenhum aluno criado nem associado").isEqualTo(before);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from teacher_student_assignment where student_id = ?"
                                        + " and teacher_id = ?",
                                Integer.class,
                                joana,
                                clara.id()))
                .isZero();
    }
}
